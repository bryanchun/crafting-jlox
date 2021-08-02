package craftinglox.lox

import craftinglox.lox.ast.Function as AstFunction

// Function object representation
class Function(
    private val declaration: AstFunction
) : Callable {
    override fun arity(): Int = declaration.params.size

    override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
        // Core language: Each function *call* gets its own new environment
        // Parameter variables are only defined within this function scope
        val environment = Environment(interpreter.globals)

        // Parameters and arguments are checked to have an equal arity before the call gets interpreted
        for ((param, argument) in declaration.params.zip(arguments)) {
            environment.define(param.lexeme, argument)
        }

        interpreter.executeBlock(declaration.body, environment)

        return null
    }

    override fun toString(): String = "<fn ${declaration.name.lexeme}>"
}