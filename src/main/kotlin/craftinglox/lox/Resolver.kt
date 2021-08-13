package craftinglox.lox

import craftinglox.lox.ast.*
import craftinglox.lox.ast.Function
import craftinglox.lox.ast.Lambda
import craftinglox.lox.ast.Return

/**
 * Semantic (static) analysis pass for resolving variable declarations
 * in between parsing and interpreting
 *
 * - Binding variables to define-time values (so that they are persistent)
 *   Do this globally before interpretation so that interpreter runs with global information
 * - Rule-based semantics enforcement: local-var-redefinition, return-not-in-function
 * - TODO: Check for any unused local variables
 */
class Resolver(
    private val interpreter: Interpreter,
    private val onError: (Token, String) -> Unit,
) : Expr.Visitor<Unit?>, Stmt.Visitor<Unit?> {

    // Public API
    fun resolve(interpretable: Interpretable) {
        when (interpretable) {
            is Interpretable.Expression -> interpretable.expr?.let { resolve(it) }
            is Interpretable.Statements -> resolve(interpretable.statements)
        }
    }

    // Scopes is a stack of block scopes
    // Each scope is a mapping of variable names bound to its resolution status (resolved or not)
    // Note: Global variables are not handled / resolved since we ignore them to be more dynamic anyway
    private val scopes: MutableList<MutableMap<String, Boolean>> = mutableListOf()

    private var currentFunction: FunctionType = FunctionType.NONE

    enum class FunctionType { NONE, FUNCTION }

    // Core logic
    override fun visitBlockStmt(stmt: Block): Unit? {
        withScope {
            resolve(stmt.statements)
        }

        return null
    }

    override fun visitVarStmt(stmt: Var): Unit? {
        declare(stmt.name)
        stmt.initializer?.also { resolve(it) }
        define(stmt.name)

        return null
    }

    override fun visitVariableExpr(expr: Variable): Unit? {
        when {
            scopes.isNotEmpty() && scopes.last()[expr.name.lexeme] == false -> {
                onError(expr.name, "Can't read local variable in its own initializer.")
            }
            else -> resolveLocal(expr, expr.name)
        }

        return null
    }

    override fun visitAssignExpr(expr: Assign): Unit? {
        resolve(expr.value)
        resolveLocal(expr, expr.name)

        return null
    }

    override fun visitFunctionStmt(stmt: Function): Unit? {
        declare(stmt.name)
        define(stmt.name)
        // Define name eagerly to enable recursive resolutions when function calls recursively

        resolveFunction(stmt, FunctionType.FUNCTION)

        return null
    }

    // Other AST nodes
    override fun visitExpressionStmt(stmt: Expression): Unit? {
        resolve(stmt.expression)

        return null
    }

    override fun visitIfStmt(stmt: If): Unit? {
        resolve(stmt.condition)
        // Being conservative in semantic analysis means unlike
        // runtime/dynamic execution, we resolve both then and else branches
        resolve(stmt.thenBranch)
        stmt.elseBranch?.also { resolve(it) }

        return null
    }

    override fun visitPrintStmt(stmt: Print): Unit? {
        resolve(stmt.expression)

        return null
    }

    override fun visitReturnStmt(stmt: Return): Unit? {
        // Return statements can only be used in function declarations
        if (currentFunction == FunctionType.NONE) {
            onError(stmt.keyword, "Can't return from top-level code.")
        }

        stmt.value?.also { resolve(it) }

        return null
    }

    override fun visitWhileStmt(stmt: While): Unit? {
        resolve(stmt.condition)
        resolve(stmt.body)

        return null
    }

    override fun visitBinaryExpr(expr: Binary): Unit? {
        resolve(expr.left)
        resolve(expr.right)

        return null
    }

    override fun visitCallExpr(expr: Call): Unit? {
        resolve(expr.callee)
        expr.arguments.forEach { resolve(it) }

        return null
    }

    override fun visitGroupingExpr(expr: Grouping): Unit? {
        resolve(expr.expression)

        return null
    }

    override fun visitLambdaExpr(expr: Lambda): Unit? {
        resolveLambda(expr)

        return null
    }

    override fun visitLiteralExpr(expr: Literal): Unit? {
        // Since literal contains no variables and subexpressions, do nothing
        return null
    }

    override fun visitLogicalExpr(expr: Logical): Unit? {
        resolve(expr.left)
        resolve(expr.right)

        return null
    }

    override fun visitUnaryExpr(expr: Unary): Unit? {
        resolve(expr.right)

        return null
    }

    // Helpers
    private fun resolve(statements: List<Stmt>) = statements.forEach { resolve(it) }
    private fun resolve(stmt: Stmt) = stmt.accept(this)
    private fun resolve(expr: Expr) = expr.accept(this)

    private fun resolveLocal(expr: Expr, name: Token) {
        for ((numParentHops, scope) in scopes.reversed().withIndex()) {
            if (scope.containsKey(name.lexeme)) {
                interpreter.resolve(expr, numParentHops)
                return
            }
        }
        // If after going through all the scopes and the name is still unresolved
        // we assume this is a global variable
    }

    private fun resolveFunction(function: Function, type: FunctionType) {
        val enclosingFunction = currentFunction
        currentFunction = type

        withScope {
            function.params.forEach {
                declare(it)
                define(it)
            }
            // In semantic analysis, we eagerly resolve things inside the function body
            // exactly once
            // Even though it was not called
            resolve(function.body)
        }

        currentFunction = enclosingFunction
    }

    private fun resolveLambda(lambda: Lambda) {
        withScope {
            lambda.params.forEach {
                declare(it)
                define(it)
            }
            resolve(lambda.body)
        }
    }

    private fun withScope(action: () -> Unit) {
        // Begin
        scopes.add(mutableMapOf())

        action()

        // End
        scopes.removeLast()
    }

    private fun declare(name: Token) {
        when {
            scopes.isEmpty() -> return
            else -> {
                // Note: these are in-place mutation by reference
                val scope = scopes.last()

                // Static error: Cannot redefine local variable
                if (scope.containsKey(name.lexeme)) {
                    onError(name, "Already a variable with this name in this scope.")
                }

                // Marked as false since variable is not ready for use before getting initialized
                scope[name.lexeme] = false
            }
        }
    }

    private fun define(name: Token) {
        when {
            scopes.isEmpty() -> return
            else -> {
                val scope = scopes.last()
                // Marked as ready for use after getting initialized
                scope[name.lexeme] = true
            }
        }
    }
}