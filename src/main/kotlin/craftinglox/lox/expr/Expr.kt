package craftinglox.lox.expr

import craftinglox.lox.Token
import kotlin.Any

public interface Expr

public data class Binary(
  public val left: Expr,
  public val `operator`: Token,
  public val right: Expr
) : Expr

public data class Grouping(
  public val expression: Expr
) : Expr

public data class Literal(
  public val `value`: Any
) : Expr

public data class Unary(
  public val `operator`: Token,
  public val right: Expr
) : Expr
