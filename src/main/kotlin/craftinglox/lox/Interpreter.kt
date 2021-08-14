package craftinglox.lox

import craftinglox.lox.ast.*
import craftinglox.lox.ast.Function
import craftinglox.lox.ast.Set
import craftinglox.lox.Class as LoxClass
import craftinglox.lox.Function as LoxFunction
import craftinglox.lox.Lambda as LoxLambda
import craftinglox.lox.Return as LoxReturn

// We need a superclass of any class to represent what could be the result
// of any interpretation, hence Interpreter is a visitor that returns Any?
class Interpreter(
    private val onRuntimeError: (RuntimeError) -> Unit
): Expr.Visitor<Any?>, Stmt.Visitor<Unit?> {

    // Public API
    fun interpret(interpretable: Interpretable) {
        try {
            when (interpretable) {
                is Interpretable.Statements -> {
                    for (statement in interpretable.statements) {
                        execute(statement)
                    }
                }
                is Interpretable.Expression -> {
                    val value = interpretable.expr?.let { evaluate(it) }
                    println(stringify(value))
                }
            }
        } catch (error: RuntimeError) {
            onRuntimeError(error)
        }
    }

    fun resolve(expr: Expr, depth: Int) {
        locals[expr] = depth
    }

    /**
     * Error
     */
    private fun throwDoubleOperandError(operator: Token): Nothing =
        throw RuntimeError(operator, "Operand must be a number.")
    private fun throwDoubleOperandsError(operator: Token): Nothing =
        throw RuntimeError(operator, "Operands must be numbers.")

    // Global environment
    val globals = Environment()

    // An interpreter has an in-memory environment for its lifetime
    private var environment = globals

    // Resolution information for variables in a side table
    // Know exactly which parent scope was the variable defined/captured, so that we can statically look them up again
    // when using them, instead of dynamically resolving to the latest variable value.
    private val locals = mutableMapOf<Expr, Int>()

    init {
        // Native functions at global environment as runtime warms up
        globals.define("clock", object : Callable {
            override fun arity(): Int = 0

            override fun call(interpreter: Interpreter, arguments: List<Any?>): Any =
                System.currentTimeMillis().toDouble() / 1000.0

            override fun toString(): String = "<native fn>"
        })
    }

    // Evaluate a sub-expression
    private fun evaluate(expr: Expr): Any? = expr.accept(this)

    // Run a statement
    private fun execute(stmt: Stmt) = stmt.accept(this)

    // Run a block (nesting, shadowing, lexical scoping)
    fun executeBlock(stmts: List<Stmt>, environment: Environment) {
        // The interpreter is using a different passed-in environment each time it executes a block
        // Remember to clean up and restore the environment before the passed-in
        val before = this.environment

        try {
            this.environment = environment
            stmts.forEach { execute(it) }
        } finally {
            this.environment = before
        }
    }

    // Partition the universe of values into either truthy and falsey
    // Lox follows Ruby's truthiness rule
    private fun isTruthy(x: Any?): Boolean =
        when (x) {
            null -> false
            is Boolean -> x
            // everything else is truthy
            else -> true
        }

    // Account for any differences between Lox's equality and the implementation language's
    // In this case, we can reuse Kotlin's `==` since both languages do not do implicit conversions
    private fun isEqual(x: Any?, y: Any?): Boolean = x == y

    private fun stringify(x: Any?): String =
        when (x) {
            // Edge case where Lox and the implementation language have different names/syntax
            null -> "nil"
            is Double -> {
                val number = x.toString()
                // Stringify integers represented as double by truncating the decimals
                if (number.endsWith(".0")) {
                    // integer
                    number.substring(0, number.length - 2)
                } else {
                    // double
                    number
                }
            }
            // For most values, we can use the implementation language's toString implementation
            else -> x.toString()
        }

    private fun lookupVariable(name: Token, expr: Expr): Any? {
        val distance = locals[expr]
        // Look up local variable, or fall back to global variable
        return distance?.let {
            environment.getAt(it, name.lexeme)
        } ?: globals.get(name)
    }

    private fun <R> Double?.unaryOp(op: Double.() -> R): R? {
        return when (this) {
            null -> null
            else -> this.op()
        }
    }

    private fun <R> Pair<Double?, Double?>.binaryOp(op: Double.(Double) -> R): R? {
        val (x, y) = this
        return when {
            x == null || y == null -> null
            else -> x.op(y)
        }
    }

    /**
     * Expr
     */

    // Semantics choice: Left-to-right evaluation order
    // Semantics choice: We evaluate both operands before checking the type of either
    // Semantics choice: If any operand is a string, implicitly convert the other operand to be a string to concat
    override fun visitBinaryExpr(expr: Binary): Any? {
        val left = evaluate(expr.left)
        val right = evaluate(expr.right)

        return when (expr.operator.type) {
            // Operator overloading!
            // - Dynamically check the type and dispatch to appropriate operation
            // - Do not assume an input to be a certain type and try to cast
            TokenType.PLUS -> when {
                left is Double && right is Double -> left + right
                left is String || right is String -> stringify(left) + stringify(right)
                else -> throw RuntimeError(expr.operator, "Operands must be two numbers or either operands must be a string.")
            }
            TokenType.MINUS -> (left as? Double to right as? Double).binaryOp { this - it }
                ?: throwDoubleOperandsError(expr.operator)
            TokenType.STAR -> (left as? Double to right as? Double).binaryOp { this * it }
                ?: throwDoubleOperandsError(expr.operator)
            // We are not following IEEE's "doubles divide-by-zero returns NaN" spec here
            TokenType.SLASH -> (left as? Double to right as? Double).binaryOp {
                    // Check and throw on divide by zero
                    if (it == 0.0) {
                        throw RuntimeError(expr.operator, "Cannot divide by zero")
                    }
                    this / it
                }
                ?: throwDoubleOperandsError(expr.operator)
            TokenType.GREATER -> (left as? Double to right as? Double).binaryOp { this > it }
                ?: throwDoubleOperandsError(expr.operator)
            TokenType.GREATER_EQUAL -> (left as? Double to right as? Double).binaryOp { this >= it }
                ?: throwDoubleOperandsError(expr.operator)
            TokenType.LESS -> (left as? Double to right as? Double).binaryOp { this < it }
                ?: throwDoubleOperandsError(expr.operator)
            TokenType.LESS_EQUAL -> (left as? Double to right as? Double).binaryOp { this <= it }
                ?: throwDoubleOperandsError(expr.operator)
            TokenType.EQUAL_EQUAL -> isEqual(left, right)
            TokenType.BANG_EQUAL -> !isEqual(left, right)
            else -> null
        }
    }

    override fun visitGroupingExpr(expr: Grouping): Any? = evaluate(expr.expression)

    // Scanner does the conversion already, so just trivially return
    override fun visitLiteralExpr(expr: Literal): Any? = expr.value

    override fun visitUnaryExpr(expr: Unary): Any? {
        val right = evaluate(expr.right)
        return when (expr.operator.type) {
            TokenType.BANG -> !isTruthy(right)
            TokenType.MINUS -> (right as? Double).unaryOp { -this }
                ?: throwDoubleOperandError(expr.operator)
            else -> null
        }
    }

    override fun visitVariableExpr(expr: Variable): Any? {
        return lookupVariable(expr.name, expr)
    }

    override fun visitAssignExpr(expr: Assign): Any? {
        val value = evaluate(expr.value)

        val distance = locals[expr]
        distance?.also {
            environment.assignAt(it, expr.name, value)
        } ?: also {
            globals.assign(expr.name, value)
        }

        return value
    }

    override fun visitLambdaExpr(expr: Lambda): Any {
        return LoxLambda(expr = expr, closure = environment)
    }

    /**
     * Stmt
     */

    override fun visitExpressionStmt(stmt: Expression): Unit? {
        evaluate(stmt.expression)
        return null
    }

    override fun visitPrintStmt(stmt: Print): Unit? {
        val value = evaluate(stmt.expression)
        println(stringify(value))
        return null
    }

    // Semantics choice: We do not hard-require an initializer explicitly in a declaration, defaults to nil
    override fun visitVarStmt(stmt: Var): Unit? {
        stmt.initializer?.let {
           val value = evaluate(it)
            environment.define(stmt.name.lexeme, value)
        } ?: let {
            environment.define(stmt.name.lexeme, null, initialized = false)
        }
        return null
    }

    override fun visitBlockStmt(stmt: Block): Unit? {
        // Execute a block using a new environment instance, with the current one as the enclosing parent environment
        executeBlock(stmt.statements, Environment(enclosing = environment))
        return null
    }

    override fun visitClassStmt(stmt: Class): Unit? {
        // Making this variable binding process 2-stage allows references to the class inside its own methods
        environment.define(stmt.name.lexeme, null)

        val methods = mutableMapOf<String, LoxFunction>()
        for (method in stmt.methods) {
            methods[method.name.lexeme] = LoxFunction(method, environment)
        }

        val clazz = LoxClass(name = stmt.name.lexeme, methods = methods)
        environment.assign(stmt.name, clazz)

        return null
    }

    override fun visitIfStmt(stmt: If): Unit? {
        if (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.thenBranch)
        // Semantic choice: Short-circuiting conditional evaluation ('lazy')
        } else {
            stmt.elseBranch?.also { execute(it) }
        }
        return null
    }

    override fun visitLogicalExpr(expr: Logical): Any? {
        val left = evaluate(expr.left)

        return when {
            expr.operator.type == TokenType.OR && isTruthy(left) -> left
            expr.operator.type == TokenType.AND && !isTruthy(left) -> left
            else -> evaluate(expr.right)
        }
    }

    override fun visitSetExpr(expr: Set): Any? {
        when (val obj = evaluate(expr.`object`)) {
            is Instance -> {
                val value = evaluate(expr.value)
                obj.set(expr.name, value)
                return value
            }
            else -> throw RuntimeError(expr.name, "Only instances have fields.")
        }
    }

    override fun visitThisExpr(expr: This): Any? {
        return lookupVariable(expr.keyword, expr)
    }

    override fun visitWhileStmt(stmt: While): Unit? {
        // re-evaluate the condition for its truthiness after each iteration of the side-effectful body
        while (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.body)
        }
        return null
    }

    override fun visitCallExpr(expr: Call): Any? {
        val callee = evaluate(expr.callee)

        // Semantic choice: order of evaluation is, evaluate arguments first, then the call
        val arguments = mutableListOf<Any?>()
        expr.arguments.forEach {
            arguments.add(evaluate(it))
        }

        // Cast callee to be a Callable that has a call() method in it
        val function = callee as? Callable
            ?: throw RuntimeError(expr.paren, "Can only call functions and classes.")

        // Semantic choice: Function arity checking
        // (some languages are lenient on this)
        if (arguments.size != function.arity()) {
            throw RuntimeError(expr.paren, "Expected ${function.arity()} arguments but got ${arguments.size}.")
        }

        return function.call(this, arguments)
    }

    override fun visitGetExpr(expr: Get): Any? =
        when (val obj = evaluate(expr.`object`)) {
            // Optimization note: using a hash table for field access is fast enough, but there are other ways too.
            is Instance -> obj.get(expr.name)
            // Semantic choice: We could silently fail to return 'nil', but we don't do this here.
            else -> throw RuntimeError(expr.name, "Only instances have properties.")
        }

    override fun visitFunctionStmt(stmt: Function): Unit? {
        // Wraps the AST function into a runtime representation, ready to be invoked later on.
        // Captures the current parent environment when declaring* the function, so that the closure environment
        // is preserved with the function object for later invocations. i.e. lexical scope not dynamic scope.
        val function = LoxFunction(declaration = stmt, closure = environment)

        // Bind/define the function object in the current environment
        environment.define(stmt.name.lexeme, function)

        return null
    }

    override fun visitReturnStmt(stmt: Return): Unit? {
        val value = stmt.value?.let { evaluate(it) }

        throw LoxReturn(value)
    }
}