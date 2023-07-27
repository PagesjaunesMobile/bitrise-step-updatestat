#!/usr/bin/env kscript

//DEPS me.lazmaid.kraph:kraph:0.7.0,com.squareup.moshi:moshi:1.8.0,com.squareup.moshi:moshi-adapters:1.8.0,com.squareup.moshi:moshi-kotlin:1.8.0,com.github.kittinunf.fuel:fuel:2.3.1,com.github.kittinunf.fuel:fuel-coroutines:2.3.1,com.github.kittinunf.fuel:fuel-moshi:2.3.1
//INCLUDE data.kt
//INCLUDE bash.kt


import com.github.kittinunf.fuel.Fuel
import kotlinx.coroutines.runBlocking
import java.io.*
import kotlin.system.exitProcess


  fun readStat(path : String) = File(path).readText()

fun getVersionPDM(doc: String): String {

  val regex = """"version":.?"([^"]+)",?""".toRegex()

  var found = regex.find(doc)

    val (match) = found!!.destructured
    println("Version $match")
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
 // println(File(destStat).forEachLine { println(it) }) //getVersionPDM(it)}.firstNotNull())
 //   exitProcess(0)
val version = getVersionPDM(readStat(destStat))

val featBranch="feat/updateStat_" + version
val title = "'feat(stat): update PDM $version'"
  println(title)

ShellCmd.git("checkout", "-b", featBranch ).invoke()
ShellCmd.git("add", ".")
ShellCmd.git("commit", "-am", title).invoke()
ShellCmd.git("push", "origin", featBranch ).invoke()
  
val repoPattern = ":(.*)\\.git".toRegex()
  val matches = repoPattern.find(System.getenv("GIT_REPOSITORY_URL"))
val (repo) = matches!!.destructured
println("Repo $repo")
  runBlocking {
      createPullRequest(repo, "update PDM $version", featBranch)
  }

exitProcess(0)
