package craftinglox.lox

data class Token(
    val type: TokenType,
    val lexeme: String,
    val literal: Any? = null,
    val line: Int,
)
