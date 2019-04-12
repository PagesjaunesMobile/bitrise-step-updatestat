import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.rx.rxObject
import com.github.kittinunf.fuel.rx.rxResponseString
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.taskworld.kraph.Kraph
import io.reactivex.Single


data class RepoResponse (
        @SerializedName("data") val data : Data) {
    class Deserializer : ResponseDeserializable<RepoResponse> {

        override fun deserialize(content: String): RepoResponse? = Gson().fromJson(content, RepoResponse::class.java)

    }
}

data class Repository(@SerializedName("id") val id: String)
data class Data(@SerializedName("repository")  val repository: Repository)

val token = System.getenv("GITHUB_AUTH_TOKEN")
val url = "https://api.github.com/graphql"
//val url = "http://localhost"

fun getRepoID(name:String, owner: String): Single<String> {
    val reqRepo = Kraph {
        query {
            fieldObject("repository", args = mapOf("name" to name, "owner" to owner))
            {
                field("id")
            }
        }
    }

    return url.httpPost()
            .header("content-type" to "application/json", "Accept" to "application/json")
            .authentication()
            .bearer(token)
            .body(reqRepo.toRequestString())
            .rxObject(RepoResponse.Deserializer())
            .map { it.get().data.repository.id }
}


fun createPullRequest(name:String, owner: String, title: String, featBranch: String, comment: String): Single<String> {
    return getRepoID(name, owner)
            .flatMap { repoId ->
                if (repoId != null)
                    println(repoId)

                val prRequest = Kraph {
                    mutation {
                        func("createPullRequest", args = mapOf("baseRefName" to "develop",
                                "headRefName" to featBranch,
                                "repositoryId" to repoId,
                                "title" to title,
                                "body" to comment))
                        {
                            fieldObject("pullRequest") {
                                field("id")
                            }
                        }
                    }
                }

                url.httpPost()
                        .authentication()
                        .bearer(token)
                        .body(prRequest.toRequestString())
                        .rxResponseString()
            }

}