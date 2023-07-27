import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.coroutines.awaitObjectResult
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.httpDelete
import com.github.kittinunf.fuel.moshi.moshiDeserializerOf
import com.squareup.moshi.*
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import me.lazmaid.kraph.Kraph


@JsonClass(generateAdapter = true)
data class RepoResponse (
        @Json(name = "data") val data : Data)

@JsonClass(generateAdapter = true)
data class Repository(@Json(name = "id") val id: String)

@JsonClass(generateAdapter = true)
data class Data(@Json(name = "project")  val repository: Repository)

@JsonClass(generateAdapter = true)
data class MergeRequest(@Json(name = "id") val id: String,
                        @Json(name = "iid") val iid: String,
                        @Json(name = "project_id") val projectId: String,
                        @Json(name = "title") val title: String)
                        

@JsonClass(generateAdapter = true)
data class Approval(@Json(name = "id")  val id: String)

val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
val adapterMR = moshi.adapter(MergeRequest::class.java)
val adapterR = moshi.adapter(RepoResponse::class.java)
val adapterApproval = moshi.adapter(List::class.java)
var adapter: JsonAdapter<Map<String, Any>> = moshi.adapter(Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java))

val token = System.getenv("GITLAB_API_TOKEN")
val urlGraph = "https://gitlab.solocal.com/api/graphql"
val urlApi = "https://gitlab.solocal.com/api/v4/projects"
//val url = "http://localhost"

suspend fun getRepoID(path:String): List<String> {
    val reqRepo = Kraph {
        query {
            fieldObject("project", args = mapOf("fullPath" to path))
            {
                field("id")
            }
        }
    }
    println("ReqRepo ${reqRepo.toGraphQueryString()}")
    return urlGraph.httpPost()
            .header("content-type" to "application/json", "Accept" to "application/json")
            .authentication()
            .bearer(token)
            .body(reqRepo.toRequestString())

            .awaitObjectResult(moshiDeserializerOf(adapterR))
            .fold(
                    { body ->
                        val repoPattern = ":.*/(\\d+)".toRegex()
                        println(body)
                        val matches = repoPattern.find( body.data.repository.id)
                        val (repoId) = matches!!.destructured
                        listOf(repoId) },
                    { error ->
                        println("1/An error of type ${error.exception} happened: ${error.message}")
                        emptyList()
                    }
            )
}

suspend fun updateRules(repoId: String,  mrId: String):  String {

    val approvals = "$urlApi/$repoId/merge_requests/${mrId}/approval_rules"
    return approvals.httpGet()
        .header(
            "content-type" to "application/json",
            "Accept" to "application/json"
        )
        .authentication()
        .bearer(token)
        .awaitObjectResult(moshiDeserializerOf(adapterApproval))
        .fold({
            println("RULES $it.")

            val url2 = "$approvals/${it}"
            url2.httpDelete()
                .header(
                    "content-type" to "application/json",
                    "Accept" to "application/json"
                )
                .authentication()
                .bearer(token)
                .responseString().toString()
        },
            { error -> "4/An error of type ${error.exception} happened: ${error.message}" })
}

 suspend fun createPullRequest(path:String, title: String, featBranch: String) {

     getRepoID(path)
         .map { repoId ->
             if (repoId != null)
                 println(repoId)

             val prRequest = mapOf(
                 "target_branch" to "develop",
                 "source_branch" to featBranch,
                 "id" to repoId,
                 "title" to title,
                 "approvals_before_merge" to 0,
                 "remove_source_branch" to true
             )

             val url = "$urlApi/$repoId/merge_requests"
             //    println("Here ${adapter.toJson(prRequest)}")
             //    println(".")
             url.httpPost()
                 .header("content-type" to "application/json", "Accept" to "application/json")
                 .authentication()
                 .bearer(token)
                 .body(adapter.toJson(prRequest).toString())
                 .awaitObjectResult(moshiDeserializerOf(adapterMR))
                 .fold(
                     { body ->
                         val url = "$urlApi/$repoId/merge_requests/${body.iid}/notes"
                         println("body $body")
                         url.httpPost()
                             .header("content-type" to "application/json", "Accept" to "application/json")
                             .authentication()
                             .bearer(token)
                             .body("{\"body\":\"code review OK\"}")
                             .responseString { _, _, result ->
                                 result.fold(
                                     {
                                         println("Notes $it")
                                     },
                                     { error -> "3/An error of type ${error.exception} happened: ${error.message}" }
                                 ).also {

                                 }.also { println("rules") }
                             }

                         updateRules(repoId = repoId, mrId = body.iid)
                     },
                     { error -> "2/An error of type ${error.exception} happened: ${error.message}" }
                 )
         }
 }
