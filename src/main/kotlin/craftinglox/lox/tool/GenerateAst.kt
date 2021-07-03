package craftinglox.lox.tool

import kotlin.system.exitProcess
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
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
    const val BASE_NAME = "Expr"

    val Token = ClassName(PACKAGE_NAME, "Token")
    val Expr = ClassName(packageName, BASE_NAME)

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

    val visiteeType = TypeVariableName("R")
    val visitorType = ClassName("$packageName.$BASE_NAME", "Visitor").run {
        parameterizedBy(visiteeType)
    }

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
                    BASE_NAME,
                    TYPES,
                    outputDir,
                )
            }
        }
    }

    fun defineAst(packageName: String, baseName: String, types: Types, outputDir: String) {
        val base = buildBase(baseName, types)
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

    fun buildBase(baseName: String, types: Types): TypeSpec {

        val visitor = TypeSpec.interfaceBuilder("Visitor").run {
            addTypeVariable(visiteeType)

            addFunctions(
                types.map { (typeName, _) ->
                    FunSpec.builder("visit$typeName$baseName").run {
                        addModifiers(KModifier.ABSTRACT)

                        addParameter(
                            ParameterSpec.builder(baseName.toLowerCase(), ClassName(packageName, typeName)).build()
                        )
                        returns(visiteeType)

                        build()
                    }
                }
            )

            build()
        }

        return TypeSpec.classBuilder(baseName).run {
            addModifiers(KModifier.ABSTRACT)

            addType(visitor)

            addFunction(
                FunSpec.builder("accept").run {
                    addModifiers(KModifier.ABSTRACT)
                    addTypeVariable(visiteeType)

                    addParameter(
                        ParameterSpec.builder(
                            "visitor",
                            visitorType
                        ).build()
                    )
                    returns(visiteeType)

                    build()
                }
            )

            build()
        }
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
                    superclass(ClassName("", superName))
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

            addFunction(
                FunSpec.builder("accept").run {
                    addModifiers(KModifier.OVERRIDE)
                    addTypeVariable(visiteeType)

                    addParameter(
                        ParameterSpec.builder(
                            "visitor",
                            visitorType
                        ).run {
                            addStatement("return visitor.visit${typeName}${superName}(this)")

                            build()
                        }
                    )
                    returns(visiteeType)

                    build()
                }
            )

            build()
        }
}