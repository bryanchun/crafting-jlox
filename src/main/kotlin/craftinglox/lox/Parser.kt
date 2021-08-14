package craftinglox.lox

import craftinglox.lox.ast.*
import craftinglox.lox.ast.Function
import craftinglox.lox.ast.Lambda
import java.lang.Exception
import java.lang.RuntimeException

class Parser(private val tokens: List<Token>, private val onError: (Token, String) -> Unit) {

    // Public API
    fun parse(): Interpretable =
         parseStatements(onFailure = {
            synchronize()
            reset()

            repl(onSuccess = {
                return@parse Interpretable.Expression(it)
            })
        }).let { Interpretable.Statements(it) }

    private inline fun parseStatements(onFailure: () -> Unit): List<Stmt> {
        val statements = mutableListOf<Stmt>()
        while (!isAtEnd()) {
            try {
                val decl = declaration()
                statements.add(decl)
            } catch (stmtParseError: ParseError) {
                onFailure()
                stmtParseError.errorOut()
            }
        }
        return statements
    }

    private inline fun repl(onSuccess: (Expr?) -> Unit) =
        try {
            val expr = expression()
            if (isAtEnd()) {
                expr
            } else {
                onError(peek(), "Invalid expression")
                null
            }.let(onSuccess)
        } catch (exprParseError: ParseError) {
            exprParseError.errorOut()
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

    private fun declaration(): Stmt =
        when {
            match(TokenType.CLASS) -> classDeclaration()
            check2(TokenType.FUN, TokenType.IDENTIFIER) && match(TokenType.FUN) -> function("function")
            match(TokenType.VAR) -> varDeclaration()
            else -> statement()
        }

    // 'Statement' is generic: multiline statements are also Stmt
    private fun statement(): Stmt =
        when {
            match(TokenType.FOR) -> forStatement()
            match(TokenType.IF) -> ifStatement()
            match(TokenType.PRINT) -> printStatement()
            match(TokenType.RETURN) -> returnStatement()
            match(TokenType.WHILE) -> whileStatement()
            match(TokenType.LEFT_BRACE) -> Block(block())
            else -> expressionStatement()
        }

    // Demonstrating syntactic sugar: without changing the interpreter, we parse for syntax
    // as while + block expressions up for interpretation
    private fun forStatement(): Stmt {
        consume(TokenType.LEFT_PAREN, "Expect '(' after 'for'.")

        val initializer = when {
            match(TokenType.SEMICOLON) -> null
            match(TokenType.VAR) -> varDeclaration()
            else -> expressionStatement()
        }

        val condition = when {
            !check(TokenType.SEMICOLON) -> expression()
            else -> null
        }
        consume(TokenType.SEMICOLON, "Expect ';' after loop condition.")

        val increment = when {
            !check(TokenType.RIGHT_PAREN) -> expression()
            else -> null
        }
        consume(TokenType.RIGHT_PAREN, "Expect ')' after for clauses.")

        // Desugar for-loop syntax
        var body = statement()

        // Append increment to end of body
        increment?.also {
            body = Block(statements = listOf(body, Expression(it)))
        }

        body = While(
            condition = condition ?: Literal(true),
            body
        )

        initializer?.also {
            // By wrapping the initializer line into a block, we preserve the for-loop scoping
            // of the initializer variable by using what blocks already offer
            body = Block(statements = listOf(it, body))
        }

        return body
    }

    private fun ifStatement(): Stmt {
        consume(TokenType.LEFT_PAREN, "Expect '(' after 'if'.")
        val condition = expression()
        consume(TokenType.RIGHT_PAREN, "Expect ')' after condition.")

        // Semantic choice: (hack to avoid extra work) eagerly collect statements under the then branch to
        // avoid the dangling else problem

        // Note that since statement contains blocks, we can accept a block of statements too,
        // but it can also be a single statement without braces
        val thenBranch = statement()
        val elseBranch = if (match(TokenType.ELSE)) { statement() } else { null }

        return If(condition, thenBranch, elseBranch)
    }

    private fun printStatement(): Stmt {
        val value = expression()
        consume(TokenType.SEMICOLON, "Expect ';' after value.")
        return Print(value)
    }

    private fun returnStatement(): Stmt {
        val keyboard = previous()
        val value = when {
            !check(TokenType.SEMICOLON) -> expression()
            // Since a semicolon cannot begin an expression as the grammar specifies.
            else -> null
        }

        consume(TokenType.SEMICOLON, "Expect ';' after return value.")
        return Return(keyboard, value)
    }

    private fun expressionStatement(): Stmt {
        val expr = expression()
        consume(TokenType.SEMICOLON, "Expect ';' after expression.")
        return Expression(expr)
    }

    private fun classDeclaration(): Stmt {
        val name = consume(TokenType.IDENTIFIER, "Expect class name.")

        // Superclass is an optional variable
        // - We have no root object to do implicit inheritance from
        // - Using a variable can make us parse more precisely than a mere token that does not refer to a defined class
        val superclass = when {
            match(TokenType.LESS) -> {
                consume(TokenType.IDENTIFIER, "Expect superclass name.")
                Variable(previous())
            }
            else -> null
        }

        consume(TokenType.LEFT_BRACE, "Expect '{' before class body.")

        val methods = mutableListOf<Function>()
        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
            methods.add(function("method"))
        }

        consume(TokenType.RIGHT_BRACE, "Expect '}' after class body.")

        return Class(name, superclass, methods)
    }

    private fun function(kind: String): Function {
        // kind for error reporting, telling between different kinds of functions, e.g. class methods too
        val name = consume(TokenType.IDENTIFIER, "Expect $kind name.")

        val (params, body) = functionParamsBody(kind)

        return Function(name, params, body)
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

    private fun whileStatement(): Stmt {
        consume(TokenType.LEFT_PAREN, "Expect '(' after 'while'.")
        val condition = expression()
        consume(TokenType.RIGHT_PAREN, "Expect ')' after condition.")

        val body = statement()
        return While(condition, body)
    }

    /**
     * Handle both expression assignment and set expression (property assignment)
     */
    private fun assignment(): Expr {
        // l-value can be anything - if this is even an assignment expression
        val expr = or()

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
                    //
                    is Get -> Set(expr.`object`, expr.name, value)
                    else -> {
                        error(equals, "Invalid assignment target.")
                        expr
                    }
                }
            }
            else -> expr
        }
    }

    private fun or(): Expr = fold(::Logical, TokenType.OR) { and() }

    private fun and(): Expr = fold(::Logical, TokenType.AND) { lambda() }

    private fun lambda(): Expr =
        when {
            match(TokenType.FUN) -> {
                val (params, body) = functionParamsBody(kind = null)
                Lambda(params, body)
            }
            else -> equality()
        }

    // List of statements with an ending right brace (left brace is not expected)
    private fun block(): List<Stmt> {
        val statements = mutableListOf<Stmt>()

        // Check is not end of source because even when parsing invalid code, anytime without '}' terminating
        // we can run into the trouble of an infinite parsing loop (declaration -> statement -> block -> declaration)
        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
            declaration().also { statements.add(it) }
        }

        consume(TokenType.RIGHT_BRACE, "Expect '}' after block.")
        return statements
    }

    private fun equality(): Expr = fold(::Binary, TokenType.BANG_EQUAL, TokenType.EQUAL_EQUAL) { comparison() }

    private fun comparison(): Expr = fold(::Binary,
        TokenType.GREATER, TokenType.GREATER_EQUAL, TokenType.LESS, TokenType.LESS_EQUAL) { term() }

    private fun term(): Expr = fold(::Binary, TokenType.MINUS, TokenType.PLUS) { factor() }

    private fun factor(): Expr = fold(::Binary, TokenType.SLASH, TokenType.STAR) { unary() }

    private fun unary(): Expr =
        if (match(TokenType.BANG, TokenType.MINUS)) {
            val operator = previous()
            val right = unary()
            Unary(operator, right)
        } else {
            call()
        }

    /**
     * Handle both function call syntax and get expression (property access by dot operator)
     */
    private fun call(): Expr {
        var expr = primary()

        // This loop deals with curried function calls.
        while (true) {
            expr = when {
                match(TokenType.LEFT_PAREN) -> {
                    finishCall(expr)
                }
                match(TokenType.DOT) -> {
                    val name = consume(TokenType.IDENTIFIER, "Expect property name after '.'.")
                    Get(expr, name)
                }
                else -> break
            }
        }

        return expr
    }

    private fun finishCall(callee: Expr): Expr {
        val arguments = mutableListOf<Expr>()

        if (!check(TokenType.RIGHT_PAREN)) {
            // If the next token is not already right paren
            // Parse at least one argument
            do {
                // Upperbound number of arguments to take
                if (arguments.size >= 255) {
                    // Report and do not panic, keep going
                    error(peek(), "Can't have more than 255 arguments.")
                }

                arguments.add(expression())
            } while (match(TokenType.COMMA))
        }

        val token = consume(TokenType.RIGHT_PAREN, "Expect ')' after arguments.")

        return Call(callee, token, arguments.toList())
    }

    private fun primary(): Expr =
        when {
            match(TokenType.FALSE) -> Literal(false)
            match(TokenType.TRUE) -> Literal(true)
            match(TokenType.NIL) -> Literal(null)
            match(TokenType.NUMBER, TokenType.STRING) -> Literal(previous().literal)
            match(TokenType.SUPER) -> {
                // 'super' syntax must be dot-accessed, it cannot stand alone
                val keyword = previous()
                consume(TokenType.DOT, "Expect '.' after 'super'.")
                val token = consume(TokenType.IDENTIFIER, "Expect superclass method name.")
                Super(keyword, token)
            }
            match(TokenType.THIS) -> This(previous())
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
     * Check whether the next 2 tokens is of the given token types
     * Doesn't consume token, only peeks
     */
    private fun check2(first: TokenType, second: TokenType): Boolean {
        val next = peek()
        val nextNext = tokens[current + 1]

        return nextNext.type != TokenType.EOF && next.type == first && nextNext.type == second
    }

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
     * Reset current cursor to beginning so that parser can re-parse a second pass
     */
    private fun reset() {
        current = 0
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
     * Fold chained operators in a reusable fashion
     */
    private fun fold(binaryExpr: (Expr, Token, Expr) -> Expr, vararg tokenTypes: TokenType, target: () -> Expr): Expr {
        var expr = target()

        while (match(*tokenTypes)) {
            val operator = previous()
            val right = target()
            expr = binaryExpr(expr, operator, right)
        }

        return expr
    }

    /**
     * Parse function parameters and body as a Lambda
     */
    private fun functionParamsBody(kind: String?): Pair<List<Token>, List<Stmt>> {
        consume(TokenType.LEFT_PAREN,
            kind?.let { "Expect '(' after $it name." }
                // No kind (name) means this is a lambda.
                ?: "Expect '(' after 'fun'."
        )

        val parameters = mutableListOf<Token>()
        if (!check(TokenType.RIGHT_PAREN)) {
            do {
                if (parameters.size >= 255) {
                    error(peek(), "Can't have more than 255 parameters.")
                }

                parameters.add(
                    consume(TokenType.IDENTIFIER, "Expect parameter name.")
                )
            } while (match(TokenType.COMMA))
        }

        consume(TokenType.RIGHT_PAREN, "Expect ')' after parameters.")

        // Reporting left brace parsing error before parsing a block, so that we have more context in the caller
        consume(TokenType.LEFT_BRACE, "Expect '{' before $kind body.")
        val body = block()

        return parameters to body
    }

    /**
     * Error
     */
    class ParseError(val errorOut: () -> Unit): RuntimeException()

    private fun error(token: Token, message: String): ParseError {
        return ParseError { onError(token, message) }
    }
}