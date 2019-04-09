import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName


data class RepoResponse (
        @SerializedName("data") val data : Data) {
    class Deserializer : ResponseDeserializable<RepoResponse> {

        override fun deserialize(content: String): RepoResponse? = Gson().fromJson(content, RepoResponse::class.java)

    }
}

data class Repository(@SerializedName("id") val id: String)
data class Data(@SerializedName("repository")  val repository: Repository)
