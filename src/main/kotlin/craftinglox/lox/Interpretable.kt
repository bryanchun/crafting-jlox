package craftinglox.lox

import craftinglox.lox.ast.Expr
import craftinglox.lox.ast.Stmt

sealed class Interpretable {
    class Expression(val expr: Expr?): Interpretable()
    class Statements(val statements: List<Stmt>): Interpretable()
}
