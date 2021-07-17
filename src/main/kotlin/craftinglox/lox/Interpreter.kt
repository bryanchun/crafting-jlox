package craftinglox.lox

import craftinglox.lox.expr.*
import java.lang.RuntimeException

// We need a superclass of any class to represent what could be the result
// of any interpretation, hence Interpreter is a visitor that returns Any?
class Interpreter(private val onRuntimeError: (RuntimeError) -> Unit): Expr.Visitor<Any?> {

    // Public API
    fun interpret(expr: Expr) {
        try {
            // For now, interpreter == an expression evaluator
            val value = evaluate(expr)
            println(stringify(value))
        } catch (error: RuntimeError) {
            onRuntimeError(error)
        }
    }

    // Evaluate a sub-expression
    private fun evaluate(expr: Expr): Any? = expr.accept(this)

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

    /**
     * Error
     */
    class RuntimeError(val token: Token, override val message: String?): RuntimeException()

    private fun throwDoubleOperandError(operator: Token): Nothing =
        throw RuntimeError(operator, "Operand must be a number.")
    private fun throwDoubleOperandsError(operator: Token): Nothing =
        throw RuntimeError(operator, "Operands must be numbers.")
}