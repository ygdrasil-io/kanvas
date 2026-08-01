package org.graphiks.kanvas.gpu.renderer.wgsl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.materials.stubPreparedMaterialProgram
import org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesLayoutAuthority
import org.graphiks.kanvas.gpu.renderer.vertices.GPUVertexMode
import org.graphiks.wgsl.ast.Attribute
import org.graphiks.wgsl.ast.FunctionDecl
import org.graphiks.wgsl.ast.IdentExpr
import org.graphiks.wgsl.ast.IntLiteral
import org.graphiks.wgsl.ast.MatrixType
import org.graphiks.wgsl.ast.ScalarKind
import org.graphiks.wgsl.ast.ScalarType
import org.graphiks.wgsl.ast.StructDecl
import org.graphiks.wgsl.ast.StructMember
import org.graphiks.wgsl.ast.TypeDecl
import org.graphiks.wgsl.ast.VectorType
import org.graphiks.wgsl.ir.ShaderStage
import org.graphiks.wgsl.parser.Lowerer
import org.graphiks.wgsl.parser.parseWgslResult

class PreparedVerticesShaderTest {
    @Test
    fun `all four layouts and both topologies expose the exact parser proven vertex fragment interface`() {
        val material = stubPreparedMaterialProgram()
        val variants = listOf(false to false, true to false, false to true, true to true)
            .flatMap { (hasColor, hasTexCoord) ->
                listOf(GPUVertexMode.Triangles, GPUVertexMode.TriangleStrip).map { topology ->
                    Variant(hasColor, hasTexCoord, topology)
                }
            }

        variants.forEach { variant ->
            val layout = GPUPreparedVerticesLayoutAuthority.layout(
                hasColors = variant.hasColor,
                hasTexCoords = variant.hasTexCoord,
            )
            val result = PreparedVerticesShaderAssembler.assemble(
                layout = layout,
                topology = variant.topology,
                material = material,
                hasPrimitiveColor = variant.hasColor,
            )
            val program = assertIs<GPUPreparedVerticesShaderResult.Ready>(
                result,
                variant.toString(),
            ).program

            val parsed = parseWgslResult(program.wgslSource)
            assertTrue(parsed.isSuccess, parsed.errors.joinToString { it.message })
            val lowered = Lowerer().lower(parsed.translationUnit)
            assertEquals(
                listOf("vs_main" to ShaderStage.Vertex, "fs_main" to ShaderStage.Fragment),
                lowered.entryPoints.map { it.name to it.stage },
                variant.toString(),
            )

            val input = parsed.translationUnit.declarations
                .filterIsInstance<StructDecl>()
                .single { it.name == "PreparedVerticesVertexInput" }
            assertEquals(
                buildList {
                    add(ParsedMember("position", "vec2<f32>", location = 0))
                    if (variant.hasColor) {
                        add(ParsedMember("primitiveColor", "vec4<f32>", location = 1))
                    }
                    if (variant.hasTexCoord) {
                        add(ParsedMember("texCoord", "vec2<f32>", location = 2))
                    }
                },
                input.members.map(StructMember::parsedMember),
                variant.toString(),
            )

            val output = parsed.translationUnit.declarations
                .filterIsInstance<StructDecl>()
                .single { it.name == "PreparedVerticesVertexOutput" }
            assertEquals(
                buildList {
                    add(ParsedMember("position", "vec4<f32>", builtin = "position"))
                    add(
                        ParsedMember(
                            "localPosition",
                            "vec2<f32>",
                            location = 0,
                            interpolation = "perspective:center",
                        ),
                    )
                    if (variant.hasColor) {
                        add(
                            ParsedMember(
                                "primitiveColor",
                                "vec4<f32>",
                                location = 1,
                                interpolation = "perspective:center",
                            ),
                        )
                    }
                },
                output.members.map(StructMember::parsedMember),
                variant.toString(),
            )

            val drawUniforms = parsed.translationUnit.declarations
                .filterIsInstance<StructDecl>()
                .single { it.name == "PreparedVerticesDrawUniforms" }
            assertEquals(
                listOf(
                    ParsedMember("localToDevice", "mat3x3<f32>"),
                    ParsedMember("targetSize", "vec2<f32>"),
                    ParsedMember("_padding", "vec2<f32>"),
                ),
                drawUniforms.members.map(StructMember::parsedMember),
            )

            val fragment = parsed.translationUnit.declarations
                .filterIsInstance<FunctionDecl>()
                .single { it.name == "fs_main" }
            assertEquals("vec4<f32>", assertNotNull(fragment.returnType).wgslType())
            assertEquals(0L, fragment.returnAttributes.singleNamed("location").intArgument())
            assertTrue(
                program.wgslSource.contains(
                    "kanvas_evaluate_material(input.localPosition)",
                ),
            )
            if (variant.hasColor) {
                assertTrue(program.wgslSource.contains("materialPremul * input.primitiveColor"))
            } else {
                assertTrue(program.wgslSource.contains("return materialPremul;"))
            }
        }
    }

    private data class Variant(
        val hasColor: Boolean,
        val hasTexCoord: Boolean,
        val topology: GPUVertexMode,
    )
}

private data class ParsedMember(
    val name: String,
    val type: String,
    val location: Long? = null,
    val builtin: String? = null,
    val interpolation: String? = null,
)

private fun StructMember.parsedMember(): ParsedMember = ParsedMember(
    name = name,
    type = type.wgslType(),
    location = attributes.singleNamedOrNull("location")?.intArgument(),
    builtin = attributes.singleNamedOrNull("builtin")?.identifierArgument(),
    interpolation = attributes.singleNamedOrNull("interpolate")?.let { attribute ->
        attribute.args.joinToString(":") { argument ->
            assertIs<IdentExpr>(argument).name
        }
    },
)

private fun TypeDecl.wgslType(): String = when (this) {
    is ScalarType -> when (kind) {
        ScalarKind.F32 -> "f32"
        else -> error("Unexpected scalar type $kind")
    }
    is VectorType -> "vec$size<${elementType.wgslType()}>"
    is MatrixType -> "mat${columns}x$rows<${elementType.wgslType()}>"
    else -> error("Unexpected WGSL type ${this::class.simpleName}")
}

private fun List<Attribute>.singleNamed(name: String): Attribute =
    single { it.name == name }

private fun List<Attribute>.singleNamedOrNull(name: String): Attribute? =
    singleOrNull { it.name == name }

private fun Attribute.intArgument(): Long = assertIs<IntLiteral>(args.single()).value

private fun Attribute.identifierArgument(): String = assertIs<IdentExpr>(args.single()).name
