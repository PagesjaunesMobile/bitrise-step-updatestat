package bash

import java.io.*
import kotlin.system.exitProcess


data class BashResult(val exitCode: Int, val stdout: Iterable<String>, val stderr: Iterable<String>) {
    fun sout() = stdout.joinToString("\n").trim()

    fun serr() = stderr.joinToString("\n").trim()
}


fun evalBash(cmd: String, showOutput: Boolean = false,
             redirectStdout: File? = null, redirectStderr: File? = null, wd: File? = null): BashResult {

    try {

        // optionally prefix script with working directory change
        val cmd = (if (wd != null) "cd '${wd.absolutePath}'\n" else "") + cmd


        var pb = ProcessBuilder("/bin/bash", "-c", cmd) //.inheritIO();
        pb.directory(File("."));
        var p = pb.start();

        val outputGobbler = StreamGobbler(p.getInputStream(), if (showOutput) System.out else null)
        val errorGobbler = StreamGobbler(p.getErrorStream(), if (showOutput) System.err else null)

        // kick them off
        errorGobbler.start()
        outputGobbler.start()

        // any error???
        val exitVal = p.waitFor()
        return BashResult(exitVal, outputGobbler.sb.lines(), errorGobbler.sb.lines())
    } catch (t: Throwable) {
        throw RuntimeException(t)
    }
}


internal class StreamGobbler(var inStream: InputStream, val printStream: PrintStream?) : Thread() {
    var sb = StringBuilder()

    override fun run() {
        try {
            val isr = InputStreamReader(inStream)
            val br = BufferedReader(isr)
            for (line in br.lines()) {
                sb.append(line!! + "\n")
                printStream?.println(line)
            }
        } catch (ioe: IOException) {
            ioe.printStackTrace()
        }
    }


    val output: String get() = sb.toString()
}


object ShellUtils {

    fun isInPath(tool: String) = evalBash("which $tool").sout().trim().isNotBlank()


    fun requireInPath(tool: String) = require(isInPath(tool)) { "$tool is not in PATH" }

    fun envman(env: kotlin.Pair<kotlin.String, kotlin.Any>) = {

        val defCmd = "envman add -key ${env.first} --value \"${env.second}\""
        evalBash(defCmd, true)
    }

    fun git(command: String, vararg args: String) = {

        val defCmd = "echo $command ${args.joinToString(separator = " ")}"
        evalBash(defCmd, true)
    }
}


public inline fun stopIfNot(value: Boolean, lazyMessage: () -> Any) {
    if (!value) {
        System.err.println("[ERROR] " + lazyMessage().toString())
        exitProcess(1)
    }
}
