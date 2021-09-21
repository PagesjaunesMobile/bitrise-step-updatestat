import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.coroutines.awaitObjectResult
import com.github.kittinunf.fuel.httpPost
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

val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
val adapterMR = moshi.adapter(MergeRequest::class.java)
val adapterR = moshi.adapter(RepoResponse::class.java)

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

 suspend fun createPullRequest(path:String, title: String, featBranch: String): List<String> {

    return getRepoID(path)
            .map{ repoId ->
                if (repoId != null)
                    println(repoId)

                val prRequest = mapOf("target_branch" to "develop",
                        "source_branch" to featBranch,
                        "id" to repoId,
                        "title" to title,
                        "approvals_before_merge" to 0,
                        "remove_source_branch" to true)

                val url = "$urlApi/$repoId/merge_requests"
                println("Here ${adapter.toJson(prRequest)}")
                println(".")
                url.httpPost()
                        .header("content-type" to "application/json", "Accept" to "application/json")
                        .authentication()
                        .bearer(token)
                        .body(adapter.toJson(prRequest).toString())
                        .awaitObjectResult(moshiDeserializerOf(adapterMR))
                        .fold(
                                { body ->
                            val url = "$urlApi/$repoId/merge_requests/${body.iid}/notes"
                            println("$url")
                            url.httpPost()
                                    .header("content-type" to "application/json", "Accept" to "application/json")
                                    .authentication()
                                    .bearer(token)
                                    .body("{\"body\":\"code review OK\"}")
                                    .responseString().toString()
                        },
                                { error -> "2/An error of type ${error.exception} happened: ${error.message}" }
                        )
            }
}
