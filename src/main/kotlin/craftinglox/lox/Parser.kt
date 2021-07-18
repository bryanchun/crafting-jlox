package craftinglox.lox

import craftinglox.lox.ast.*
import java.lang.Exception
import java.lang.RuntimeException

class Parser(private val tokens: List<Token>, private val onError: (Token, String) -> Unit) {

    // Public API
    fun parse(): List<Stmt> {
       val statements = mutableListOf<Stmt>()

       while (!isAtEnd()) {
           declaration()?.let { statements.add(it) }
       }

       return statements
    }

    // Recursive descent!
    // i.e. top-down parsing from root/most encompassing rule, and recursive parsing corresponds
    // to a recursive function call

    // Points to the next token eagerly waiting to be parsed
    private var current = 0

    // Each grammar rule is a method that returns a syntax tree to the caller
    // A non-ambiguous efficient-enough grammar is chosen out of many possible grammars
    // In ascending order of precedence

    private fun expression(): Expr {
        return assignment()
    }

    private fun declaration(): Stmt? =
        try {
            when {
                match(TokenType.VAR) -> varDeclaration()
                else -> statement()
            }
        } catch (error: ParseError) {
            synchronize()
            null
        }

    private fun statement(): Stmt =
        when {
            match(TokenType.PRINT) -> printStatement()
            else -> expressionStatement()
        }

    private fun printStatement(): Stmt {
        val value = expression()
        consume(TokenType.SEMICOLON, "Expect ';' after value.")
        return Print(value)
    }

    private fun expressionStatement(): Stmt {
        val expr = expression()
        consume(TokenType.SEMICOLON, "Expect ';' after expression")
        return Expression(expr)
    }

    private fun varDeclaration(): Stmt {
        val name = consume(TokenType.IDENTIFIER, "Expect variable name.")

        val initializer = if (match(TokenType.EQUAL)) {
            expression()
        } else {
            null
        }

        consume(TokenType.SEMICOLON, "Expect ';' after variable declaration.")
        return Var(name, initializer)
    }

    private fun assignment(): Expr {
        // l-value can be anything - if this is even an assignment expression
        val expr = equality()

        return when {
            match(TokenType.EQUAL) -> {
                val equals = previous()
                // Right-associative assignments -> recursive calls here
                val value = assignment()

                when (expr) {
                    // Trick: After parsing the l-value, check if it falls under a list of allowed l-values
                    // that qualifies as a valid "assignment target", e.g. Variable reference Expr
                    // Notice that we kept using single token lookahead with no backtracking to achieve this
                    is Variable -> Assign(name = expr.name, value = value)
                    else -> {
                        error(equals, "Invalid assignment target.")
                        expr
                    }
                }
            }
            else -> expr
        }
    }

    private fun equality(): Expr {
        var expr = comparison()

        while (match(TokenType.BANG_EQUAL, TokenType.EQUAL_EQUAL)) {
            val operator = previous()
            val right = comparison()
            expr = Binary(expr, operator, right)
        }

        return expr
    }

    private fun comparison(): Expr {
        var expr = term()

        while (match(TokenType.GREATER, TokenType.GREATER_EQUAL, TokenType.LESS, TokenType.LESS_EQUAL)) {
            val operator = previous()
            val right = term()
            expr = Binary(expr, operator, right)
        }

        return expr
    }


    private fun term(): Expr {
        var expr = factor()

        while (match(TokenType.MINUS, TokenType.PLUS)) {
            val operator = previous()
            val right = factor()
            expr = Binary(expr, operator, right)
        }

        return expr
    }

    private fun factor(): Expr {
        var expr = unary()

        while (match(TokenType.SLASH, TokenType.STAR)) {
            val operator = previous()
            val right = unary()
            expr = Binary(expr, operator, right)
        }

        return expr
    }

    private fun unary(): Expr =
        if (match(TokenType.BANG, TokenType.MINUS)) {
            val operator = previous()
            val right = unary()
            Unary(operator, right)
        } else { primary() }

    private fun primary(): Expr =
        when {
            match(TokenType.FALSE) -> Literal(false)
            match(TokenType.TRUE) -> Literal(true)
            match(TokenType.NIL) -> Literal(null)
            match(TokenType.NUMBER, TokenType.STRING) -> Literal(previous().literal)
            match(TokenType.IDENTIFIER) -> Variable(previous())
            match(TokenType.LEFT_PAREN) -> {
                val expr = expression()
                consume(TokenType.RIGHT_PAREN, "Expect ')' after expression.")
                Grouping(expr)
            }
            else -> {
                throw Exception("Expect expression.")
            }
        }

    // Helpers

    /**
     * Check whether the current token matches one of the token types
     * Doesn't consume token, only peeks
     */
    private fun match(vararg types: TokenType): Boolean {
        return types.firstOrNull { check(it) }?.run {
            advance()
            true
        } ?: false
    }

    /**
     * Check whether the current token is of a given type
     * Consume it if so
     * Otherwise error out on some other token it sees
     */
    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) {
            return advance()
        }

        throw error(peek(), message)
    }

    /**
     * Check whether the current token is of a given token type
     * Doesn't consume token, only peeks
     */
    private fun check(type: TokenType): Boolean =
        if (isAtEnd()) { false } else { peek().type == type }

    /**
     * Consume current token and return it
     * (similar to scanner's)
     */
    private fun advance(): Token {
        if (!isAtEnd()) {
            current++
        }
        return previous()   // incremented current index, return the truncated last token from the token list
    }

    /**
     * Check whether we have run out of tokens to parse
     */
    private fun isAtEnd(): Boolean = peek().type == TokenType.EOF

    /**
     * Returns the next token to-be-consumed
     */
    private fun peek(): Token = tokens[current]

    /**
     * Returns the token just consumed
     */
    private fun previous(): Token = tokens[current - 1]

    /**
     * Synchronization: discard invalid tokens such that the parser can resume on another production
     * Until it thinks* it hits a statement boundary
     */
    private fun synchronize() {
        advance()

        while (!isAtEnd()) {
            if (previous().type == TokenType.SEMICOLON) { return }
            when (peek().type) {
                TokenType.CLASS, TokenType.FUN, TokenType.VAR,
                    TokenType.FOR, TokenType.IF, TokenType.WHILE,
                    TokenType.PRINT, TokenType.RETURN -> { return }
                else -> {}
            }
        }

        advance()
    }

    /**
     * Error
     */
    class ParseError: RuntimeException()

    private fun error(token: Token, message: String): ParseError {
        onError(token, message)
        return ParseError()
    }
}