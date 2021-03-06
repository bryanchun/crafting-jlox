package craftinglox.lox

class Instance(
    private val clazz: Class
) {
    // An instance stores states.
    private val fields = mutableMapOf<String, Any?>()

    fun get(name: Token): Any? {
        // Optionally used to cache looked up method
        var method: Function? = null

        return when {
            fields.containsKey(name.lexeme) -> fields[name.lexeme]
            clazz.findMethod(name.lexeme)?.also { method = it } != null -> method?.bind(this)
            else -> throw RuntimeError(name, "Undefined property '${name.lexeme}'.")
        }
    }

    fun set(name: Token, value: Any?) {
        // Semantic choice: Freely stuff into an instance's field: there is no need to check whether a field is
        // (statically) present/defined already.
        fields[name.lexeme] = value
    }

    override fun toString(): String = "${clazz.name} instance"
}