package craftinglox.lox.expr

import craftinglox.lox.Token
import kotlin.Any

public abstract class Expr {
  public abstract fun <R> accept(visitor: Visitor<R>): R

  public interface Visitor<R> {
    public fun visitBinaryExpr(expr: Binary): R

    public fun visitGroupingExpr(expr: Grouping): R

    public fun visitLiteralExpr(expr: Literal): R

    public fun visitUnaryExpr(expr: Unary): R
  }
}

public data class Binary(
  public val left: Expr,
  public val `operator`: Token,
  public val right: Expr
) : Expr() {
  public override fun <R> accept(visitor: craftinglox.lox.expr.Expr.Visitor<R>): R =
      visitor.visitBinaryExpr(this)
}

public data class Grouping(
  public val expression: Expr
) : Expr() {
  public override fun <R> accept(visitor: craftinglox.lox.expr.Expr.Visitor<R>): R =
      visitor.visitGroupingExpr(this)
}

public data class Literal(
  public val `value`: Any
) : Expr() {
  public override fun <R> accept(visitor: craftinglox.lox.expr.Expr.Visitor<R>): R =
      visitor.visitLiteralExpr(this)
}

public data class Unary(
  public val `operator`: Token,
  public val right: Expr
) : Expr() {
  public override fun <R> accept(visitor: craftinglox.lox.expr.Expr.Visitor<R>): R =
      visitor.visitUnaryExpr(this)
}
