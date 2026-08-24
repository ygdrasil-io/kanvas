package org.graphiks.kanvas.surface.gpu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertFailsWith
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
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramCompiler
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceAlphaClassification
import org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes
import org.graphiks.kanvas.gpu.renderer.vertices.GPUVertexLayoutPlan
import org.graphiks.kanvas.gpu.renderer.vertices.GPUVertexMode
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.KanvasPreparedRuntimeEffectResolver
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
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.kanvas.types.Mesh
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
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
        val positions = mutableListOf(Point2F32(0f, 0f), Point2F32(2f, 0f), Point2F32(0f, 2f))
        val transform = Matrix3x3F32.translation(3f, 4f)
        val operation = DisplayOp.DrawVertices(
            Vertices(VertexMode.TRIANGLES, positions),
            Paint.fill(Color.RED),
            transform,
            ClipStack.WideOpen,
        )

        val draw = lower(operation).ready().draw
        positions[0] = Point2F32(99f, 99f)

        assertEquals(0f, draw.artifact.vertexBytesForUpload().let { java.nio.ByteBuffer.wrap(it).order(java.nio.ByteOrder.LITTLE_ENDIAN).float })
        assertEquals(3f, draw.transform.tx)
        assertEquals("drawVertices", draw.provenance)
    }

    @Test
    fun `every packer refusal retains canonical code operation index and deterministic facts`() {
        val operation = DisplayOp.DrawVertices(
            Vertices(VertexMode.TRIANGLES, listOf(Point2F32(Float.NaN, 0f), Point2F32(1f, 0f), Point2F32(0f, 1f))),
            Paint.fill(Color.RED), Matrix3x3F32.Identity, ClipStack.WideOpen,
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
            null to listOf(Point2F32(0f, 0f), Point2F32(1f, 0f), Point2F32(0f, 1f)),
            listOf(Color.RED, Color.GREEN, Color.BLUE) to listOf(Point2F32(0f, 0f), Point2F32(1f, 0f), Point2F32(0f, 1f)),
        )
        attributes.forEachIndexed { index, (colors, texCoords) ->
            val draw = lower(DisplayOp.DrawVertices(
                Vertices(VertexMode.TRIANGLES, vertices().positions, texCoords, colors),
                Paint.fill(Color.WHITE), Matrix3x3F32.Identity, ClipStack.WideOpen,
            )).ready().draw
            assertEquals(listOf(8, 12, 16, 20)[index], draw.artifact.layout.strideBytes)
            assertEquals(colors != null, draw.primitiveColorPresent)
        }

        val fan = lower(DisplayOp.DrawVertices(
            Vertices(VertexMode.TRIANGLE_FAN, listOf(
                Point2F32(0f, 0f), Point2F32(2f, 0f), Point2F32(2f, 2f), Point2F32(0f, 2f),
            )), Paint.fill(Color.WHITE), Matrix3x3F32.Identity, ClipStack.WideOpen,
        )).ready().draw
        assertEquals(6, fan.artifact.indexCount)

        val strip = lower(DisplayOp.DrawVertices(
            Vertices(VertexMode.TRIANGLE_STRIP, vertices().positions, indices = listOf(0, 1, 2)),
            Paint.fill(Color.WHITE), Matrix3x3F32.Identity, ClipStack.WideOpen,
        )).ready().draw
        assertEquals(3, strip.artifact.indexCount)
    }

    @Test
    fun `closed public topology layout and index product preserves exact prepared artifacts`() {
        val position = GPUVertexLayoutPlan(
            attributes = listOf("position"), strideBytes = 8,
            offsets = mapOf("position" to 0), shaderLocations = mapOf("position" to 0),
        )
        val positionUv = GPUVertexLayoutPlan(
            attributes = listOf("position", "texcoord"), strideBytes = 16,
            offsets = mapOf("position" to 0, "texcoord" to 8),
            shaderLocations = mapOf("position" to 0, "texcoord" to 2),
        )
        val positionColor = GPUVertexLayoutPlan(
            attributes = listOf("position", "color"), strideBytes = 12,
            offsets = mapOf("position" to 0, "color" to 8),
            shaderLocations = mapOf("position" to 0, "color" to 1),
        )
        val positionColorUv = GPUVertexLayoutPlan(
            attributes = listOf("position", "color", "texcoord"), strideBytes = 20,
            offsets = mapOf("position" to 0, "color" to 8, "texcoord" to 12),
            shaderLocations = mapOf("position" to 0, "color" to 1, "texcoord" to 2),
        )
        val expectedAttributeFormats = mapOf(
            position to listOf("float32x2"),
            positionUv to listOf("float32x2", "float32x2"),
            positionColor to listOf("float32x2", "unorm8x4"),
            positionColorUv to listOf("float32x2", "unorm8x4", "float32x2"),
        )
        val noIndices: ByteArray? = null
        val triangleIndices = byteArrayOf(2, 0, 0, 0, 1, 0)
        val stripIndices = byteArrayOf(3, 0, 1, 0, 2, 0, 0, 0)
        val implicitFanIndices = byteArrayOf(0, 0, 1, 0, 2, 0, 0, 0, 2, 0, 3, 0)
        val explicitFanIndices = byteArrayOf(2, 0, 3, 0, 0, 0, 2, 0, 0, 0, 1, 0)
        val cases = listOf(
            ProductCase("triangles-position-unindexed", VertexMode.TRIANGLES, position, false, 3, GPUVertexMode.Triangles, null, null, noIndices),
            ProductCase("triangles-position-indexed", VertexMode.TRIANGLES, position, true, 3, GPUVertexMode.Triangles, 3, "uint16", triangleIndices),
            ProductCase("triangles-uv-unindexed", VertexMode.TRIANGLES, positionUv, false, 3, GPUVertexMode.Triangles, null, null, noIndices),
            ProductCase("triangles-uv-indexed", VertexMode.TRIANGLES, positionUv, true, 3, GPUVertexMode.Triangles, 3, "uint16", triangleIndices),
            ProductCase("triangles-color-unindexed", VertexMode.TRIANGLES, positionColor, false, 3, GPUVertexMode.Triangles, null, null, noIndices),
            ProductCase("triangles-color-indexed", VertexMode.TRIANGLES, positionColor, true, 3, GPUVertexMode.Triangles, 3, "uint16", triangleIndices),
            ProductCase("triangles-color-uv-unindexed", VertexMode.TRIANGLES, positionColorUv, false, 3, GPUVertexMode.Triangles, null, null, noIndices),
            ProductCase("triangles-color-uv-indexed", VertexMode.TRIANGLES, positionColorUv, true, 3, GPUVertexMode.Triangles, 3, "uint16", triangleIndices),
            ProductCase("strip-position-unindexed", VertexMode.TRIANGLE_STRIP, position, false, 4, GPUVertexMode.TriangleStrip, null, null, noIndices),
            ProductCase("strip-position-indexed", VertexMode.TRIANGLE_STRIP, position, true, 4, GPUVertexMode.TriangleStrip, 4, "uint16", stripIndices),
            ProductCase("strip-uv-unindexed", VertexMode.TRIANGLE_STRIP, positionUv, false, 4, GPUVertexMode.TriangleStrip, null, null, noIndices),
            ProductCase("strip-uv-indexed", VertexMode.TRIANGLE_STRIP, positionUv, true, 4, GPUVertexMode.TriangleStrip, 4, "uint16", stripIndices),
            ProductCase("strip-color-unindexed", VertexMode.TRIANGLE_STRIP, positionColor, false, 4, GPUVertexMode.TriangleStrip, null, null, noIndices),
            ProductCase("strip-color-indexed", VertexMode.TRIANGLE_STRIP, positionColor, true, 4, GPUVertexMode.TriangleStrip, 4, "uint16", stripIndices),
            ProductCase("strip-color-uv-unindexed", VertexMode.TRIANGLE_STRIP, positionColorUv, false, 4, GPUVertexMode.TriangleStrip, null, null, noIndices),
            ProductCase("strip-color-uv-indexed", VertexMode.TRIANGLE_STRIP, positionColorUv, true, 4, GPUVertexMode.TriangleStrip, 4, "uint16", stripIndices),
            ProductCase("fan-position-unindexed", VertexMode.TRIANGLE_FAN, position, false, 4, GPUVertexMode.Triangles, 6, "uint16", implicitFanIndices),
            ProductCase("fan-position-indexed", VertexMode.TRIANGLE_FAN, position, true, 4, GPUVertexMode.Triangles, 6, "uint16", explicitFanIndices),
            ProductCase("fan-uv-unindexed", VertexMode.TRIANGLE_FAN, positionUv, false, 4, GPUVertexMode.Triangles, 6, "uint16", implicitFanIndices),
            ProductCase("fan-uv-indexed", VertexMode.TRIANGLE_FAN, positionUv, true, 4, GPUVertexMode.Triangles, 6, "uint16", explicitFanIndices),
            ProductCase("fan-color-unindexed", VertexMode.TRIANGLE_FAN, positionColor, false, 4, GPUVertexMode.Triangles, 6, "uint16", implicitFanIndices),
            ProductCase("fan-color-indexed", VertexMode.TRIANGLE_FAN, positionColor, true, 4, GPUVertexMode.Triangles, 6, "uint16", explicitFanIndices),
            ProductCase("fan-color-uv-unindexed", VertexMode.TRIANGLE_FAN, positionColorUv, false, 4, GPUVertexMode.Triangles, 6, "uint16", implicitFanIndices),
            ProductCase("fan-color-uv-indexed", VertexMode.TRIANGLE_FAN, positionColorUv, true, 4, GPUVertexMode.Triangles, 6, "uint16", explicitFanIndices),
        )

        cases.forEach { case ->
            val points = listOf(Point2F32(0f, 0f), Point2F32(2f, 0f), Point2F32(2f, 2f), Point2F32(0f, 2f))
                .take(case.vertexCount)
            val colors = if ("color" in case.layout.attributes) {
                listOf(Color.RED, Color.GREEN, Color.BLUE, Color.WHITE).take(case.vertexCount)
            } else null
            val uvs = if ("texcoord" in case.layout.attributes) {
                listOf(Point2F32(0f, 0f), Point2F32(1f, 0f), Point2F32(1f, 1f), Point2F32(0f, 1f)).take(case.vertexCount)
            } else null
            val sourceIndices = if (!case.indexed) null else when (case.mode) {
                VertexMode.TRIANGLES -> listOf(2, 0, 1)
                VertexMode.TRIANGLE_STRIP -> listOf(3, 1, 2, 0)
                VertexMode.TRIANGLE_FAN -> listOf(2, 3, 0, 1)
            }
            val artifact = lower(DisplayOp.DrawVertices(
                Vertices(case.mode, points, uvs, colors, sourceIndices),
                Paint.fill(Color.WHITE), Matrix3x3F32.Identity, ClipStack.WideOpen,
            )).ready().draw.artifact

            assertEquals(case.vertexCount, artifact.vertexCount, case.name)
            assertEquals(case.indexCount, artifact.indexCount, case.name)
            assertEquals(case.topology, artifact.topology, case.name)
            assertEquals(case.indexFormat, artifact.indexFormat, case.name)
            assertEquals(case.layout, artifact.layout, case.name)
            assertEquals(
                expectedAttributeFormats.getValue(case.layout),
                artifact.layout.attributeFormats,
                case.name,
            )
            assertEquals(
                case.indexBytes?.toList(),
                artifact.indexBytesForUpload()?.toList(),
                case.name,
            )
        }
    }

    @Test
    fun `published attribute formats reject hostile mutation`() {
        val formats = lower(DisplayOp.DrawVertices(
            vertices(), Paint.fill(Color.WHITE), Matrix3x3F32.Identity, ClipStack.WideOpen,
        )).ready().draw.artifact.layout.attributeFormats

        @Suppress("UNCHECKED_CAST")
        assertFailsWith<UnsupportedOperationException> {
            (formats as MutableList<String>).clear()
        }
        assertEquals(listOf("float32x2"), formats)
    }

    @Test
    fun `skew rotation and reflection bounds conservatively enclose four transformed corners`() {
        val cases = listOf(
            Matrix3x3F32.of(1f, 1f, 0f, 0f, 1f, 0f, 0f, 0f, 1f) to GPUBounds(0f, 0f, 4f, 2f),
            Matrix3x3F32.of(0f, -1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f) to GPUBounds(-2f, 0f, 0f, 2f),
            Matrix3x3F32.scaling(-1f, 1f) to GPUBounds(-2f, 0f, 0f, 2f),
        )
        cases.forEach { (transform, expected) ->
            assertEquals(expected, lower(DisplayOp.DrawVertices(
                vertices(), Paint.fill(Color.RED), transform, ClipStack.WideOpen,
            )).ready().draw.deviceBounds)
        }
    }

    @Test
    fun `transform mesh bounds and mesh child refusals retain their canonical boundaries`() {
        val perspective = lower(DisplayOp.DrawVertices(
            vertices(), Paint.fill(Color.RED),
            Matrix3x3F32.of(1f, 0f, 0f, 0f, 1f, 0f, 0.1f, 0f, 1f), ClipStack.WideOpen,
        )).refused()
        assertEquals(GPUPreparedVerticesRefusalCodes.Transform, perspective.code)
        assertEquals("perspective", perspective.facts["reason"])

        val invalidBounds = lower(meshOperation(program = registeredMeshProgram()).copy(
            mesh = Mesh(vertices(), registeredMeshProgram(), RectF32(Float.NaN, 0f, 1f, 1f)),
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
        val clip = ClipStack.DeviceRect(RectF32.ofLTRB(4f, 6f, 12f, 14f), antiAlias = false)
        val draw = lower(DisplayOp.DrawVertices(
            vertices(), Paint.fill(Color.RED), Matrix3x3F32.translation(5f, 7f), clip,
        )).ready().draw

        assertEquals(GPUBounds(5f, 7f, 7f, 9f), draw.deviceBounds)
        assertEquals(GPUBounds(5f, 7f, 7f, 9f), draw.clippedBounds)
        assertTrue(draw.clipSnapshot.identity.contains("gpu-clip-coverage-v1"))
        assertIs<GPUClipCoveragePlan.Scissor>(draw.clipSnapshot.coveragePlan)
    }

    @Test
    fun `different device clips have distinct identities and an empty scissor intersection remains explicit`() {
        val first = lower(DisplayOp.DrawVertices(
            vertices(), Paint.fill(Color.RED), Matrix3x3F32.Identity,
            ClipStack.DeviceRect(RectF32.ofLTRB(10f, 10f, 20f, 20f), antiAlias = false),
        )).ready().draw
        val second = lower(DisplayOp.DrawVertices(
            vertices(), Paint.fill(Color.RED), Matrix3x3F32.Identity,
            ClipStack.DeviceRect(RectF32.ofLTRB(11f, 10f, 20f, 20f), antiAlias = false),
        )).ready().draw

        assertNotEquals(first.clipSnapshot.identity, second.clipSnapshot.identity)
        assertEquals<GPUBounds?>(null, first.clippedBounds)
        assertTrue(first.culledByClip)
    }

    @Test
    fun `clip authority refusal keeps the clip planner stable code`() {
        val refusal = GPUPreparedVerticesLowerer.lower(
            DisplayOp.DrawVertices(
                vertices(), Paint.fill(Color.RED), Matrix3x3F32.Identity,
                ClipStack.DeviceRect(RectF32.ofLTRB(0f, 0f, 2f, 2f), antiAlias = false),
            ),
            7,
            target(),
            capabilities(maxTextureDimension = 1),
        ).refused()
        assertEquals("unsupported.clip.texture_limit", refusal.code)
        assertEquals("coverage_refused", refusal.facts["reason"])
        assertEquals("GPUClipCoveragePlanner", refusal.facts["authority"])
    }

    @Test
    fun `perspective clip capture refuses with the clip boundary code`() {
        val refusal = GPUPreparedVerticesLowerer.lower(
            DisplayOp.DrawVertices(
                vertices(), Paint.fill(Color.RED), Matrix3x3F32.Identity,
                ClipStack.Complex(listOf(
                    ClipStackOp.PathOp(
                        Path().addRect(RectF32.ofLTRB(0f, 0f, 2f, 2f)),
                        ClipOp.INTERSECT,
                        antiAlias = true,
                        perspectiveCaptureRefusal = true,
                    ),
                )),
            ),
            7,
            target(),
            capabilities(maxTextureDimension = 1024),
        ).refused()
        assertEquals(GPUPreparedVerticesRefusalCodes.ClipCoverage, refusal.code)
        assertEquals("perspective_capture_refusal", refusal.facts["reason"])
    }

    @Test
    fun `mesh without override blend derives the final blend from the snapshotted paint`() {
        val draw = lower(meshOperation(
            paintBlend = BlendMode.MULTIPLY,
            overrideBlend = null,
            program = null,
        )).ready().draw

        assertEquals(GPUBlendMode.MULTIPLY, draw.finalBlend.mode)
        assertEquals(1, draw.paintAlphaApplicationCount)
    }

    @Test
    fun `mask and analytic clip plans refuse with clip coverage code before semantic publication`() {
        val maskClip = ClipStack.Complex(listOf(
            ClipStackOp.PathOp(Path().addRect(RectF32.ofLTRB(0f, 0f, 2f, 2f)), ClipOp.INTERSECT),
        ))
        val analyticClip = ClipStack.Complex(listOf(
            ClipStackOp.RectOp(RectF32.ofLTRB(0f, 0f, 2f, 2f), ClipOp.INTERSECT, antiAlias = false),
            ClipStackOp.RectOp(RectF32.ofLTRB(1f, 1f, 3f, 3f), ClipOp.INTERSECT, antiAlias = false),
        ))
        listOf(
            maskClip to "mask_clip_unsupported",
            analyticClip to "analytic_clip_unsupported",
        ).forEach { (clip, reason) ->
            val verticesRefusal = lower(DisplayOp.DrawVertices(
                vertices(), Paint.fill(Color.RED), Matrix3x3F32.Identity, clip,
            )).refused()
            assertEquals(GPUPreparedVerticesRefusalCodes.ClipCoverage, verticesRefusal.code, reason)
            assertEquals("clip", verticesRefusal.facts["stage"], reason)
            assertEquals("GPUClipMapper", verticesRefusal.facts["authority"], reason)
            assertEquals(reason, verticesRefusal.facts["reason"], reason)

            val meshRefusal = lower(meshOperation(program = registeredMeshProgram()).copy(
                clip = clip,
            )).refused()
            assertEquals(GPUPreparedVerticesRefusalCodes.ClipCoverage, meshRefusal.code, reason)
            assertEquals("clip", meshRefusal.facts["stage"], reason)
        }
    }

    @Test
    fun `transform overflow refuses rather than publishing nonfinite bounds`() {
        val refusal = lower(DisplayOp.DrawVertices(
            vertices(), Paint.fill(Color.RED), Matrix3x3F32.scaling(Float.MAX_VALUE, Float.MAX_VALUE), ClipStack.WideOpen,
        )).refused()

        assertEquals(GPUPreparedVerticesRefusalCodes.Transform, refusal.code)
        assertEquals("bounds_overflow", refusal.facts["reason"])
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
    fun `every nonreserved refusal code has one executable public lowerer case`() {
        val child = MeshProgram(effect("runtime.simple_rt"), UniformBlock { float4("gColor", 1f, 0f, 0f, 1f) },
            MeshChildren.of("blend" to BlenderChild(Blender.Arithmetic(0f, 0f, 0f, 0f))))
        val cases: Map<String, () -> GPUPreparedVerticesLowering.Refused> = mapOf(
            GPUPreparedVerticesRefusalCodes.PositionCount to { lower(DisplayOp.DrawVertices(Vertices(VertexMode.TRIANGLES, emptyList()), Paint.fill(Color.RED), Matrix3x3F32.Identity, ClipStack.WideOpen)).refused() },
            GPUPreparedVerticesRefusalCodes.AttributeCount to { lower(DisplayOp.DrawVertices(Vertices(VertexMode.TRIANGLES, vertices().positions, texCoords = listOf(Point2F32.Origin)), Paint.fill(Color.RED), Matrix3x3F32.Identity, ClipStack.WideOpen)).refused() },
            GPUPreparedVerticesRefusalCodes.NonFinite to { lower(DisplayOp.DrawVertices(Vertices(VertexMode.TRIANGLES, listOf(Point2F32(Float.NaN, 0f), Point2F32(1f, 0f), Point2F32(0f, 1f))), Paint.fill(Color.RED), Matrix3x3F32.Identity, ClipStack.WideOpen)).refused() },
            GPUPreparedVerticesRefusalCodes.IndexOutOfRange to { lower(DisplayOp.DrawVertices(Vertices(VertexMode.TRIANGLES, vertices().positions, indices = listOf(0, 1, 9)), Paint.fill(Color.RED), Matrix3x3F32.Identity, ClipStack.WideOpen)).refused() },
            GPUPreparedVerticesRefusalCodes.IndexFormat to { lower(DisplayOp.DrawVertices(Vertices(VertexMode.TRIANGLES, List(65538) { Point2F32(it.toFloat(), 0f) }, indices = listOf(65537, 0, 1)), Paint.fill(Color.RED), Matrix3x3F32.Identity, ClipStack.WideOpen)).refused() },
            GPUPreparedVerticesRefusalCodes.Transform to { lower(DisplayOp.DrawVertices(vertices(), Paint.fill(Color.RED), Matrix3x3F32.of(1f,0f,0f,0f,1f,0f,.1f,0f,1f), ClipStack.WideOpen)).refused() },
            GPUPreparedVerticesRefusalCodes.Material to { lower(DisplayOp.DrawVertices(vertices(), Paint.fill(Color.RED).copy(shader = hostileShader()), Matrix3x3F32.Identity, ClipStack.WideOpen)).refused() },
            GPUPreparedVerticesRefusalCodes.Budget to { lower(DisplayOp.DrawVertices(Vertices(VertexMode.TRIANGLES, List(1_000_002) { Point2F32(it.toFloat(), 0f) }), Paint.fill(Color.RED), Matrix3x3F32.Identity, ClipStack.WideOpen)).refused() },
            GPUPreparedVerticesRefusalCodes.MeshBounds to { lower(meshOperation(program = registeredMeshProgram()).copy(mesh = Mesh(vertices(), registeredMeshProgram(), RectF32(Float.NaN,0f,1f,1f)))).refused() },
            GPUPreparedVerticesRefusalCodes.MeshProgramUnregistered to { lower(meshOperation(program = MeshProgram(effect("missing")))).refused() },
            GPUPreparedVerticesRefusalCodes.MeshProgramCpuUnavailable to { lower(meshOperation(program = registeredMeshProgram()), GPUPreparedRuntimeEffectResolver { _, _ -> GPUPreparedRuntimeEffectResolution.ProgramUnavailable("cpu", GPUPreparedRuntimeEffectResolution.ProgramUnavailableReason.CpuUnavailable) }).refused() },
            GPUPreparedVerticesRefusalCodes.MeshProgramWgslUnavailable to { lower(meshOperation(program = registeredMeshProgram()), GPUPreparedRuntimeEffectResolver { _, _ -> GPUPreparedRuntimeEffectResolution.ProgramUnavailable("wgsl", GPUPreparedRuntimeEffectResolution.ProgramUnavailableReason.WgslUnavailable) }).refused() },
            GPUPreparedVerticesRefusalCodes.MeshProgramWgslValidation to { lower(meshOperation(program = registeredMeshProgram()), GPUPreparedRuntimeEffectResolver { _, _ -> GPUPreparedRuntimeEffectResolution.ProgramUnavailable("wgsl", GPUPreparedRuntimeEffectResolution.ProgramUnavailableReason.WgslValidation) }).refused() },
            GPUPreparedVerticesRefusalCodes.MeshProgramAbi to { lower(meshOperation(program = registeredMeshProgram(uniforms = UniformBlock { }))).refused() },
            GPUPreparedVerticesRefusalCodes.MeshProgramChild to { lower(meshOperation(program = child)).refused() },
            GPUPreparedVerticesRefusalCodes.ClipCoverage to { lower(DisplayOp.DrawVertices(vertices(), Paint.fill(Color.RED), Matrix3x3F32.Identity, ClipStack.Complex(listOf(ClipStackOp.PathOp(Path().addRect(RectF32.ofLTRB(0f, 0f, 2f, 2f)), ClipOp.INTERSECT))))).refused() },
        )
        val reserved = GPUPreparedVerticesRefusalCoverage.classifications
            .filterValues { it.disposition == GPUPreparedVerticesRefusalDisposition.Reserved }
        assertEquals(GPUPreparedVerticesRefusalCodes.ALL, (cases.keys + reserved.keys).toSet())
        val expectedAuthorities = mapOf(
            GPUPreparedVerticesRefusalCodes.PositionCount to "GPUPreparedVerticesPacker",
            GPUPreparedVerticesRefusalCodes.AttributeCount to "GPUPreparedVerticesPacker",
            GPUPreparedVerticesRefusalCodes.NonFinite to "GPUPreparedVerticesPacker",
            GPUPreparedVerticesRefusalCodes.IndexOutOfRange to "GPUPreparedVerticesPacker",
            GPUPreparedVerticesRefusalCodes.IndexFormat to "GPUPreparedVerticesPacker",
            GPUPreparedVerticesRefusalCodes.Transform to "GPUPreparedVerticesLowerer",
            GPUPreparedVerticesRefusalCodes.Material to "PreparedTextPaintSnapshotter",
            GPUPreparedVerticesRefusalCodes.Budget to "GPUPreparedVerticesPacker",
            GPUPreparedVerticesRefusalCodes.MeshBounds to "GPUPreparedVerticesLowerer",
            GPUPreparedVerticesRefusalCodes.MeshProgramUnregistered to "KanvasPreparedRuntimeEffectResolver",
            GPUPreparedVerticesRefusalCodes.MeshProgramCpuUnavailable to "GPUPreparedRuntimeEffectResolver",
            GPUPreparedVerticesRefusalCodes.MeshProgramWgslUnavailable to "GPUPreparedRuntimeEffectResolver",
            GPUPreparedVerticesRefusalCodes.MeshProgramWgslValidation to "GPUPreparedRuntimeEffectResolver",
            GPUPreparedVerticesRefusalCodes.MeshProgramAbi to "GPUPreparedMaterialProgramCompiler",
            GPUPreparedVerticesRefusalCodes.MeshProgramChild to "GPUMaterialMapper",
            GPUPreparedVerticesRefusalCodes.ClipCoverage to "GPUClipMapper",
        )
        assertEquals(cases.keys, expectedAuthorities.keys)
        cases.forEach { (code, execute) ->
            val refusal = execute()
            assertEquals(code, refusal.code)
            assertEquals(7, refusal.operationIndex)
            assertEquals(expectedAuthorities.getValue(code), refusal.facts["authority"], code)
            assertTrue(refusal.facts["reason"].orEmpty().isNotBlank())
        }
        reserved.values.forEach { assertTrue(it.reason.isNotBlank() && it.authority.isNotBlank()) }
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
            Paint.fill(Color.fromRGBA(1f, 1f, 1f, 0.5f)), Matrix3x3F32.Identity, ClipStack.WideOpen,
        )).ready().draw

        assertEquals(1f, draw.material.paintAlpha)
        assertEquals(
            128f / 255f,
            java.nio.ByteBuffer.wrap(draw.material.uniformBytes.map(Int::toByte).toByteArray())
                .order(java.nio.ByteOrder.LITTLE_ENDIAN).getFloat(12),
        )
        assertEquals(GPUBlendMode.SRC_OVER, draw.primitiveBlendPlan?.plan?.mode)
        assertEquals(GPUSourceAlphaClassification.Translucent, draw.finalBlend.sourceAlpha)
        assertEquals(1, draw.paintAlphaApplicationCount)
    }

    @Test
    fun `opaque solid vertices retain opaque target source alpha without vertex colors`() {
        val draw = lower(DisplayOp.DrawVertices(
            vertices(), Paint.fill(Color.WHITE), Matrix3x3F32.Identity, ClipStack.WideOpen,
        )).ready().draw
        assertEquals(GPUSourceAlphaClassification.ProvenOpaque, draw.finalBlend.sourceAlpha)
    }

    @Test
    fun `hostile paint snapshot becomes one typed material refusal`() {
        val result = lower(DisplayOp.DrawVertices(
            vertices(), Paint.fill(Color.RED).copy(shader = hostileShader()),
            Matrix3x3F32.Identity, ClipStack.WideOpen,
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
    fun `runtime unknown is generic material and resolver is called once then frozen`() {
        var calls = 0
        val resolver = GPUPreparedRuntimeEffectResolver { effectId, version ->
            calls++
            if (calls == 1) org.graphiks.kanvas.gpu.renderer.runtimeeffects.KanvasPreparedRuntimeEffectResolver()
                .resolve(effectId, version)
            else GPUPreparedRuntimeEffectResolution.ProgramUnavailable(
                "changed", GPUPreparedRuntimeEffectResolution.ProgramUnavailableReason.Unknown,
            )
        }
        val draw = lower(meshOperation(program = registeredMeshProgram()), resolver).ready().draw
        assertEquals(1, calls)
        assertEquals(GPUPreparedVerticesOperationKind.DrawMesh, draw.operationKind)

        val unknown = lower(meshOperation(program = registeredMeshProgram()),
            GPUPreparedRuntimeEffectResolver { _, _ -> GPUPreparedRuntimeEffectResolution.ProgramUnavailable(
                "unknown", GPUPreparedRuntimeEffectResolution.ProgramUnavailableReason.Unknown,
            ) }).refused()
        assertEquals(GPUPreparedVerticesRefusalCodes.Material, unknown.code)
        assertEquals("Unknown", unknown.facts["runtimeReason"])
    }

    @Test
    fun `root resolver exception and linkage failure retain the resolver boundary`() {
        val cases = listOf(
            GPUPreparedRuntimeEffectResolver { _, _ -> throw IllegalStateException("boom") } to
                "resolver_exception",
            GPUPreparedRuntimeEffectResolver { _, _ -> throw LinkageError("missing") } to
                "resolver_linkage_failure",
        )

        cases.forEach { (resolver, reason) ->
            val refusal = lower(meshOperation(program = registeredMeshProgram()), resolver).refused()
            assertEquals(GPUPreparedVerticesRefusalCodes.Material, refusal.code)
            assertEquals(7, refusal.operationIndex)
            assertEquals(
                mapOf(
                    "stage" to "runtime-resolver",
                    "reason" to reason,
                    "authority" to "GPUPreparedRuntimeEffectResolver",
                ),
                refusal.facts,
            )
        }
    }

    @Test
    fun `canonical registered program with missing uniform payload reaches compiler ABI refusal`() {
        val abi = lower(meshOperation(program = registeredMeshProgram(uniforms = UniformBlock { }))).refused()

        assertEquals(GPUPreparedVerticesRefusalCodes.MeshProgramAbi, abi.code)
        assertEquals(7, abi.operationIndex)
        assertEquals(
            mapOf(
                "stage" to "mesh-program",
                "reason" to "compiler_refused",
                "authority" to "GPUPreparedMaterialProgramCompiler",
                "compilerCode" to "unsupported.material.runtime_effect.uniform_payload",
                "sourceKind" to "RuntimeEffect",
            ),
            abi.facts,
        )
    }

    @Test
    fun `compiler exception and linkage failure retain the compiler boundary`() {
        val failures = listOf<() -> Nothing>(
            { throw IllegalStateException("compiler boom") },
            { throw LinkageError("compiler missing") },
        )
        val expectedReasons = listOf("compiler_exception", "compiler_linkage_failure")

        failures.zip(expectedReasons).forEach { (failure, reason) ->
            val refusal = lower(
                meshOperation(program = registeredMeshProgram()),
                KanvasPreparedRuntimeEffectResolver(),
                GPUPreparedVerticesMaterialCompiler { _, _, _ -> failure() },
            ).refused()
            assertEquals(GPUPreparedVerticesRefusalCodes.Material, refusal.code)
            assertEquals(
                mapOf(
                    "stage" to "material-compiler",
                    "reason" to reason,
                    "authority" to "GPUPreparedMaterialProgramCompiler",
                ),
                refusal.facts,
            )
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun `fatal JVM errors escape resolver and compiler boundaries`() {
        val fatalFactories = listOf<() -> Error>(
            { OutOfMemoryError("fatal vm") },
            { ThreadDeath() },
        )

        fatalFactories.forEach { fatal ->
            assertFailsWith<Error> {
                lower(
                    meshOperation(program = registeredMeshProgram()),
                    GPUPreparedRuntimeEffectResolver { _, _ -> throw fatal() },
                )
            }
            assertFailsWith<Error> {
                lower(
                    meshOperation(program = registeredMeshProgram()),
                    KanvasPreparedRuntimeEffectResolver(),
                    GPUPreparedVerticesMaterialCompiler { _, _, _ -> throw fatal() },
                )
            }
        }
    }

    @Test
    fun `child resolver failure inside compiler retains resolver source authority`() {
        val canonical = KanvasPreparedRuntimeEffectResolver()
        val resolver = GPUPreparedRuntimeEffectResolver { effectId, version ->
            if (effectId == "runtime.child") throw IllegalStateException("child resolver boom")
            canonical.resolve(effectId, version)
        }
        val refusal = lower(
            meshOperation(program = registeredMeshProgram()),
            resolver,
            GPUPreparedVerticesMaterialCompiler { _, _, context ->
                context.runtimeEffectResolver.resolve("runtime.child", 1)
                error("unreachable")
            },
        ).refused()

        assertEquals(
            mapOf(
                "stage" to "runtime-resolver",
                "reason" to "resolver_exception",
                "authority" to "GPUPreparedRuntimeEffectResolver",
            ),
            refusal.facts,
        )
        assertEquals(GPUPreparedVerticesRefusalCodes.Material, refusal.code)
        assertEquals(7, refusal.operationIndex)
    }

    @Test
    fun `root and child resolutions are memoized independently by key`() {
        val canonical = KanvasPreparedRuntimeEffectResolver()
        val calls = linkedMapOf<Pair<String, Int>, Int>()
        val resolver = GPUPreparedRuntimeEffectResolver { effectId, version ->
            val key = effectId to version
            calls[key] = calls.getOrDefault(key, 0) + 1
            if (effectId == "runtime.child") {
                GPUPreparedRuntimeEffectResolution.DescriptorUnavailable("child unavailable")
            } else {
                canonical.resolve(effectId, version)
            }
        }
        val draw = lower(
            meshOperation(program = registeredMeshProgram()),
            resolver,
            GPUPreparedVerticesMaterialCompiler { descriptor, paintAlpha, context ->
                context.runtimeEffectResolver.resolve("runtime.child", 1)
                context.runtimeEffectResolver.resolve("runtime.child", 1)
                GPUPreparedMaterialProgramCompiler.compile(descriptor, paintAlpha, context)
            },
        ).ready().draw

        assertEquals(GPUPreparedVerticesOperationKind.DrawMesh, draw.operationKind)
        assertEquals(
            mapOf(("runtime.simple_rt" to 1) to 1, ("runtime.child" to 1) to 1),
            calls,
        )
    }

    @Test
    fun `mesh snapshots vertices and clips before resolver can mutate caller objects`() {
        val positions = mutableListOf(Point2F32(0f, 0f), Point2F32(2f, 0f), Point2F32(0f, 2f))
        val clip = ClipStack.DeviceRect(RectF32.ofLTRB(0f, 0f, 4f, 4f), antiAlias = false)
        val operation = meshOperation(program = registeredMeshProgram()).copy(
            mesh = Mesh(
                Vertices(VertexMode.TRIANGLES, positions),
                registeredMeshProgram(),
                RectF32.ofLTRB(0f, 0f, 2f, 2f),
            ),
            clip = clip,
        )
        val resolver = GPUPreparedRuntimeEffectResolver { effectId, version ->
            positions[0] = Point2F32(99f, 99f)
            org.graphiks.kanvas.gpu.renderer.runtimeeffects.KanvasPreparedRuntimeEffectResolver()
                .resolve(effectId, version)
        }

        val draw = lower(operation, resolver).ready().draw

        assertEquals(0f, java.nio.ByteBuffer.wrap(draw.artifact.vertexBytesForUpload())
            .order(java.nio.ByteOrder.LITTLE_ENDIAN).float)
        assertEquals(clip, draw.clip)
        assertEquals(
            GPUBounds(0f, 0f, 4f, 4f),
            assertIs<GPUClipCoveragePlan.Scissor>(draw.clipSnapshot.coveragePlan).bounds,
        )
    }

    private fun lower(operation: DisplayOp): GPUPreparedVerticesLowering =
        GPUPreparedVerticesLowerer.lower(operation, 7, target(), capabilities())

    private fun lower(
        operation: DisplayOp,
        runtimeEffectResolver: GPUPreparedRuntimeEffectResolver,
    ): GPUPreparedVerticesLowering =
        GPUPreparedVerticesLowerer.lower(operation, 7, target(), capabilities(), runtimeEffectResolver)

    private fun lower(
        operation: DisplayOp,
        runtimeEffectResolver: GPUPreparedRuntimeEffectResolver,
        materialCompiler: GPUPreparedVerticesMaterialCompiler,
    ): GPUPreparedVerticesLowering = GPUPreparedVerticesLowerer.lower(
        operation, 7, target(), capabilities(), runtimeEffectResolver, materialCompiler,
    )

    private fun meshOperation(
        paintBlend: BlendMode = BlendMode.SRC_OVER,
        overrideBlend: BlendMode? = null,
        program: MeshProgram? = null,
    ): DisplayOp.DrawMesh = DisplayOp.DrawMesh(
        mesh = Mesh(vertices(), program, RectF32.ofLTRB(0f, 0f, 2f, 2f)),
        paint = Paint.fill(Color.RED).copy(blendMode = paintBlend),
        blendMode = overrideBlend,
        transform = Matrix3x3F32.Identity,
        clip = ClipStack.WideOpen,
    )

    private fun vertices(): Vertices = Vertices(
        VertexMode.TRIANGLES,
        listOf(Point2F32(0f, 0f), Point2F32(2f, 0f), Point2F32(0f, 2f)),
    )

    private fun registeredMeshProgram(
        uniforms: UniformBlock = UniformBlock { float4("gColor", 1f, 0f, 0f, 1f) },
    ): MeshProgram = MeshProgram(
        effect("runtime.simple_rt"),
        uniforms = uniforms,
    )

    private data class ProductCase(
        val name: String,
        val mode: VertexMode,
        val layout: GPUVertexLayoutPlan,
        val indexed: Boolean,
        val vertexCount: Int,
        val topology: GPUVertexMode,
        val indexCount: Int?,
        val indexFormat: String?,
        val indexBytes: ByteArray?,
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

    private fun capabilities(maxTextureDimension: Int = 8192): GPUCapabilities = GPUCapabilities(
        implementation = GPUImplementationIdentity("wgpu4k", "test", "adapter", "device"),
        facts = emptyList(), snapshotId = "fp06-vertices",
        limits = GPULimits(maxTextureDimension.toLong(), 256, 256, maxBufferSize = 1L shl 30, maxDynamicUniformBuffersPerPipelineLayout = 1),
    )
}

private fun GPUPreparedVerticesLowering.Ready.ready(): GPUPreparedVerticesLowering.Ready = this
private fun GPUPreparedVerticesLowering.ready(): GPUPreparedVerticesLowering.Ready =
    assertIs<GPUPreparedVerticesLowering.Ready>(this)
private fun GPUPreparedVerticesLowering.refused(): GPUPreparedVerticesLowering.Refused =
    assertIs<GPUPreparedVerticesLowering.Refused>(this)
