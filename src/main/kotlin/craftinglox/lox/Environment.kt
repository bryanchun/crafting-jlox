package craftinglox.lox

// Make interpreter remember things (a side effect) and do things referencing the memory (also side effect?)
class Environment(
    // Parent environment
    val enclosing: Environment?
) {

    private val values = mutableMapOf<String, Any?>()
    private val uninitialized = mutableSetOf<String>()

    // For global environment, as the "end of the chain"
    constructor(): this(null)

    // Public API
    fun define(name: String, value: Any?, initialized: Boolean = true) {
        // Semantics choice: Always re-define variable without checking if it's already defined
        // - At least at the toplevel (global variables)
        if (initialized) {
            values[name] = value
        } else {
            uninitialized.add(name)
        }
    }

    fun get(name: Token): Any? {
        val varName = name.lexeme
        return when {
            values.containsKey(varName) -> values[varName]
            // Walk the environment chain, ask the parent environment for the variable; recursively
            enclosing != null -> enclosing.get(name)
            uninitialized.contains(varName) -> throw RuntimeError(name, "Uninitialized variable '$varName'")
            // Semantics choice: Syntax vs Runtime error (don't use default null value please)
            // - Referring to a variable without necessarily evaluating it (e.g. in function body)
            //   probably should not cause it to statically fail (cannot compile),
            //   otherwise mutually recursive definitions are not possible
            // - Other choices could be: Java's declare all first before defining any (bodies), C's forward declarations
            else -> throw RuntimeError(name, "Undefined variable '$varName'.")
        }
    }

    fun getAt(distance: Int, name: String) = ancestor(distance).values[name]

    fun assignAt(distance: Int, name: Token, value: Any?) {
        ancestor(distance).values[name.lexeme] = value
    }

    fun ancestor(distance: Int): Environment {
        var environment: Environment = this
        // Fixed number of hops using the distance
        repeat(distance) {
            // Deep trust/coupling in the resolver that the exact number of hops with the distance
            // will bring the environment to the one that defined the variable
            environment = environment.enclosing!!
        }
        return environment
    }

    fun assign(name: Token, value: Any?) {
        val varName = name.lexeme
        when {
            values.containsKey(varName) || uninitialized.contains(varName) -> {
                values[varName] = value
                uninitialized.removeIf { it == varName }
            }
            // Assign to the parent environment if any; recursively
            enclosing != null -> {
                enclosing.assign(name, value)
            }
            // Restricted API to not allow creating new variables
            else -> throw RuntimeError(name, "Undefined variable '$varName'.")
        }

    }
}