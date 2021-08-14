package craftinglox.lox

import craftinglox.lox.ast.Function as AstFunction
import craftinglox.lox.ast.Lambda as AstLambda

// Function object representation
class Function
private constructor(
    private val declaration: AstFunction,
    private val closure: Environment,
    private val isInitializer: Boolean,
    private val lambda: Lambda,
) : Callable by lambda {

    // Public constructor
    constructor(
        declaration: AstFunction,
        closure: Environment,
        isInitializer: Boolean,
    ): this(
        declaration,
        closure,
        isInitializer,
        Lambda(
            expr = AstLambda(declaration.params, declaration.body),
            closure = closure,
        )
    )


    // Bind the function with an instance through the 'this' variable and new wrapping closure
    // to return a bound method
    fun bind(instance: Instance): Function =
        Function(
            declaration = declaration,
            closure = Environment(closure).apply {
                define("this", instance)
            },
            isInitializer = isInitializer,
        )

    override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
        val result = lambda.call(interpreter, arguments)

        // Semantic choice: class init methods always returns 'this'
        return if (isInitializer) {
            closure.getAt(0, "this")
        } else {
            result
        }
    }

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