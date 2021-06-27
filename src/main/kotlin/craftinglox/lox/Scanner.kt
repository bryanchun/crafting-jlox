package craftinglox.lox

class Scanner(private val source: String, private val onError: (Int, String) -> Unit) {
    private val tokens = mutableListOf<Token>()

    // Scanning states
    private var start = 0
    private var current = 0
    private var line = 0

    fun scanTokens(): List<Token> {
        while (!isAtEnd()) {
            start = current             // `start` remembers the starting position for the current lexeme, only need to determine the ending position
            scanToken()
        }

        tokens.add(Token(TokenType.EOF, "", null, line))
        return tokens
    }

    /**
     * Single-step manipulate scanner states to advance scanning.
     */
    private fun scanToken() {
        when (val c = advance()) {
            // TODO: How to balance parentheses?
            '(' -> addToken(TokenType.LEFT_PAREN)
            ')' -> addToken(TokenType.RIGHT_PAREN)
            '{' -> addToken(TokenType.LEFT_BRACE)
            '}' -> addToken(TokenType.RIGHT_BRACE)
            ',' -> addToken(TokenType.COMMA)
            '.' -> addToken(TokenType.DOT)
            '-' -> addToken(TokenType.MINUS)
            '+' -> addToken(TokenType.PLUS)
            ';' -> addToken(TokenType.SEMICOLON)
            '*' -> addToken(TokenType.STAR)
            // 2-token operator needs 1-token lookahead, and a fallback token
            // Note: principle of maximal munch - when ambiguous, match the token with the most characters
            '!' -> addToken(if (isNext('=')) TokenType.BANG_EQUAL else TokenType.BANG)
            '=' -> addToken(if (isNext('=')) TokenType.EQUAL_EQUAL else TokenType.EQUAL)
            '<' -> addToken(if (isNext('=')) TokenType.LESS_EQUAL else TokenType.LESS)
            '>' -> addToken(if (isNext('=')) TokenType.GREATER_EQUAL else TokenType.GREATER)
            // CHALLENGE: Add support for /* multiline comment block */
            '/' -> if (isNext('/')) {
                    while (peek() != '\n' && !isAtEnd()) { advance() }      // advance to ditch commented Chars
                } else {
                    addToken(TokenType.SLASH)
                }
            ' ', '\r', '\t' -> {}                                           // ignore
            '\n' -> { line++ }                                              // new line
            '"' -> string()
            else -> scanRest(c)
        }
    }

    /**
     * Handle rest of tokens from distinct symbols
     */
    private fun scanRest(c: Char) {
        when {
            isDigit(c) -> number()
            // Begin by assuming any lexeme starting with a letter or underscore is an identifier
            isAlpha(c) -> identifier()
            else -> onError(line, "Unexpected character.")
        }
    }

    private fun isAtEnd(): Boolean = current >= source.length

    // Lookaheads
    // Note: the fewer tokens of lookahead from the grammar, the faster the scanner
    private fun advance(): Char = source[current++]
    private fun peek(): Char = if (isAtEnd()) 0.toChar() else source[current]
    private fun peekNext(): Char = if (current + 1 >= source.length) 0.toChar() else source[current + 1]
    private fun isNext(expected: Char): Boolean = when {
        isAtEnd() -> false
        else -> {
            val isExpected = source[current] == expected
            if (isExpected) { current++ }
            isExpected
        }
    }

    /**
     * Add a token.
     */
    private fun addToken(type: TokenType) {
        addToken(type = type, literal = null)
    }
    private fun addToken(type: TokenType, literal: Any?) {
        val text = source.substring(start, current)
        tokens.add(Token(type, text, literal, line))
    }

    /**
     * String builder scanner.
     */
    private fun string() {
        // Consume all of quoted value
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') { line++ }
            advance()
        }

        // One of these post-conditions is true (since they are mutually exclusive):
        // (1). peek() == '"'
        // (2). isAtEnd()
        // in which (2) implies the string is not terminated yet,
        // otherwise it would have been (1)

        if (isAtEnd()) {
            onError(line, "Unterminated string.")
            return
        }

        // Consume '"'
        advance()

        val value = source.substring(start + 1, current - 1)
        // If Lox supported escape sequences like \n, we’d unescape those here.
        addToken(TokenType.STRING, value)
    }

    private fun isDigit(c: Char): Boolean = c in '0'..'9'

    /**
     * Number builder scanner.
     */
    private fun number() {
        // Whole number part
        while (isDigit(peek())) { advance() }

        // Fractional number part
        if (peek() == '.' && isDigit(peekNext())) {
            // Consume `.`
            advance()

            // Decimal number part
            while (isDigit(peek())) { advance() }
        }

        val value = source.substring(start, current).toDouble()
        addToken(TokenType.NUMBER, value)
    }

    private fun isAlpha(c: Char): Boolean =
        c in 'a'..'z' || c in 'A'..'Z' || c == '_'

    private fun isAlphaNumeric(c: Char): Boolean = isAlpha(c) || isDigit(c)

    /**
     * Identifier builder scanner.
     */
    private fun identifier() {
        while (isAlphaNumeric(peek())) { advance() }

        val value = source.substring(start, current)
        val type = KEYWORDS[value]

        // Keyword or user-defined identifier (e.g. variables, functions)
        addToken(type = type ?: TokenType.IDENTIFIER)
    }

    companion object {
        val KEYWORDS = mapOf(
            "and" to TokenType.AND,
            "class" to TokenType.CLASS,
            "else" to TokenType.ELSE,
            "false" to TokenType.FALSE,
            "for" to TokenType.FOR,
            "fun" to TokenType.FUN,
            "if" to TokenType.IF,
            "nil" to TokenType.NIL,
            "or" to TokenType.OR,
            "print" to TokenType.PRINT,
            "return" to TokenType.RETURN,
            "super" to TokenType.SUPER,
            "this" to TokenType.THIS,
            "true" to TokenType.TRUE,
            "var" to TokenType.VAR,
            "while" to TokenType.WHILE,
        )
    }
}
