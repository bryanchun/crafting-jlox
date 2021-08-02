package craftinglox.lox.ast

import craftinglox.lox.Token
import kotlin.collections.List

public abstract class Stmt {
  public abstract fun <R> accept(visitor: Visitor<R>): R

  public interface Visitor<R> {
    public fun visitBlockStmt(stmt: Block): R

    public fun visitExpressionStmt(stmt: Expression): R

    public fun visitFunctionStmt(stmt: Function): R

    public fun visitIfStmt(stmt: If): R

    public fun visitPrintStmt(stmt: Print): R

    public fun visitVarStmt(stmt: Var): R

    public fun visitWhileStmt(stmt: While): R
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

public data class Function(
  public val name: Token,
  public val params: List<Token>,
  public val body: List<Stmt>
) : Stmt() {
  public override fun <R> accept(visitor: craftinglox.lox.ast.Stmt.Visitor<R>): R =
      visitor.visitFunctionStmt(this)
}

public data class If(
  public val condition: Expr,
  public val thenBranch: Stmt,
  public val elseBranch: Stmt?
) : Stmt() {
  public override fun <R> accept(visitor: craftinglox.lox.ast.Stmt.Visitor<R>): R =
      visitor.visitIfStmt(this)
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

public data class While(
  public val condition: Expr,
  public val body: Stmt
) : Stmt() {
  public override fun <R> accept(visitor: craftinglox.lox.ast.Stmt.Visitor<R>): R =
      visitor.visitWhileStmt(this)
}
