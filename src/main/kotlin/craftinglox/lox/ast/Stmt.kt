package craftinglox.lox.ast

public abstract class Stmt {
  public abstract fun <R> accept(visitor: Visitor<R>): R

  public interface Visitor<R> {
    public fun visitExpressionStmt(stmt: Expression): R

    public fun visitPrintStmt(stmt: Print): R
  }
}

public data class Expression(
  public val expression: Expr
) : Stmt() {
  public override fun <R> accept(visitor: craftinglox.lox.ast.Stmt.Visitor<R>): R =
      visitor.visitExpressionStmt(this)
}

public data class Print(
  public val expression: Expr
) : Stmt() {
  public override fun <R> accept(visitor: craftinglox.lox.ast.Stmt.Visitor<R>): R =
      visitor.visitPrintStmt(this)
}
