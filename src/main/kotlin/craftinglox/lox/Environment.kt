package craftinglox.lox

class Environment {

    // Public API
    fun define(name: String, value: Any?) {
        // Semantics choice: Always re-define variable without checking if it's already defined
        // - At least at the toplevel (global variables)
        values[name] = value
    }

    fun get(name: Token): Any? {
        val varName = name.lexeme
        return when {
            values.containsKey(varName) -> values[varName]
            // Semantics choice: Syntax vs Runtime error (don't use default null value please)
            // - Referring to a variable without necessarily evaluating it (e.g. in function body)
            //   probably should not cause it to statically fail (cannot compile),
            //   otherwise mutually recursive definitions are not possible
            // - Other choices could be: Java's declare all first before defining any (bodies), C's forward declarations
            else -> RuntimeError(name, "Undefined variable '$varName'.")
        }
    }

    private val values = mutableMapOf<String, Any?>()
}