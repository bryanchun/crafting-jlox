package craftinglox.lox.tool

import kotlin.system.exitProcess
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import craftinglox.lox.Lox.PACKAGE_NAME
import java.nio.file.Paths


typealias TypeField = Pair<String, ClassName>
typealias TypeFields = List<TypeField>
typealias Types = Map<String, TypeFields>

object GenerateAst {

    private val Token = ClassName(PACKAGE_NAME, "Token")
    private val Expr = ClassName("${PACKAGE_NAME}.ast", "Expr")

    private val VisiteeType = TypeVariableName("R")

    private fun ClassName.nullable(): ClassName = copy(nullable = true, annotations = annotations, tags = tags)

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
                    outputDir,
                    packageName = "$PACKAGE_NAME.ast",
                    baseName = "Expr",
                    types = mapOf(
                        "Assign" to listOf(
                            "name" to Token,  // l-value
                            "value" to Expr,
                        ),
                        "Binary" to listOf(
                            "left" to Expr,
                            "operator" to Token,
                            "right" to Expr,
                        ),
                        "Grouping" to listOf(
                            "expression" to Expr,
                        ),
                        "Literal" to listOf(
                            "value" to ANY.nullable(),
                        ),
                        "Unary" to listOf(
                            "operator" to Token,
                            "right" to Expr,
                        ),
                        "Variable" to listOf(
                            "name" to Token,
                        ),
                    ),
                )

                defineAst(
                    outputDir,
                    packageName = "$PACKAGE_NAME.ast",
                    baseName = "Stmt",
                    types = mapOf(
                        "Expression" to listOf(
                            "expression" to Expr,
                        ),
                        "Print" to listOf(
                            "expression" to Expr,
                        ),
                        "Var" to listOf(
                            "name" to Token,
                            "initializer" to Expr.nullable(),
                        ),
                    ),
                )
            }
        }
    }

    private fun defineAst(outputDir: String, packageName: String, baseName: String, types: Types) {
        val base = buildBase(packageName, baseName, types)
        val file = FileSpec.builder(packageName, baseName).run {
            addType(base)

            types.forEach { (typeName, typeField) ->
                addType(
                    buildAstType(packageName, baseName, typeName, typeField)
                )
            }

            build()
        }

        file.writeTo(Paths.get(outputDir))
    }

    private fun buildBase(packageName: String, baseName: String, types: Types): TypeSpec {

        val visitor = TypeSpec.interfaceBuilder("Visitor").run {
            addTypeVariable(VisiteeType)

            addFunctions(
                types.map { (typeName, _) ->
                    FunSpec.builder("visit$typeName$baseName").run {
                        addModifiers(KModifier.ABSTRACT)

                        addParameter(
                            ParameterSpec.builder(baseName.toLowerCase(), ClassName(packageName, typeName)).build()
                        )
                        returns(VisiteeType)

                        build()
                    }
                }
            )

            build()
        }

        val visitorType = ClassName("$packageName.$baseName", "Visitor").run {
            parameterizedBy(VisiteeType)
        }

        return TypeSpec.classBuilder(baseName).run {
            addModifiers(KModifier.ABSTRACT)

            addType(visitor)

            addFunction(
                FunSpec.builder("accept").run {
                    addModifiers(KModifier.ABSTRACT)
                    addTypeVariable(VisiteeType)

                    addParameter(
                        ParameterSpec.builder(
                            "visitor",
                            visitorType
                        ).build()
                    )
                    returns(VisiteeType)

                    build()
                }
            )

            build()
        }
    }

    private fun buildAstType(packageName: String, superName: String, typeName: String, typeFields: TypeFields): TypeSpec =
        TypeSpec.classBuilder(typeName).run {
            addModifiers(KModifier.DATA)

            primaryConstructor(
                FunSpec.constructorBuilder().run {
                    addParameters(
                        typeFields.map { (fieldName, fieldType) ->
                            ParameterSpec.builder(fieldName, fieldType).build()
                        }
                    )
                    superclass(ClassName(packageName, superName))
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

            val visitorType = ClassName("$packageName.$superName", "Visitor").run {
                parameterizedBy(VisiteeType)
            }

            addFunction(
                FunSpec.builder("accept").run {
                    addModifiers(KModifier.OVERRIDE)
                    addTypeVariable(VisiteeType)

                    addParameter(
                        ParameterSpec.builder(
                            "visitor",
                            visitorType
                        ).run {
                            addStatement("return visitor.visit${typeName}${superName}(this)")

                            build()
                        }
                    )
                    returns(VisiteeType)

                    build()
                }
            )

            build()
        }
}