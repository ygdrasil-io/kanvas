package org.graphiks.kanvas.surface.gpu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUBounds
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectResolution
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedRuntimeEffectResolver
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.MeshProgram
import org.graphiks.kanvas.paint.MeshChildren
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Blender
import org.graphiks.kanvas.paint.BlenderChild
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.ShaderModule
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.pipeline.UniformLayout
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Mesh
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.VertexMode
import org.graphiks.kanvas.types.Vertices
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.canvas.ClipStackOp

class GPUPreparedVerticesLowererTest {
    @Test
    fun `draw mesh resolves final blend exactly once`() {
        val operation = meshOperation(
            paintBlend = BlendMode.MULTIPLY,
            overrideBlend = BlendMode.PLUS,
            program = registeredMeshProgram(),
        )

        val draw = lower(operation).ready().draw

        assertEquals(GPUBlendMode.PLUS, draw.finalBlend.mode)
        assertEquals(1, draw.paintAlphaApplicationCount)
        assertEquals(GPUPreparedVerticesOperationKind.DrawMesh, draw.operationKind)
    }

    @Test
    fun `unregistered mesh program is terminal before native work`() {
        val result = lower(meshOperation(program = MeshProgram(effect("not.registered"))))

        val refusal = assertIs<GPUPreparedVerticesLowering.Refused>(result)
        assertEquals(GPUPreparedVerticesRefusalCodes.MeshProgramUnregistered, refusal.code)
        assertEquals(7, refusal.operationIndex)
        assertEquals("mesh-program", refusal.facts["stage"])
    }

    @Test
    fun `mesh without program follows public vertices normalization with its resolved blend`() {
        val operation = meshOperation(
            paintBlend = BlendMode.MULTIPLY,
            overrideBlend = BlendMode.PLUS,
            program = null,
        )

        val draw = lower(operation).ready().draw

        assertEquals(GPUPreparedVerticesOperationKind.DrawVertices, draw.operationKind)
        assertEquals(GPUBlendMode.PLUS, draw.finalBlend.mode)
        assertEquals("drawMesh:no-program", draw.provenance)
    }

    @Test
    fun `vertices snapshot geometry transform clip and paint before publication`() {
        val positions = mutableListOf(Point(0f, 0f), Point(2f, 0f), Point(0f, 2f))
        val transform = Matrix33.translate(3f, 4f)
        val operation = DisplayOp.DrawVertices(
            Vertices(VertexMode.TRIANGLES, positions),
            Paint.fill(Color.RED),
            transform,
            ClipStack.WideOpen,
        )

        val draw = lower(operation).ready().draw
        positions[0] = Point(99f, 99f)

        assertEquals(0f, draw.artifact.vertexBytesForUpload().let { java.nio.ByteBuffer.wrap(it).order(java.nio.ByteOrder.LITTLE_ENDIAN).float })
        assertEquals(3f, draw.transform.transX)
        assertEquals("drawVertices", draw.provenance)
    }

    @Test
    fun `every packer refusal retains canonical code operation index and deterministic facts`() {
        val operation = DisplayOp.DrawVertices(
            Vertices(VertexMode.TRIANGLES, listOf(Point(Float.NaN, 0f), Point(1f, 0f), Point(0f, 1f))),
            Paint.fill(Color.RED), Matrix33.identity(), ClipStack.WideOpen,
        )

        val refusal = lower(operation).refused()

        assertEquals(GPUPreparedVerticesRefusalCodes.NonFinite, refusal.code)
        assertEquals(7, refusal.operationIndex)
        assertEquals("geometry", refusal.facts["stage"])
        assertEquals("position", refusal.facts["attribute"])
    }

    @Test
    fun `public vertex modes and attribute combinations preserve packer layout facts`() {
        val attributes = listOf(
            null to null,
            listOf(Color.RED, Color.GREEN, Color.BLUE) to null,
            null to listOf(Point(0f, 0f), Point(1f, 0f), Point(0f, 1f)),
            listOf(Color.RED, Color.GREEN, Color.BLUE) to listOf(Point(0f, 0f), Point(1f, 0f), Point(0f, 1f)),
        )
        attributes.forEachIndexed { index, (colors, texCoords) ->
            val draw = lower(DisplayOp.DrawVertices(
                Vertices(VertexMode.TRIANGLES, vertices().positions, texCoords, colors),
                Paint.fill(Color.WHITE), Matrix33.identity(), ClipStack.WideOpen,
            )).ready().draw
            assertEquals(listOf(8, 12, 16, 20)[index], draw.artifact.layout.strideBytes)
            assertEquals(colors != null, draw.primitiveColorPresent)
        }

        val fan = lower(DisplayOp.DrawVertices(
            Vertices(VertexMode.TRIANGLE_FAN, listOf(
                Point(0f, 0f), Point(2f, 0f), Point(2f, 2f), Point(0f, 2f),
            )), Paint.fill(Color.WHITE), Matrix33.identity(), ClipStack.WideOpen,
        )).ready().draw
        assertEquals(6, fan.artifact.indexCount)
    }

    @Test
    fun `transform mesh bounds and mesh child refusals retain their canonical boundaries`() {
        val perspective = lower(DisplayOp.DrawVertices(
            vertices(), Paint.fill(Color.RED),
            Matrix33.makeAll(1f, 0f, 0f, 0f, 1f, 0f, 0.1f, 0f, 1f), ClipStack.WideOpen,
        )).refused()
        assertEquals(GPUPreparedVerticesRefusalCodes.Transform, perspective.code)
        assertEquals("perspective", perspective.facts["reason"])

        val invalidBounds = lower(meshOperation(program = registeredMeshProgram()).copy(
            mesh = Mesh(vertices(), registeredMeshProgram(), Rect(Float.NaN, 0f, 1f, 1f)),
        )).refused()
        assertEquals(GPUPreparedVerticesRefusalCodes.MeshBounds, invalidBounds.code)
        assertEquals("mesh-bounds", invalidBounds.facts["stage"])

        val child = MeshProgram(
            effect("runtime.simple_rt"),
            uniforms = UniformBlock { float4("gColor", 1f, 0f, 0f, 1f) },
            children = MeshChildren.of("blend" to BlenderChild(Blender.Arithmetic(0f, 0f, 0f, 0f))),
        )
        val childRefusal = lower(meshOperation(program = child)).refused()
        assertEquals(GPUPreparedVerticesRefusalCodes.MeshProgramChild, childRefusal.code)
        assertEquals("mesh-program", childRefusal.facts["stage"])
    }

    @Test
    fun `ready draw retains conservative transformed and scissor clipped bounds with clip identity`() {
        val clip = ClipStack.DeviceRect(Rect.fromLTRB(4f, 6f, 12f, 14f), antiAlias = false)
        val draw = lower(DisplayOp.DrawVertices(
            vertices(), Paint.fill(Color.RED), Matrix33.translate(5f, 7f), clip,
        )).ready().draw

        assertEquals(GPUBounds(5f, 7f, 7f, 9f), draw.deviceBounds)
        assertEquals(GPUBounds(5f, 7f, 7f, 9f), draw.clippedBounds)
        assertEquals("prepared-vertices-clip:device-rect", draw.clipSnapshot.identity)
        assertIs<GPUClipCoveragePlan.Scissor>(draw.clipSnapshot.coveragePlan)
    }

    @Test
    fun `prepared refusal coverage closes every canonical code as executable or reserved`() {
        assertEquals(
            GPUPreparedVerticesRefusalCodes.ALL,
            GPUPreparedVerticesRefusalCoverage.classifications.keys,
        )
        GPUPreparedVerticesRefusalCoverage.classifications.values.forEach { classification ->
            assertTrue(classification.authority.isNotBlank())
            assertTrue(classification.reason.isNotBlank())
        }
    }

    @Test
    fun `compiler refusal mapping is closed and unknown remains generic material`() {
        assertEquals(
            GPUPreparedVerticesRefusalCodes.MeshProgramAbi,
            meshMaterialCode("unsupported.material.runtime_effect.uniform_payload"),
        )
        assertEquals(
            GPUPreparedVerticesRefusalCodes.MeshProgramChild,
            meshMaterialCode("unsupported.material.runtime_effect.child_role"),
        )
        assertEquals(
            GPUPreparedVerticesRefusalCodes.MeshProgramWgslValidation,
            meshMaterialCode("unsupported.material.wgsl_validation"),
        )
        assertEquals(
            GPUPreparedVerticesRefusalCodes.MeshProgramResource,
            meshMaterialCode("unsupported.material.image_resource"),
        )
        assertEquals(
            GPUPreparedVerticesRefusalCodes.Material,
            meshMaterialCode("unsupported.material.runtime_effect.unknown_future"),
        )
    }

    @Test
    fun `partial alpha vertex colors retain primitive src-over plan independently of material paint alpha`() {
        val draw = lower(DisplayOp.DrawVertices(
            Vertices(
                VertexMode.TRIANGLES,
                vertices().positions,
                colors = listOf(Color.fromRGBA(1f, 0f, 0f, 0.5f), Color.GREEN, Color.BLUE),
            ),
            Paint.fill(Color.fromRGBA(1f, 1f, 1f, 0.5f)), Matrix33.identity(), ClipStack.WideOpen,
        )).ready().draw

        assertEquals(1f, draw.material.paintAlpha)
        assertEquals(
            128f / 255f,
            java.nio.ByteBuffer.wrap(draw.material.uniformBytes.map(Int::toByte).toByteArray())
                .order(java.nio.ByteOrder.LITTLE_ENDIAN).getFloat(12),
        )
        assertEquals(GPUBlendMode.SRC_OVER, draw.primitiveBlendPlan?.plan?.mode)
        assertEquals(1, draw.paintAlphaApplicationCount)
    }

    @Test
    fun `hostile paint snapshot becomes one typed material refusal`() {
        val result = lower(DisplayOp.DrawVertices(
            vertices(), Paint.fill(Color.RED).copy(shader = hostileShader()),
            Matrix33.identity(), ClipStack.WideOpen,
        ))

        val refusal = result.refused()
        assertEquals(GPUPreparedVerticesRefusalCodes.Material, refusal.code)
        assertEquals(7, refusal.operationIndex)
        assertEquals("paint-snapshot", refusal.facts["stage"])
        assertEquals("PreparedTextPaintSnapshotter", refusal.facts["authority"])
        assertEquals("snapshot_exception", refusal.facts["reason"])
    }

    @Test
    fun `mesh program lowering does not visit unused hostile paint shader`() {
        val draw = lower(meshOperation(program = registeredMeshProgram()).copy(
            paint = Paint.fill(Color.RED).copy(shader = hostileShader()),
        )).ready().draw

        assertEquals(GPUPreparedVerticesOperationKind.DrawMesh, draw.operationKind)
    }

    @Test
    fun `mesh runtime resolver maps each executable unavailability authority to one canonical code`() {
        val cases = listOf(
            GPUPreparedRuntimeEffectResolution.DescriptorUnavailable("missing") to
                GPUPreparedVerticesRefusalCodes.MeshProgramUnregistered,
            GPUPreparedRuntimeEffectResolution.ProgramUnavailable(
                "cpu", GPUPreparedRuntimeEffectResolution.ProgramUnavailableReason.CpuUnavailable,
            ) to GPUPreparedVerticesRefusalCodes.MeshProgramCpuUnavailable,
            GPUPreparedRuntimeEffectResolution.ProgramUnavailable(
                "wgsl", GPUPreparedRuntimeEffectResolution.ProgramUnavailableReason.WgslUnavailable,
            ) to GPUPreparedVerticesRefusalCodes.MeshProgramWgslUnavailable,
            GPUPreparedRuntimeEffectResolution.ProgramUnavailable(
                "invalid", GPUPreparedRuntimeEffectResolution.ProgramUnavailableReason.WgslValidation,
            ) to GPUPreparedVerticesRefusalCodes.MeshProgramWgslValidation,
        )

        cases.forEach { (resolution, expected) ->
            val result = lower(
                meshOperation(program = registeredMeshProgram()),
                GPUPreparedRuntimeEffectResolver { _, _ -> resolution },
            ).refused()
            assertEquals(expected, result.code)
            assertEquals("mesh-program", result.facts["stage"])
            assertEquals("runtime_effect_unavailable", result.facts["reason"])
        }
    }

    @Test
    fun `mesh snapshots vertices and clips before resolver can mutate caller objects`() {
        val positions = mutableListOf(Point(0f, 0f), Point(2f, 0f), Point(0f, 2f))
        val path = Path().addRect(Rect.fromLTRB(0f, 0f, 4f, 4f))
        val operation = meshOperation(program = registeredMeshProgram()).copy(
            mesh = Mesh(
                Vertices(VertexMode.TRIANGLES, positions),
                registeredMeshProgram(),
                Rect.fromLTRB(0f, 0f, 2f, 2f),
            ),
            clip = ClipStack.Complex(listOf(ClipStackOp.PathOp(path, ClipOp.INTERSECT))),
        )
        val resolver = GPUPreparedRuntimeEffectResolver { effectId, version ->
            positions[0] = Point(99f, 99f)
            path.addRect(Rect.fromLTRB(50f, 50f, 60f, 60f))
            org.graphiks.kanvas.gpu.renderer.runtimeeffects.KanvasPreparedRuntimeEffectResolver()
                .resolve(effectId, version)
        }

        val draw = lower(operation, resolver).ready().draw

        assertEquals(0f, java.nio.ByteBuffer.wrap(draw.artifact.vertexBytesForUpload())
            .order(java.nio.ByteOrder.LITTLE_ENDIAN).float)
        val snapshottedPath = ((draw.clip as ClipStack.Complex).ops.single() as ClipStackOp.PathOp).path
        assertEquals(Rect.fromLTRB(0f, 0f, 4f, 4f), snapshottedPath.computeBounds())
    }

    private fun lower(operation: DisplayOp): GPUPreparedVerticesLowering =
        GPUPreparedVerticesLowerer.lower(operation, 7, target(), capabilities())

    private fun lower(
        operation: DisplayOp,
        runtimeEffectResolver: GPUPreparedRuntimeEffectResolver,
    ): GPUPreparedVerticesLowering =
        GPUPreparedVerticesLowerer.lower(operation, 7, target(), capabilities(), runtimeEffectResolver)

    private fun meshOperation(
        paintBlend: BlendMode = BlendMode.SRC_OVER,
        overrideBlend: BlendMode? = null,
        program: MeshProgram? = null,
    ): DisplayOp.DrawMesh = DisplayOp.DrawMesh(
        mesh = Mesh(vertices(), program, Rect.fromLTRB(0f, 0f, 2f, 2f)),
        paint = Paint.fill(Color.RED).copy(blendMode = paintBlend),
        blendMode = overrideBlend,
        transform = Matrix33.identity(),
        clip = ClipStack.WideOpen,
    )

    private fun vertices(): Vertices = Vertices(
        VertexMode.TRIANGLES,
        listOf(Point(0f, 0f), Point(2f, 0f), Point(0f, 2f)),
    )

    private fun registeredMeshProgram(): MeshProgram = MeshProgram(
        effect("runtime.simple_rt"),
        uniforms = UniformBlock { float4("gColor", 1f, 0f, 0f, 1f) },
    )

    private fun effect(id: String): RuntimeEffect = RuntimeEffect(
        id = id,
        module = ShaderModule.fromSource("fixture"),
        uniformLayout = UniformLayout(emptyList()),
        children = emptyList(),
    )

    private fun hostileShader(): Shader {
        var shader: Shader = Shader.SolidColor(Color.RED)
        repeat(66) { shader = Shader.Blend(BlendMode.SRC_OVER, shader, Shader.SolidColor(Color.BLUE)) }
        return shader
    }

    private fun target() = GPUTargetFacts(64, 64, "rgba8unorm-srgb")

    private fun capabilities(): GPUCapabilities = GPUCapabilities(
        implementation = GPUImplementationIdentity("wgpu4k", "test", "adapter", "device"),
        facts = emptyList(), snapshotId = "fp06-vertices",
        limits = GPULimits(8192, 256, 256, maxBufferSize = 1L shl 30, maxDynamicUniformBuffersPerPipelineLayout = 1),
    )
}

private fun GPUPreparedVerticesLowering.Ready.ready(): GPUPreparedVerticesLowering.Ready = this
private fun GPUPreparedVerticesLowering.ready(): GPUPreparedVerticesLowering.Ready =
    assertIs<GPUPreparedVerticesLowering.Ready>(this)
private fun GPUPreparedVerticesLowering.refused(): GPUPreparedVerticesLowering.Refused =
    assertIs<GPUPreparedVerticesLowering.Refused>(this)
