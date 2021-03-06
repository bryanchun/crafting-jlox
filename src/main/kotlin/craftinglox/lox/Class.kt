package craftinglox.lox

class Class(
    val name: String,
    val superclass: Class?,
    // A class stores behaviors.
    private val methods: Map<String, Function>,
) : Callable {

    fun findMethod(name: String): Function? =
        methods.takeIf { it.containsKey(name) }?.get(name)
            // If the current class does not contain the method
            // Recursively look up the method in the superclass (if any)
            ?: superclass?.let { it.findMethod(name) }

    override fun arity(): Int = findMethod("init")?.arity() ?: 0

    // Constructor
    override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
        val instance = Instance(this)
        val initializer = findMethod("init")
        initializer?.bind(instance)?.call(interpreter, arguments)
        return instance
    }

    override fun toString(): String = name
}