package craftinglox.lox.tool

import kotlin.system.exitProcess
import com.squareup.kotlinpoet.*
import craftinglox.lox.Lox.PACKAGE_NAME
import java.nio.file.Paths


typealias FieldName = String
typealias FieldType = ClassName
typealias Field = Pair<FieldName, FieldType>

typealias TypeName = String
typealias TypeFields = List<Field>
typealias Types = Map<TypeName, TypeFields>

object GenerateAst {

    const val packageName = "${PACKAGE_NAME}.expr"

    val Token = ClassName(PACKAGE_NAME, "Token")
    val Expr = ClassName(packageName, "Expr")

    val TYPES = mapOf(
        "Binary" to listOf(
            "left" to Expr,
            "operator" to Token,
            "right" to Expr,
        ),
        "Grouping" to listOf(
            "expression" to Expr,
        ),
        "Literal" to listOf(
            "value" to ANY,
        ),
        "Unary" to listOf(
            "operator" to Token,
            "right" to Expr,
        )
    )

    @JvmStatic
    fun main(args: Array<String>) {
        when {
            args.size != 1 -> {
                println("Usage: generate_ast <output_directory>")
                exitProcess(64)
            }
            else -> {
                val outputDir = args[0]

                defineAst(
                    packageName,
                    "Expr",
                    TYPES,
                    outputDir,
                )
            }
        }
    }


    fun defineAst(packageName: String, baseName: String, types: Types, outputDir: String) {
        val base = TypeSpec.interfaceBuilder(baseName).build()
        val file = FileSpec.builder(packageName, baseName).run {
            addType(base)

            types.forEach { (typeName, typeField) ->
                addType(
                    buildAstType(baseName, typeName, typeField)
                )
            }

            build()
        }

        file.writeTo(Paths.get(outputDir))
    }

    fun buildAstType(superName: String, typeName: String, typeFields: TypeFields): TypeSpec =
        TypeSpec.classBuilder(typeName).run {
            addModifiers(KModifier.DATA)

            primaryConstructor(
                FunSpec.constructorBuilder().run {
                    addParameters(
                        typeFields.map { (fieldName, fieldType) ->
                            ParameterSpec.builder(fieldName, fieldType).build()
                        }
                    )
                    addSuperinterface(ClassName("", superName))
                    build()
                }
            )

            addProperties(
                typeFields.map { (fieldName, fieldType) ->
                    PropertySpec.builder(fieldName, fieldType).run {
                        initializer(fieldName)
                        build()
                    }
                }
            )

            build()
        }
}