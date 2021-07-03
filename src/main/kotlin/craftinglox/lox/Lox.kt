package craftinglox.lox

import kotlin.system.exitProcess

object Lox {

    const val PACKAGE_NAME = "craftinglox.lox"

    @JvmStatic
    fun main(args: Array<String>) {
        when {
            args.size > 1 -> {
                println("Usage: jlox [script]")
                exitProcess(64)         // EX_USAGE
            }
            args.size == 1 -> {
                 FileRunner(path = args[0]).run()
            }
            else -> {
                 PromptRunner().run()
            }
        }
    }
}
