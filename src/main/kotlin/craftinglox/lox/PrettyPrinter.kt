package craftinglox.lox

import craftinglox.lox.ast.*

class PrettyPrinter: Expr.Visitor<String> {

    fun print(expr: Expr?) = expr?.accept(this)

    private fun parenthesize(name: String, vararg exprs: Expr): String {
        // prefix printing for now
        val results = listOf(name) + exprs.map { it.accept(this) }
        return results.joinToString(separator = " ", prefix = "(", postfix = ")")
    }

    override fun visitBinaryExpr(expr: Binary): String {
        return parenthesize(expr.operator.lexeme, expr.left, expr.right)
    }

    override fun visitGroupingExpr(expr: Grouping): String {
        return parenthesize("group", expr.expression)
    }

    override fun visitLiteralExpr(expr: Literal): String {
        return expr.value?.toString() ?: "nil"
    }

    override fun visitUnaryExpr(expr: Unary): String {
        return parenthesize(expr.operator.lexeme, expr.right)
    }

    override fun visitVariableExpr(expr: Variable): String {
        TODO("Not yet implemented")
    }

    override fun visitAssignExpr(expr: Assign): String {
        TODO("Not yet implemented")
    }

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            val testExpression =
                Binary(
                    Unary(
                        Token(TokenType.MINUS, "-", null, 1),
                        Literal(123)
                    ),
                    Token(TokenType.STAR, "*", null, 1),
                    Grouping(Literal(45.67))
                )

            val res = PrettyPrinter().print(testExpression)
            println(res)    // (* (- 123) (group 45.67))
        }
    }
}