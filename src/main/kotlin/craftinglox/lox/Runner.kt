package craftinglox.lox

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.Charset
import kotlin.system.exitProcess

abstract class Runner {

    protected var hasErrors = false

    /**
     * Handles IO of running a program in a particular way.
     * Composes `runLox` in between.
     * Can modify error-reporting states.
     */
    abstract fun run()

    protected fun runLox(source: String) {
        val scanner = Scanner(source = source, onError = ::error)
        val tokens = scanner.scanTokens()

        // For now
        for (token in tokens) {
            println(token)
        }
    }

    private fun error(line: Int, message: String) {
        report(line = line, where = "", message = message)
    }

    private fun report(line: Int, where: String, message: String) {
        System.err.println("[line $line] Error$where: $message")
        hasErrors = true
    }
}

class FileRunner(private val path: String) : Runner() {
    override fun run() {
        val program = File(path).readText(Charset.defaultCharset())
        runLox(program)
        if (hasErrors) {
            exitProcess(65)         // EX_DATAERR
        }
    }
}

class PromptRunner : Runner() {
    override fun run() {
        val input = InputStreamReader(System.`in`)
        val reader = BufferedReader(input)

        while (true) {
            print("$PROMPT ")
            val line = reader.readLine()
            line?.let {
                runLox(it)
                hasErrors = false
            } ?: break
        }
    }

    companion object {
        const val PROMPT = ">"
    }
}
