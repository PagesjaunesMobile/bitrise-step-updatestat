import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.coroutines.awaitObjectResult
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.result.map
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import me.lazmaid.kraph.Kraph


data class RepoResponse (
        @SerializedName("data") val data : Data) {
    class Deserializer : ResponseDeserializable<RepoResponse> {

        override fun deserialize(content: String): RepoResponse? = Gson().fromJson(content, RepoResponse::class.java)

    }
}

data class Repository(@SerializedName("id") val id: String)
data class Data(@SerializedName("project")  val repository: Repository)
data class MergeRequest(@SerializedName("id") val id: String,
                        @SerializedName("iid") val iid: String,
                        @SerializedName("project_id") val projectId: String,
                        @SerializedName("title") val title: String) {
    class Deserializer : ResponseDeserializable<MergeRequest> {

        override fun deserialize(content: String): MergeRequest? = Gson().fromJson(content, MergeRequest::class.java)

    }
}

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
            .awaitObjectResult(RepoResponse.Deserializer())
            .fold(
                    { body ->
                        val repoPattern = ":.*/(\\d+)".toRegex()
                        println(body.data)
                        val matches = repoPattern.find( body.data.repository.id)
                        val (repoId) = matches!!.destructured
                        listOf(repoId) },
                    { error ->
                        println("An error of type ${error.exception} happened: ${error.message}")
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
                println("Here $url")
                println(Gson().toJson(prRequest))
                println(".")
                url.httpPost()
                        .header("content-type" to "application/json", "Accept" to "application/json")
                        .authentication()
                        .bearer(token)
                        .body(Gson().toJson(prRequest))
                        .awaitObjectResult(MergeRequest.Deserializer())
                        .fold(
                                { body ->
                            val url = "$urlApi/$repoId/merge_requests/${body.iid}/notes"
                            println(".")
                            url.httpPost()
                                    .header("content-type" to "application/json", "Accept" to "application/json")
                                    .authentication()
                                    .bearer(token)
                                    .body("{\"body\":\"code review OK\"}")
                                    .responseString().toString()
                        },
                                { error -> "An error of type ${error.exception} happened: ${error.message}" }
                        )
            }
}
