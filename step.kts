#!/usr/bin/env kscript

//DEPS me.lazmaid.kraph:kraph:0.6.0 com.google.code.gson:gson:2.8.5 log4j:log4j:1.2.14 com.github.kittinunf.fuel:fuel:2.3.1 com.github.kittinunf.fuel:fuel-rxjava:2.3.1
//INCLUDE data.kt
//INCLUDE bash.kt

import org.w3c.dom.Document
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.*
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory
import kotlin.system.exitProcess


fun readXmlStat(path : String): Document {
    val xmlFile = File("$path/xiti/xiti_prod.xml")
    val dbFactory = DocumentBuilderFactory.newInstance()
    val dBuilder = dbFactory.newDocumentBuilder()
    val xmlInput = InputSource(StringReader(xmlFile.readText()))

    return dBuilder.parse(xmlInput)
}

fun getVersionPDM(doc: Document): String {
    val xpFactory = XPathFactory.newInstance()
    val xPath = xpFactory.newXPath()
    val xpath = "/resStats/at"
    val elementNodeList = xPath.evaluate(xpath, doc, XPathConstants.NODE) as Node

    return elementNodeList.attributes.getNamedItem("pdm").nodeValue
}

fun copyStatsFiles(type: String, origin: String, dest: String) {
    File("$origin/$type").walkTopDown().filter { it.extension =="xml" }
            .forEach {
                it.copyTo(File("$dest/$type/${it.name}"), true)
            }
}

val origin = System.getenv("BITRISE_SOURCE_DIR") + "/build/stats"

val version = getVersionPDM(readXmlStat(origin))
val featBranch="feat/updateStat_" + version
val title = "'feat(stat): update PDM v$version'"
ShellCmd.cd(System.getenv("BITRISE_SOURCE_DIR"))
ShellCmd.git("checkout", "-b", featBranch ).invoke()
val destStat = System.getenv("BITRISE_SOURCE_DIR") + "/app/src/main/assets/data/"
copyStatsFiles("xiti", origin, destStat)
copyStatsFiles("wsstat", origin, destStat)
copyStatsFiles("logcollector", origin, destStat)
ShellCmd.git("add", ".")
ShellCmd.git("commit", "-am", title ).invoke()
ShellCmd.git("push", "origin", featBranch ).invoke()

val repoPattern = ":(.*)\\.git".toRegex()
  val matches = repoPattern.find(System.getenv("GIT_REPOSITORY_URL"))
val (repo) = matches!!.destructured
println(repo)
createPullRequest( repo, "update PDM v$version", featBranch).doOnSuccess { r->
    print(r)
}.blockingGet()

exitProcess(0)
