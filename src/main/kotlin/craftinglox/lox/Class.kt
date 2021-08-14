package craftinglox.lox

class Class(
    val name: String,
    // A class stores behaviors.
    private val methods: Map<String, Function>,
) : Callable {

    fun findMethod(name: String): Function? =
        methods.takeIf { it.containsKey(name) }?.get(name)

    override fun arity(): Int = 0

    override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? = Instance(this)

    override fun toString(): String = name
}