package craftinglox.lox

class Class(
    val name: String
) : Callable {

    override fun arity(): Int = 0

    override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? = Instance(this)

    override fun toString(): String = name
}