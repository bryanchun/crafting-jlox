package craftinglox.lox

import craftinglox.lox.ast.Function as AstFunction
import craftinglox.lox.ast.Lambda as AstLambda

// Function object representation
class Function(
    private val declaration: AstFunction,
    private val closure: Environment,
) : Callable by Lambda(
    expr = AstLambda(declaration.params, declaration.body),
    closure = closure,
) {
    override fun toString(): String = "<fn ${declaration.name.lexeme}>"
}

// Lambda object representation
class Lambda(
    private val expr: AstLambda,
    private val closure: Environment,
) : Callable {
    override fun arity(): Int = expr.params.size

    override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
        // Core language: Each function *call* gets its own new environment
        // Parameter variables are only defined within this function scope
        val environment = Environment(closure)

        // Parameters and arguments are checked to have an equal arity before the call gets interpreted
        for ((param, argument) in expr.params.zip(arguments)) {
            environment.define(param.lexeme, argument)
        }

        try {
            interpreter.executeBlock(expr.body, environment)
        } catch (returnValue: Return) {
            // Implements return: early or last-line
            return returnValue.value
        }

        // Defaults to return nil even if function does not have explicit return statements.
        return null
    }

    override fun toString(): String = "<fn lambda>"
}