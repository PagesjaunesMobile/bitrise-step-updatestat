#!/usr/bin/env kscript
import bash.ShellUtils
import bash.evalBash
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.core.representationOfBytes
import com.github.kittinunf.fuel.httpPost
import com.taskworld.kraph.Kraph
import org.w3c.dom.Document
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.File
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory
import kotlin.system.exitProcess

//DEPS com.taskworld.kraph:kraph:0.4.1 com.github.kittinunf.fuel:fuel:2.0.1 com.google.code.gson:gson:2.8.5 log4j:log4j:1.2.14
//fuel-rxjava
//INCLUDE data.kt
//INCLUDE bash.kt
/*
for (arg in args) {
    println("arg: $arg")
}
*/

fun readXmlStat(path : String): Document {
    val xmlFile = File("$path/xiti/xiti_prod.xml")
    val dbFactory = DocumentBuilderFactory.newInstance()
    val dBuilder = dbFactory.newDocumentBuilder()
    val xmlInput = InputSource(StringReader(xmlFile.readText()))
    val doc = dBuilder.parse(xmlInput)

    return doc
}
fun getVersionPDM(doc: Document): String {

    val xpFactory = XPathFactory.newInstance()
    val xPath = xpFactory.newXPath()

    val xpath = "/resStats/at"

    val elementNodeList = xPath.evaluate(xpath, doc, XPathConstants.NODE) as Node

    return elementNodeList.attributes.getNamedItem("pdm").nodeValue
}

fun copyStatsFiles(type: String, origin: String, dest: String)
{
    File("$origin/$type").walkTopDown().filter { it.extension =="xml" }
            .forEach {
                println(it)
                it.copyTo(File("$dest/$type/${it.name}"), true)
            }
}


val token = System.getenv("GITHUB_AUTH_TOKEN")
val url = "https://api.github.com/graphql"
val origin = System.getenv("BITRISE_SOURCE_DIR") + "build/stats"

val version = getVersionPDM(readXmlStat(origin))
val featBranch="feat/updateStat_" + version
val title = "feat(stat): update PDM v" + version

ShellUtils.git("checkout", "-b", featBranch )
copyStatsFiles("xiti", origin,"app/src/main/assets/data/")
copyStatsFiles("wsstat", origin,"app/src/main/assets/data/")
copyStatsFiles("logcollector", origin,"app/src/main/assets/data/")
ShellUtils.git("commit", "-am", title )

val query = Kraph {
    query {
        fieldObject("viewer") {
            field("login")
        }
    }
}

val reqRepo = Kraph {
    query {
        fieldObject("repository", args = mapOf("name" to System.getenv("BITRISE_APP_TITLE"),
                "owner" to System.getenv("BITRISEIO_GIT_REPOSITORY_OWNER")))
        {
            field("id")
        }
    }
}

url.httpPost()
        .header("content-type" to "application/json", "Accept" to "application/json")
        .authentication()
        .bearer(token)
        .body(reqRepo.toRequestString())
        //  .also { println(it) }
        .responseObject(RepoResponse.Deserializer()) { request, response, result ->
            val dataRepo = result.get().data.repository
            //     println("data " + response.body().representationOfBytes("application/json"))

            val pr =  Kraph {
                mutation {
                    func("createPullRequest", args = mapOf("baseRefName" to "master",
                            "headRefName" to featBranch,
                            "repositoryId" to dataRepo.id,
                            "title" to title))
                    {
                        fieldObject("pullRequest") {
                            field("id")
                        }
                    }
                }
            }

            //println(pr.toRequestString())
            url.httpPost()
                    .authentication()
                    .bearer(token)
                    .body(pr.toRequestString())
                    .also { println(it) }
                    .response { result -> }

                    .join()
        }.join()

exitProcess(0)

