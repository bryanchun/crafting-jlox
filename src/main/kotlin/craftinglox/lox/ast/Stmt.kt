package craftinglox.lox.ast

import craftinglox.lox.Token
import kotlin.collections.List

public abstract class Stmt {
  public abstract fun <R> accept(visitor: Visitor<R>): R

  public interface Visitor<R> {
    public fun visitBlockStmt(stmt: Block): R

    public fun visitExpressionStmt(stmt: Expression): R

    public fun visitPrintStmt(stmt: Print): R

    public fun visitVarStmt(stmt: Var): R
  }
}

public data class Block(
  public val statements: List<Stmt>
) : Stmt() {
  public override fun <R> accept(visitor: craftinglox.lox.ast.Stmt.Visitor<R>): R =
      visitor.visitBlockStmt(this)
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

public data class Var(
  public val name: Token,
  public val initializer: Expr?
) : Stmt() {
  public override fun <R> accept(visitor: craftinglox.lox.ast.Stmt.Visitor<R>): R =
      visitor.visitVarStmt(this)
}
