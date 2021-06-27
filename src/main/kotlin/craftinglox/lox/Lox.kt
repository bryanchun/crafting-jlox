package craftinglox.lox

//import java.io.BufferedReader
//import java.io.IOException
//import java.io.InputStreamReader
//import java.nio.charset.Charset
//import java.nio.file.Files
//import java.nio.file.Paths
import kotlin.system.exitProcess

object Lox {

    @JvmStatic
    fun main(args: Array<String>) {
        when {
            args.size > 1 -> {
                println("Usage: jlox [script]")
                exitProcess(64)         // EX_USAGE
            }
            args.size == 1 -> {
                // runSource(args[0])
            }
            else -> {
                // runPrompt()
            }
        }
    }
}