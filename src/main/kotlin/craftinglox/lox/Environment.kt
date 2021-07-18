package craftinglox.lox

// Make interpreter remember things (a side effect) and do things referencing the memory (also side effect?)
class Environment(
    // Parent environment
    val enclosing: Environment?
) {
    // For global environment, as the "end of the chain"
    constructor(): this(null)

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
            // Walk the environment chain, ask the parent environment for the variable; recursively
            enclosing != null -> enclosing.get(name)
            // Semantics choice: Syntax vs Runtime error (don't use default null value please)
            // - Referring to a variable without necessarily evaluating it (e.g. in function body)
            //   probably should not cause it to statically fail (cannot compile),
            //   otherwise mutually recursive definitions are not possible
            // - Other choices could be: Java's declare all first before defining any (bodies), C's forward declarations
            else -> throw RuntimeError(name, "Undefined variable '$varName'.")
        }
    }

    fun assign(name: Token, value: Any?) {
        val varName = name.lexeme
        when {
            values.containsKey(varName) -> {
                values[varName] = value
            }
            // Assign to the parent environment if any; recursively
            enclosing != null -> {
                enclosing.assign(name, value)
            }
            // Restricted API to not allow creating new variables
            else -> {
                throw RuntimeError(name, "Undefined variable '$varName'.")
            }
        }

    }

    private val values = mutableMapOf<String, Any?>()
}