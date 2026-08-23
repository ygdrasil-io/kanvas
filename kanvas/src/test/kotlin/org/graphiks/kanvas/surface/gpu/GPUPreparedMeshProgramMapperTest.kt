package org.graphiks.kanvas.surface.gpu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUPreparedBlenderChildDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUPreparedColorFilterChildDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURuntimeEffectChildDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURuntimeEffectUniformValue
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Blender
import org.graphiks.kanvas.paint.BlenderChild
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.ColorFilterChild
import org.graphiks.kanvas.paint.MeshChild
import org.graphiks.kanvas.paint.MeshChildren
import org.graphiks.kanvas.paint.MeshProgram
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.ShaderChild
import org.graphiks.kanvas.pipeline.ChildSlot
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.ShaderModule
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.pipeline.UniformLayout
import org.graphiks.kanvas.types.Color
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.kanvas.types.Mesh
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.VertexMode
import org.graphiks.kanvas.types.Vertices

class GPUPreparedMeshProgramMapperTest {
    @Test
    fun `mesh program maps exact effect uniforms copied matrices and ordered typed children`() {
        val matrix4 = FloatArray(16) { index -> index.toFloat() }
        val colorMatrix = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        val mutableEntries = mutableListOf(
            MeshChildren.Entry("shader", ShaderChild(Shader.SolidColor(Color.RED))),
            MeshChildren.Entry("filter", ColorFilterChild(ColorFilter.Matrix(colorMatrix))),
            MeshChildren.Entry("blender", BlenderChild(Blender.Mode(BlendMode.PLUS))),
        )
        val program = MeshProgram(
            effect = effect("mesh.registered", children = emptyList()),
            uniforms = UniformBlock {
                float1("amount", 0.25f)
                float2("pair", 1f, 2f)
                float3("triple", 3f, 4f, 5f)
                float4("color", 0.25f, 0.5f, 0.75f, 1f)
                int1("count", 7)
                mat3x3("matrix3", Matrix3x3F32.translation(3f, 5f))
                mat4x4("matrix4", matrix4)
            },
            children = MeshChildren(mutableEntries),
        )

        val mapping = program.toPreparedMeshProgramMapping(paintAlpha = 0.5f)
        matrix4.fill(99f)
        colorMatrix.fill(99f)
        mutableEntries.clear()

        assertEquals("mesh.registered", mapping.descriptor.effectId)
        assertEquals(1, mapping.descriptor.descriptorVersion)
        assertEquals(0.5f, mapping.paintAlpha)
        assertEquals(
            GPURuntimeEffectUniformValue.Float1(0.25f),
            mapping.descriptor.uniforms.getValue("amount"),
        )
        assertEquals(
            GPURuntimeEffectUniformValue.Float2(1f, 2f),
            mapping.descriptor.uniforms.getValue("pair"),
        )
        assertEquals(
            GPURuntimeEffectUniformValue.Float3(3f, 4f, 5f),
            mapping.descriptor.uniforms.getValue("triple"),
        )
        assertEquals(
            GPURuntimeEffectUniformValue.Float4(0.25f, 0.5f, 0.75f, 1f),
            mapping.descriptor.uniforms.getValue("color"),
        )
        assertEquals(
            GPURuntimeEffectUniformValue.Int1(7),
            mapping.descriptor.uniforms.getValue("count"),
        )
        assertEquals(
            listOf(1f, 0f, 3f, 0f, 1f, 5f, 0f, 0f, 1f),
            assertIs<GPURuntimeEffectUniformValue.Matrix3x3>(
                mapping.descriptor.uniforms.getValue("matrix3"),
            ).values,
        )
        assertEquals(
            (0 until 16).map(Int::toFloat),
            assertIs<GPURuntimeEffectUniformValue.Matrix4x4>(
                mapping.descriptor.uniforms.getValue("matrix4"),
            ).values,
        )
        assertEquals(
            listOf("shader", "filter", "blender"),
            mapping.descriptor.childDescriptors.keys.toList(),
        )
        assertIs<GPURuntimeEffectChildDescriptor.Shader>(
            mapping.descriptor.childDescriptors.getValue("shader"),
        )
        assertIs<GPURuntimeEffectChildDescriptor.ColorFilter>(
            mapping.descriptor.childDescriptors.getValue("filter"),
        )
        assertEquals(
            listOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
            assertIs<GPUPreparedColorFilterChildDescriptor.Matrix>(
                assertIs<GPURuntimeEffectChildDescriptor.ColorFilter>(
                    mapping.descriptor.childDescriptors.getValue("filter"),
                ).filter,
            ).values,
        )
        assertEquals(
            GPUPreparedBlenderChildDescriptor.Mode(GPUBlendMode.PLUS),
            assertIs<GPURuntimeEffectChildDescriptor.Blender>(
                mapping.descriptor.childDescriptors.getValue("blender"),
            ).blender,
        )
    }

    @Test
    fun `mesh program child roles are part of descriptor identity`() {
        val shader = program(
            MeshChildren.of("child" to ShaderChild(Shader.SolidColor(Color.RED))),
        )
        val filter = program(
            MeshChildren.of("child" to ColorFilterChild(identityMatrixFilter())),
        )

        assertNotEquals(
            shader.toPreparedMeshProgramMapping(1f).descriptor,
            filter.toPreparedMeshProgramMapping(1f).descriptor,
        )
    }

    @Test
    fun `duplicate names refuse before any child mapping`() {
        val result = program(
            MeshChildren(
                listOf(
                    MeshChildren.Entry("same", ColorFilterChild(ColorFilter.Luma)),
                    MeshChildren.Entry("same", ShaderChild(Shader.SolidColor(Color.RED))),
                ),
            ),
        ).toPreparedMeshProgramMappingResult(1f)

        val refused = assertIs<GPUPreparedMeshProgramMappingResult.Refused>(result)
        assertEquals(GPUPreparedVerticesRefusalCodes.MeshProgramChild, refused.code)
        assertEquals("duplicate_name", refused.facts["reason"])
        assertEquals("same", refused.facts["childName"])
    }

    @Test
    fun `closed color filter child set maps matrix blend compose and registered runtime`() {
        val nestedRuntime = ColorFilter.RuntimeEffect(
            effect = effect("filter.registered"),
            uniforms = UniformBlock { float1("amount", 0.75f) },
            children = linkedMapOf("input" to identityMatrixFilter()),
        )
        val children = MeshChildren.of(
            "matrix" to ColorFilterChild(identityMatrixFilter()),
            "blend" to ColorFilterChild(ColorFilter.Blend(Color.BLUE, BlendMode.MULTIPLY)),
            "compose" to ColorFilterChild(
                ColorFilter.Compose(
                    outer = ColorFilter.Blend(Color.RED, BlendMode.PLUS),
                    inner = nestedRuntime,
                ),
            ),
        )

        val descriptor = program(children).toPreparedMeshProgramMapping(1f).descriptor

        assertIs<GPUPreparedColorFilterChildDescriptor.Matrix>(filter(descriptor, "matrix"))
        assertEquals(
            GPUPreparedColorFilterChildDescriptor.Blend(
                rgba = listOf(0f, 0f, 1f, 1f),
                mode = GPUBlendMode.MULTIPLY,
            ),
            filter(descriptor, "blend"),
        )
        val compose = assertIs<GPUPreparedColorFilterChildDescriptor.Compose>(
            filter(descriptor, "compose"),
        )
        assertIs<GPUPreparedColorFilterChildDescriptor.Blend>(compose.outer)
        val registered = assertIs<GPUPreparedColorFilterChildDescriptor.RegisteredRuntimeEffect>(
            compose.inner,
        ).effect
        assertEquals("filter.registered", registered.effectId)
        assertEquals(
            GPURuntimeEffectUniformValue.Float1(0.75f),
            registered.uniforms.getValue("amount"),
        )
        assertEquals(listOf("input"), registered.childDescriptors.keys.toList())
        assertIs<GPURuntimeEffectChildDescriptor.ColorFilter>(
            registered.childDescriptors.getValue("input"),
        )
    }

    @Test
    fun `all color filter variants outside the closed set refuse canonically`() {
        @OptIn(ExperimentalUnsignedTypes::class)
        val unsupported = listOf<ColorFilter>(
            ColorFilter.Table(UByteArray(256) { it.toUByte() }),
            ColorFilter.Lighting(Color.WHITE, Color.BLACK),
            ColorFilter.SRGBToLinear,
            ColorFilter.LinearToSRGB,
            ColorFilter.HSLAMatrix(FloatArray(20)),
            ColorFilter.Lerp(0.5f, identityMatrixFilter(), identityMatrixFilter()),
            ColorFilter.HighContrast,
            ColorFilter.Luma,
            ColorFilter.Overdraw,
        )

        unsupported.forEach { colorFilter ->
            val refused = assertIs<GPUPreparedMeshProgramMappingResult.Refused>(
                program(
                    MeshChildren.of("child" to ColorFilterChild(colorFilter)),
                ).toPreparedMeshProgramMappingResult(1f),
                colorFilter.toString(),
            )
            assertEquals(GPUPreparedVerticesRefusalCodes.MeshProgramChild, refused.code)
            assertEquals("unsupported_color_filter", refused.facts["reason"])
        }
    }

    @Test
    fun `mode blenders map and arithmetic refuses without an existing CPU WGSL authority`() {
        BlendMode.entries.forEach { mode ->
            val mapping = program(
                MeshChildren.of("blender" to BlenderChild(Blender.Mode(mode))),
            ).toPreparedMeshProgramMapping(1f)
            assertEquals(
                mode.toGpuBlendFacts().mode,
                assertIs<GPUPreparedBlenderChildDescriptor.Mode>(
                    assertIs<GPURuntimeEffectChildDescriptor.Blender>(
                        mapping.descriptor.childDescriptors.getValue("blender"),
                    ).blender,
                ).mode,
            )
        }

        val refused = assertIs<GPUPreparedMeshProgramMappingResult.Refused>(
            program(
                MeshChildren.of(
                    "blender" to BlenderChild(Blender.Arithmetic(0f, 1f, 1f, 0f)),
                ),
            ).toPreparedMeshProgramMappingResult(1f),
        )
        assertEquals(GPUPreparedVerticesRefusalCodes.MeshProgramChild, refused.code)
        assertEquals("arithmetic_blender_unregistered", refused.facts["reason"])
    }

    @Test
    fun `unsupported shader child becomes canonical mesh child refusal`() {
        val refused = assertIs<GPUPreparedMeshProgramMappingResult.Refused>(
            program(
                MeshChildren.of(
                    "shader" to ShaderChild(Shader.PerlinNoise(0f, 1f, 1, 7, null)),
                ),
            ).toPreparedMeshProgramMappingResult(1f),
        )

        assertEquals(GPUPreparedVerticesRefusalCodes.MeshProgramChild, refused.code)
        assertEquals("unsupported_shader", refused.facts["reason"])
    }

    @Test
    fun `unsupported shader descendant in a composite refuses the whole mesh child`() {
        val refused = assertIs<GPUPreparedMeshProgramMappingResult.Refused>(
            program(
                MeshChildren.of(
                    "shader" to ShaderChild(
                        Shader.Blend(
                            mode = BlendMode.SRC_OVER,
                            dst = Shader.SolidColor(Color.RED),
                            src = Shader.PerlinNoise(0f, 1f, 1, 7, null),
                        ),
                    ),
                ),
            ).toPreparedMeshProgramMappingResult(1f),
        )

        assertEquals(GPUPreparedVerticesRefusalCodes.MeshProgramChild, refused.code)
        assertEquals("unsupported_shader", refused.facts["reason"])
    }

    @Test
    fun `blank effect invalid alpha and descriptor depth refuse deterministically`() {
        val blank = MeshProgram(effect("   ")).toPreparedMeshProgramMappingResult(1f)
        val blankRefusal = assertIs<GPUPreparedMeshProgramMappingResult.Refused>(blank)
        assertEquals(GPUPreparedVerticesRefusalCodes.MeshProgramUnregistered, blankRefusal.code)
        assertEquals("blank_effect_id", blankRefusal.facts["reason"])

        listOf(Float.NaN, Float.NEGATIVE_INFINITY, -0.01f, 1.01f).forEach { alpha ->
            val refused = assertIs<GPUPreparedMeshProgramMappingResult.Refused>(
                program().toPreparedMeshProgramMappingResult(alpha),
            )
            assertEquals(GPUPreparedVerticesRefusalCodes.Material, refused.code)
            assertEquals("invalid_paint_alpha", refused.facts["reason"])
        }

        var deep: ColorFilter = identityMatrixFilter()
        repeat(PREPARED_MAPPING_MAX_ACTIVE_GRAPH_DEPTH - 2) {
            deep = ColorFilter.Compose(identityMatrixFilter(), deep)
        }
        assertIs<GPUPreparedMeshProgramMappingResult.Ready>(
            program(MeshChildren.of("boundary" to ColorFilterChild(deep)))
                .toPreparedMeshProgramMappingResult(1f),
        )
        deep = ColorFilter.Compose(identityMatrixFilter(), deep)
        val depth = assertIs<GPUPreparedMeshProgramMappingResult.Refused>(
            program(MeshChildren.of("deep" to ColorFilterChild(deep)))
                .toPreparedMeshProgramMappingResult(1f),
        )
        assertEquals(GPUPreparedVerticesRefusalCodes.MeshProgramChild, depth.code)
        assertEquals("child_graph_depth", depth.facts["reason"])
    }

    @Test
    fun `shared compose dag near depth forty maps once and preserves shared prepared value`() {
        var shared: ColorFilter = identityMatrixFilter()
        repeat(38) {
            shared = ColorFilter.Compose(shared, shared)
        }

        val mapping = assertIs<GPUPreparedMeshProgramMappingResult.Ready>(
            program(MeshChildren.of("dag" to ColorFilterChild(shared)))
                .toPreparedMeshProgramMappingResult(1f),
        ).mapping

        var prepared = filter(mapping.descriptor, "dag")
        repeat(38) {
            val compose = assertIs<GPUPreparedColorFilterChildDescriptor.Compose>(prepared)
            assertSame(compose.outer, compose.inner)
            prepared = compose.outer
        }
        assertIs<GPUPreparedColorFilterChildDescriptor.Matrix>(prepared)
    }

    @Test
    fun `shallow memoized child cannot bypass the depth limit when reused deeper`() {
        val shared = identityMatrixFilter()
        var deep: ColorFilter = shared
        repeat(63) {
            deep = ColorFilter.Compose(identityMatrixFilter(), deep)
        }
        val program = program(
            MeshChildren(
                listOf(
                    MeshChildren.Entry("shallow", ColorFilterChild(shared)),
                    MeshChildren.Entry("deep", ColorFilterChild(deep)),
                ),
            ),
        )

        val refused = assertIs<GPUPreparedMeshProgramMappingResult.Refused>(
            program.toPreparedMeshProgramMappingResult(1f),
        )

        assertEquals(GPUPreparedVerticesRefusalCodes.MeshProgramChild, refused.code)
        assertEquals("child_graph_depth", refused.facts["reason"])
        assertEquals("deep", refused.facts["childName"])
    }

    @Test
    fun `hostile compose chain refuses at the active depth guard`() {
        var hostile: ColorFilter = identityMatrixFilter()
        repeat(65_536) {
            hostile = ColorFilter.Compose(identityMatrixFilter(), hostile)
        }

        val refused = assertIs<GPUPreparedMeshProgramMappingResult.Refused>(
            program(MeshChildren.of("hostile" to ColorFilterChild(hostile)))
                .toPreparedMeshProgramMappingResult(1f),
        )

        assertEquals(GPUPreparedVerticesRefusalCodes.MeshProgramChild, refused.code)
        assertEquals("child_graph_depth", refused.facts["reason"])
        assertEquals("hostile", refused.facts["childName"])
    }

    @Test
    fun `draw mesh override or paint blend is retained separately from material identity`() {
        val base = drawMesh(blendMode = null, paintBlendMode = BlendMode.DST_OVER)
        val overridden = drawMesh(blendMode = BlendMode.PLUS, paintBlendMode = BlendMode.DST_OVER)

        val baseMapping = assertIs<GPUPreparedMeshProgramMappingResult.Ready>(
            base.toPreparedMeshProgramMappingResult(),
        ).mapping
        val overrideMapping = assertIs<GPUPreparedMeshProgramMappingResult.Ready>(
            overridden.toPreparedMeshProgramMappingResult(),
        ).mapping

        assertEquals(GPUBlendMode.DST_OVER, baseMapping.finalTargetBlendMode)
        assertEquals(GPUBlendMode.PLUS, overrideMapping.finalTargetBlendMode)
        assertEquals(baseMapping.descriptor, overrideMapping.descriptor)
    }

    private fun program(children: MeshChildren = MeshChildren.EMPTY): MeshProgram =
        MeshProgram(effect("mesh.program"), children = children)

    private fun effect(
        id: String,
        children: List<ChildSlot> = emptyList(),
    ): RuntimeEffect = RuntimeEffect(
        id = id,
        module = ShaderModule.fromSource("registered-only"),
        uniformLayout = UniformLayout(emptyList()),
        children = children,
    )

    private fun identityMatrixFilter(): ColorFilter.Matrix =
        ColorFilter.Matrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )

    private fun filter(
        descriptor: GPUMaterialDescriptor.RuntimeEffect,
        name: String,
    ): GPUPreparedColorFilterChildDescriptor =
        assertIs<GPURuntimeEffectChildDescriptor.ColorFilter>(
            descriptor.childDescriptors.getValue(name),
        ).filter

    private fun drawMesh(
        blendMode: BlendMode?,
        paintBlendMode: BlendMode,
    ): DisplayOp.DrawMesh = DisplayOp.DrawMesh(
        mesh = Mesh(
            vertices = Vertices(
                mode = VertexMode.TRIANGLES,
                positions = listOf(Point(0f, 0f), Point(1f, 0f), Point(0f, 1f)),
            ),
            program = program(),
            bounds = Rect.fromLTRB(0f, 0f, 1f, 1f),
        ),
        paint = Paint(color = Color.WHITE, blendMode = paintBlendMode),
        blendMode = blendMode,
        transform = Matrix3x3F32.Identity,
        clip = ClipStack.WideOpen,
    )
}
