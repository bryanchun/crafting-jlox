package craftinglox.lox.ast

import craftinglox.lox.Token
import kotlin.Any
import kotlin.collections.List

public abstract class Expr {
  public abstract fun <R> accept(visitor: Visitor<R>): R

  public interface Visitor<R> {
    public fun visitAssignExpr(expr: Assign): R

    public fun visitBinaryExpr(expr: Binary): R

    public fun visitCallExpr(expr: Call): R

    public fun visitGroupingExpr(expr: Grouping): R

    public fun visitLiteralExpr(expr: Literal): R

    public fun visitLogicalExpr(expr: Logical): R

    public fun visitUnaryExpr(expr: Unary): R

    public fun visitVariableExpr(expr: Variable): R
  }
}

public data class Assign(
  public val name: Token,
  public val `value`: Expr
) : Expr() {
  public override fun <R> accept(visitor: craftinglox.lox.ast.Expr.Visitor<R>): R =
      visitor.visitAssignExpr(this)
}

public data class Binary(
  public val left: Expr,
  public val `operator`: Token,
  public val right: Expr
) : Expr() {
  public override fun <R> accept(visitor: craftinglox.lox.ast.Expr.Visitor<R>): R =
      visitor.visitBinaryExpr(this)
}

public data class Call(
  public val callee: Expr,
  public val paren: Token,
  public val arguments: List<Expr>
) : Expr() {
  public override fun <R> accept(visitor: craftinglox.lox.ast.Expr.Visitor<R>): R =
      visitor.visitCallExpr(this)
}

public data class Grouping(
  public val expression: Expr
) : Expr() {
  public override fun <R> accept(visitor: craftinglox.lox.ast.Expr.Visitor<R>): R =
      visitor.visitGroupingExpr(this)
}

public data class Literal(
  public val `value`: Any?
) : Expr() {
  public override fun <R> accept(visitor: craftinglox.lox.ast.Expr.Visitor<R>): R =
      visitor.visitLiteralExpr(this)
}

public data class Logical(
  public val left: Expr,
  public val `operator`: Token,
  public val right: Expr
) : Expr() {
  public override fun <R> accept(visitor: craftinglox.lox.ast.Expr.Visitor<R>): R =
      visitor.visitLogicalExpr(this)
}

public data class Unary(
  public val `operator`: Token,
  public val right: Expr
) : Expr() {
  public override fun <R> accept(visitor: craftinglox.lox.ast.Expr.Visitor<R>): R =
      visitor.visitUnaryExpr(this)
}

public data class Variable(
  public val name: Token
) : Expr() {
  public override fun <R> accept(visitor: craftinglox.lox.ast.Expr.Visitor<R>): R =
      visitor.visitVariableExpr(this)
}
