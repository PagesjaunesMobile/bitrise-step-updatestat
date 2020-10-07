#!/usr/bin/env kscript

//DEPS me.lazmaid.kraph:kraph:0.6.0 com.google.code.gson:gson:2.8.5 log4j:log4j:1.2.14 com.github.kittinunf.fuel:fuel:2.1.0 com.github.kittinunf.fuel:fuel-rxjava:2.1.0
//INCLUDE data.kt
//INCLUDE bash.kt


import java.io.*
import kotlin.system.exitProcess


  fun readStat(path : String) = File(path).readText()

fun getVersionPDM(doc: String): String {

  val regex = """"version":.?"([^"]+)",?""".toRegex()
  var found = regex.find(doc)
    val (match) = found!!.destructured
    return match
}

fun copyStatsFiles(origin: String, dest: String) {
    File("$origin").walkTopDown().filter { it.extension =="json" }
            .forEach {
                it.copyTo(File("$dest"), true)
            }
}

ShellCmd.cd(System.getenv("BITRISE_SOURCE_DIR"))

val origin = System.getenv("BITRISE_SOURCE_DIR") + "/build/stats"
val destStat = System.getenv("BITRISE_SOURCE_DIR") + "/stat/src/main/res/raw/at.json"
  
copyStatsFiles(origin, destStat)
    
val message = System.getenv("BITRISE_GIT_MESSAGE")
val version = getVersionPDM(readStat(destStat))
val featBranch="feat/updateStat_" + version
val title = "'feat(stat): update PDM $version \n\n$message'"
  println(title)

ShellCmd.git("checkout", "-b", featBranch ).invoke()



ShellCmd.git("add", ".")
ShellCmd.git("commit", "-am", """$title""" ).invoke()
ShellCmd.git("push", "origin", featBranch ).invoke()

val repoPattern = ":(.*)\\.git".toRegex()
  val matches = repoPattern.find(System.getenv("GIT_REPOSITORY_URL"))
val (repo) = matches!!.destructured
println(repo)
createPullRequest( repo, "update PDM $version", featBranch).doOnSuccess { r->
    print(r)
}.blockingGet()

exitProcess(0)
