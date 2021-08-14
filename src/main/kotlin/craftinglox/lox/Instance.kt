package craftinglox.lox

class Instance(
    private val clazz: Class
) {

    override fun toString(): String = "${clazz.name} instance"
}