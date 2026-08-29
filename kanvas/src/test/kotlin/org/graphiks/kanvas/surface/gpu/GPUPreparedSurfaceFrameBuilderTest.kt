package org.graphiks.kanvas.surface.gpu

import java.io.File
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUFirstSliceCapabilityName.PATH_FILL_STENCIL_COVER
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformType
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUPreparedImageRefusalCodes
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveSourceFamily
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveMaterialPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedAtlasSourceBlend
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageSampling
import org.graphiks.kanvas.gpu.renderer.product.GPUProductFlagConfig
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.math.color.ColorARGB
import org.graphiks.kanvas.types.Lattice
import org.graphiks.kanvas.types.LatticeFlags
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.kanvas.types.PointMode
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32
import org.graphiks.kanvas.types.VertexMode
import org.graphiks.kanvas.types.Vertices

class GPUPreparedSurfaceFrameBuilderTest {
    @Test
    fun `matrix transform facts classify coherent pure scales as Scale`() {
        val cases = listOf(
            Triple("identity", Matrix3x3F32.Identity, GPUTransformType.Identity),
            Triple("positive-scale", Matrix3x3F32(sx = 2f, sy = 3f), GPUTransformType.Scale),
            Triple("reflected-scale", Matrix3x3F32(sx = -2f, sy = 3f), GPUTransformType.Scale),
            Triple("singular", Matrix3x3F32(sx = 0f, sy = 1f), GPUTransformType.Affine),
            Triple(
                "non-finite-determinant",
                Matrix3x3F32(sx = Float.MAX_VALUE, sy = Float.MAX_VALUE),
                GPUTransformType.Affine,
            ),
            Triple(
                "signed-zero-translation",
                Matrix3x3F32(sx = 2f, sy = 3f, tx = -0f),
                GPUTransformType.Affine,
            ),
            Triple("perspective", Matrix3x3F32(persp0 = 0.25f), GPUTransformType.Perspective),
        )

        cases.forEach { (label, matrix, expectedType) ->
            assertEquals(expectedType, matrix.toGPUTransformFacts().type, label)
        }
    }

    @Test
    fun `matrix transform facts classify pure translation separately from scale translation and skew`() {
        val cases = listOf(
            Triple("pure-translation", Matrix3x3F32(tx = 4f, ty = 5f), GPUTransformType.Translate),
            Triple("scale-translation", Matrix3x3F32(sx = 2f, sy = 3f, tx = 4f, ty = 5f), GPUTransformType.Affine),
            Triple("skew", Matrix3x3F32(kx = 0.25f), GPUTransformType.Affine),
        )

        cases.forEach { (label, matrix, expectedType) ->
            assertEquals(expectedType, matrix.toGPUTransformFacts().type, label)
        }
    }

    @Test
    fun `public non finite singular and perspective transforms refuse before frame task assembly`() {
        val cases = listOf(
            "non-finite" to Matrix3x3F32(tx = Float.NaN) to "unsupported.transform.non_finite",
            "singular" to Matrix3x3F32(sx = 0f, sy = 1f) to "unsupported.transform.affine_singular",
            "perspective" to Matrix3x3F32(persp0 = .1f) to "unsupported.transform.perspective",
        )

        cases.forEach { (labelAndMatrix, expectedCode) ->
            val (label, matrix) = labelAndMatrix
            val refused = assertIs<GPUPreparedSurfaceFrameBuildResult.Refused>(
                GPUPreparedSurfaceFrameBuilder.build(
                    request(listOf(rect().copy(transform = matrix))),
                ),
                label,
            )
            assertEquals(expectedCode, refused.diagnostic.code.value, label)
        }
    }

    @Test
    fun `public axis scaled solid DrawRRect records sealed device geometry`() {
        val result = GPUPreparedSurfaceFrameBuilder.build(
            request(
                listOf(
                    DisplayOp.DrawRRect(
                        RRectF32.of(RectF32.ofLTRB(8f, 16f, 24f, 48f), radius = 4f),
                        Paint.fill(ColorARGB.of(255, 255, 165, 0)).copy(antiAlias = false),
                        Matrix3x3F32(sx = 2f, sy = 1f),
                        ClipStack.WideOpen,
                    ),
                ),
            ).copy(
                targetFacts = GPUTargetFacts(64, 64, "rgba8unorm-srgb"),
                targetBounds = GPUPixelBounds(0, 0, 64, 64),
            ),
        )
        val ready = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            result,
            (result as? GPUPreparedSurfaceFrameBuildResult.Refused)?.diagnostic?.let { diagnostic ->
                "${diagnostic.code.value}: ${diagnostic.facts}"
            },
        )
        val packet = ready.taskList.tasks
            .filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
            .single()
        val semantic = assertIs<GPUDrawSemanticPayload.CorePrimitive>(packet.semanticPayload)
        val geometry = assertIs<GPUCorePrimitiveGeometry.RRect>(semantic.geometry)

        assertEquals(GPUCorePrimitiveSourceFamily.RRect, semantic.sourceFamily)
        assertEquals(16f, geometry.left)
        assertEquals(16f, geometry.top)
        assertEquals(48f, geometry.right)
        assertEquals(48f, geometry.bottom)
        assertEquals(listOf(8f, 4f, 8f, 4f, 8f, 4f, 8f, 4f), geometry.radii)
    }

    @Test
    fun `public scaled aa and linear gradient DrawRRect refuse without prepared packets`() {
        fun scaledRRect(paint: Paint) = DisplayOp.DrawRRect(
            RRectF32.of(RectF32.ofLTRB(8f, 16f, 24f, 48f), radius = 4f),
            paint,
            Matrix3x3F32(sx = 2f, sy = 1f),
            ClipStack.WideOpen,
        )
        val linearGradient = Paint.fill(ColorARGB.Transparent).copy(
            shader = Shader.LinearGradient(
                Point2F32(8f, 16f),
                Point2F32(24f, 48f),
                listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
            ),
            antiAlias = false,
        )
        val cases = listOf(
            "anti-alias" to Paint.fill(ColorARGB.of(255, 255, 165, 0)).copy(antiAlias = true),
            "linear-gradient" to linearGradient,
        )

        cases.forEach { (label, paint) ->
            val result = GPUPreparedSurfaceFrameBuilder.build(
                request(listOf(scaledRRect(paint)), capabilitiesWithLinearFact()).copy(
                    targetFacts = GPUTargetFacts(64, 64, "rgba8unorm-srgb"),
                    targetBounds = GPUPixelBounds(0, 0, 64, 64),
                ),
            )
            val refused = assertIs<GPUPreparedSurfaceFrameBuildResult.Refused>(result, label)

            assertEquals("unsupported.transform.rrect_scale_unproven", refused.diagnostic.code.value, label)
        }
    }

    @Test
    fun `public DrawRect stroke records four ordinary fill visuals with one source operation ownership`() {
        val result = GPUPreparedSurfaceFrameBuilder.build(
            request(
                listOf(
                    DisplayOp.DrawRect(
                        RectF32.ofLTRB(8f, 8f, 24f, 20f),
                        Paint.stroke(ColorARGB.Red, 4f).copy(antiAlias = false),
                        Matrix3x3F32.Identity,
                        ClipStack.WideOpen,
                    ),
                ),
            ),
        )

        val ready = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            result,
            (result as? GPUPreparedSurfaceFrameBuildResult.Refused)?.diagnostic?.let { diagnostic ->
                "${diagnostic.code.value}: ${diagnostic.facts}"
            },
        )
        assertEquals(4, ready.visualOperationCount)
        val packets = ready.taskList.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
        assertEquals(listOf(0, 1, 2, 3), packets.map(GPUDrawPacket::commandIdValue))
        assertEquals(4, packets.size)
    }

    @Test
    fun `public bounded gradient stroke records four gradient CorePrimitive packets without changing local axes`() {
        val request = request(
            listOf(
                DisplayOp.DrawRect(
                    RectF32.ofLTRB(8f, 16f, 56f, 48f),
                    Paint.stroke(ColorARGB.Transparent, 4f).copy(
                        shader = Shader.LinearGradient(
                            Point2F32(8.5f, 32.5f),
                            Point2F32(55.5f, 32.5f),
                            listOf(
                                GradientStop(0f, ColorARGB.of(255, 255, 56, 56)),
                                GradientStop(1f, ColorARGB.of(255, 56, 112, 255)),
                            ),
                        ),
                        antiAlias = false,
                    ),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            capabilitiesWithLinearFact(),
        ).copy(
            targetFacts = GPUTargetFacts(64, 64, "rgba8unorm-srgb"),
            targetBounds = GPUPixelBounds(0, 0, 64, 64),
        )

        val ready = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            GPUPreparedSurfaceFrameBuilder.build(request),
        )
        val packets = ready.taskList.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
        assertEquals(listOf(0, 1, 2, 3), packets.map(GPUDrawPacket::commandIdValue))
        assertEquals(listOf(0, 1, 2, 3), packets.map(GPUDrawPacket::originalPaintOrder))
        assertEquals(
            List(4) { "layout.core-primitive.dynamic-uniform592-gradient-linear-v1" },
            packets.map(GPUDrawPacket::bindingLayoutHash),
        )
        packets.forEach { packet ->
            val semantic = assertIs<GPUDrawSemanticPayload.CorePrimitive>(packet.semanticPayload)
            val material = assertIs<GPUCorePrimitiveMaterialPayload.LinearGradient>(semantic.material)
            assertEquals(8.5f, material.startX)
            assertEquals(32.5f, material.startY)
            assertEquals(55.5f, material.endX)
            assertEquals(32.5f, material.endY)
            assertEquals(listOf(0f, 1f), material.positions)
            assertEquals("clamp", material.tileMode)
            assertEquals("srgb", material.interpolation)
        }
    }

    @Test
    fun `public DrawPath gradient stroke refuses before prepared packets are published`() {
        val result = GPUPreparedSurfaceFrameBuilder.build(
            request(
                listOf(
                    DisplayOp.DrawPath(
                        Path().addRect(RectF32.ofLTRB(8f, 16f, 24f, 22f)),
                        Paint.stroke(ColorARGB.Transparent, 4f).copy(
                            shader = Shader.LinearGradient(
                                Point2F32(8.5f, 19.5f), Point2F32(23.5f, 19.5f),
                                listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                            ),
                            antiAlias = false,
                        ),
                        Matrix3x3F32.Identity,
                        ClipStack.WideOpen,
                    ),
                ),
                capabilitiesWithLinearFact(),
            ),
        )

        val refused = assertIs<GPUPreparedSurfaceFrameBuildResult.Refused>(result)
        assertEquals("unsupported.core_primitive.material.path_stencil", refused.diagnostic.code.value)
    }

    @Test
    fun `public DrawRRect linear gradient records the analytic 656 byte CorePrimitive program`() {
        val result = GPUPreparedSurfaceFrameBuilder.build(
            request(
                listOf(
                    DisplayOp.DrawRRect(
                        RRectF32.of(RectF32.ofLTRB(2f, 3f, 30f, 21f), radius = 4f),
                        Paint(
                            shader = Shader.LinearGradient(
                                Point2F32(2f, 3f),
                                Point2F32(30f, 21f),
                                listOf(
                                    GradientStop(0f, ColorARGB.of(160, 40, 120, 208)),
                                    GradientStop(1f, ColorARGB.of(96, 224, 72, 48)),
                                ),
                            ),
                        ).copy(antiAlias = false),
                        Matrix3x3F32.Identity,
                        ClipStack.WideOpen,
                    ),
                ),
                capabilitiesWithLinearFact(),
            ),
        )

        val ready = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(result)
        val packet = ready.taskList.tasks
            .filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
            .single()
        assertEquals(
            "layout.core-primitive.dynamic-uniform656-gradient-analytic-linear-v1",
            packet.bindingLayoutHash,
        )
    }

    @Test
    fun `public DrawRect with injected LinearGradient fact records the direct 592 byte CorePrimitive program`() {
        val result = GPUPreparedSurfaceFrameBuilder.build(
            request(
                listOf(
                    DisplayOp.DrawRect(
                        RectF32.ofLTRB(2f, 3f, 30f, 21f),
                        Paint(
                            shader = Shader.LinearGradient(
                                Point2F32(2f, 3f),
                                Point2F32(30f, 21f),
                                listOf(
                                    GradientStop(0f, ColorARGB.of(160, 40, 120, 208)),
                                    GradientStop(1f, ColorARGB.of(96, 224, 72, 48)),
                                ),
                            ),
                        ).copy(antiAlias = false),
                        Matrix3x3F32.Identity,
                        ClipStack.WideOpen,
                    ),
                ),
                capabilitiesWithLinearFact(),
            ),
        )

        val ready = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(result)
        val packet = ready.taskList.tasks
            .filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
            .single()
        assertEquals(
            "layout.core-primitive.dynamic-uniform592-gradient-linear-v1",
            packet.bindingLayoutHash,
        )
    }

    @Test
    fun `public LinearGradient with destination read blend closes before prepared task publication`() {
        val result = GPUPreparedSurfaceFrameBuilder.build(
            request(
                listOf(
                    DisplayOp.DrawRect(
                        RectF32.ofLTRB(2f, 3f, 30f, 21f),
                        Paint(
                            shader = Shader.LinearGradient(
                                Point2F32(2f, 3f),
                                Point2F32(30f, 21f),
                                listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                            ),
                        ).copy(antiAlias = false, blendMode = BlendMode.MULTIPLY),
                        Matrix3x3F32.Identity,
                        ClipStack.WideOpen,
                    ),
                ),
            ),
        )

        assertEquals(
            "unsupported.native-core-primitive.blend",
            assertIs<GPUPreparedSurfaceFrameBuildResult.Refused>(result).diagnostic.code.value,
        )
    }

    @Test
    fun `prepared vertices semantic-only draw plans an executable render task`() {
        val base = request(listOf(rect()))
        val result = GPUPreparedSurfaceFrameBuilder.build(
            base.copy(
                candidate = GPUPreparedSurfaceEligibility.Candidate(
                    operations = listOf(vertices()),
                    config = base.candidate.config,
                    color = base.candidate.color,
                ),
            ),
        )

        val ready = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            result,
            (result as? GPUPreparedSurfaceFrameBuildResult.Refused)
                ?.diagnostic?.code?.value.toString(),
        )
        val verticesPacket = ready.taskList.tasks
            .filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
            .single { packet -> packet.semanticPayload is GPUDrawSemanticPayload.Vertices }
        assertNotNull(verticesPacket.renderPipelineKey)
        val framePlan = org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlanner.plan(
            ready.taskList,
        )
        assertFalse(framePlan.atomicallyRefused)
    }

    @Test
    fun `core frame above text generation range remains valid when it contains no text`() {
        val result = GPUPreparedSurfaceFrameBuilder.build(
            request(listOf(rect())).copy(
                frameId = GPUFrameID(Int.MAX_VALUE.toLong() + 1L),
            ),
        )

        assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(result)
    }

    @Test
    fun `prepared atlas expands to ordered sampled packets sharing one artifact with distinct uniforms`() {
        val atlas = atlasImage("builder-atlas")
        val operation = DisplayOp.DrawAtlas(
            atlas = atlas,
            transforms = listOf(
                Matrix3x3F32.translation(2f, 3f),
                Matrix3x3F32.translation(12f, 5f) * Matrix3x3F32.skewing(0.25f, 0f),
            ),
            texRects = listOf(
                RectF32.ofLTRB(0f, 0f, 2f, 2f),
                RectF32.ofLTRB(2f, 0f, 4f, 2f),
            ),
            colors = listOf(ColorARGB.Red, ColorARGB.of(128, 0, 128, 0)),
            blendMode = BlendMode.MODULATE,
            paint = Paint.fill(ColorARGB.of(192, 255, 255, 255)),
            transform = Matrix3x3F32.Identity,
            clip = ClipStack.WideOpen,
        )

        val buildResult = GPUPreparedSurfaceFrameBuilder.build(imageRequest(listOf(operation)))
        val ready = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            buildResult,
            buildResult.toString(),
        )
        val packets = ready.taskList.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
        val semantics = packets.map { packet ->
            assertIs<GPUDrawSemanticPayload.SampledImage>(packet.semanticPayload)
        }
        val uploads = ready.taskList.tasks.filterIsInstance<GPUTask.Upload>()
            .mapNotNull(GPUTask.Upload::imageResourcePlan)

        assertEquals(listOf(0, 1), packets.map(GPUDrawPacket::commandIdValue))
        assertEquals(2, ready.visualOperationCount)
        assertEquals(1, semantics.map { it.artifact.key }.toSet().size)
        assertEquals(1, uploads.size)
        assertEquals(
            listOf(GPUPreparedAtlasSourceBlend.Modulate, GPUPreparedAtlasSourceBlend.Modulate),
            semantics.map { it.atlasSourceBlend },
        )
        assertEquals(
            listOf(1f, 0f, 0f, 1f),
            semantics[0].atlasColorPremultipliedRgba,
        )
        assertEquals(
            listOf(0f, (128f / 255f) * (128f / 255f), 0f, 128f / 255f),
            semantics[1].atlasColorPremultipliedRgba,
        )
        assertNotEquals(semantics[0].canonicalHash, semantics[1].canonicalHash)
    }

    @Test
    fun `prepared atlas materializes one exact integral scissor without changing sprite order`() {
        val atlas = atlasImage("builder-atlas-scissor")
        val operation = DisplayOp.DrawAtlas(
            atlas = atlas,
            transforms = listOf(
                Matrix3x3F32.translation(2f, 3f),
                Matrix3x3F32.translation(12f, 5f),
            ),
            texRects = listOf(
                RectF32.ofLTRB(0f, 0f, 2f, 2f),
                RectF32.ofLTRB(2f, 0f, 4f, 2f),
            ),
            colors = listOf(ColorARGB.Red, ColorARGB.Green),
            blendMode = BlendMode.SRC,
            paint = Paint.fill(ColorARGB.White),
            transform = Matrix3x3F32.Identity,
            clip = ClipStack.DeviceRect(
                rect = RectF32.ofLTRB(4f, 6f, 14f, 15f),
                antiAlias = false,
            ),
        )

        val build = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            GPUPreparedSurfaceFrameBuilder.build(imageRequest(listOf(operation))),
        )
        val packets = build.taskList.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
        val semantics = packets.map { packet ->
            assertIs<GPUDrawSemanticPayload.SampledImage>(packet.semanticPayload)
        }

        assertEquals(listOf(0, 1), packets.map(GPUDrawPacket::commandIdValue))
        assertEquals(2, build.visualOperationCount)
        assertEquals(1, packets.map(GPUDrawPacket::scissorBoundsHash).toSet().size)
        assertTrue(packets.all { it.scissorBoundsHash != null })
        assertEquals(
            listOf(GPUPixelBounds(4, 6, 14, 15), GPUPixelBounds(4, 6, 14, 15)),
            semantics.map(GPUDrawSemanticPayload.SampledImage::scissorBounds),
        )
    }

    @Test
    fun `prepared atlas clamps target scissor and refuses invalid bounds before tasks`() {
        fun atlasWithClip(rect: RectF32) = DisplayOp.DrawAtlas(
            atlas = atlasImage("builder-atlas-scissor-total"),
            transforms = listOf(Matrix3x3F32.translation(2f, 3f)),
            texRects = listOf(RectF32.ofLTRB(0f, 0f, 2f, 2f)),
            colors = listOf(ColorARGB.Red),
            blendMode = BlendMode.SRC,
            paint = Paint.fill(ColorARGB.White),
            transform = Matrix3x3F32.Identity,
            clip = ClipStack.DeviceRect(rect, antiAlias = false),
        )

        val ready = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            GPUPreparedSurfaceFrameBuilder.build(
                imageRequest(listOf(atlasWithClip(RectF32.ofLTRB(-4f, 6f, 40f, 30f)))),
            ),
        )
        val packet = ready.taskList.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
            .single()
        val semantic = assertIs<GPUDrawSemanticPayload.SampledImage>(packet.semanticPayload)
        assertEquals(GPUPixelBounds(0, 6, 32, 24), semantic.scissorBounds)
        assertEquals(
            GPUClipCoveragePlan.Scissor(
                org.graphiks.kanvas.gpu.renderer.clips.GPUBounds(0f, 6f, 32f, 24f),
            ),
            packet.clipCoveragePlan,
        )
        assertEquals(
            GPUClipExecutionPlan.ScissorOnly(GPUPixelBounds(0, 6, 32, 24)),
            packet.clipExecutionPlan,
        )

        val invalid = listOf(
            RectF32.ofLTRB(16f, 6f, 4f, 15f),
            RectF32.ofLTRB(4f, 6f, Float.POSITIVE_INFINITY, 15f),
            RectF32.ofLTRB(40f, 6f, 50f, 15f),
        )
        invalid.forEach { rect ->
            val refused = assertIs<GPUPreparedSurfaceFrameBuildResult.Refused>(
                GPUPreparedSurfaceFrameBuilder.build(
                    imageRequest(listOf(atlasWithClip(rect))),
                ),
            )
            assertEquals("unsupported.surface.prepared.image-clip", refused.diagnostic.code.value)
        }
    }

    @Test
    fun `prepared image nine expands to nine ordered packets with one artifact upload`() {
        val image = imageNine("builder-nine")
        val operation = DisplayOp.DrawImageNine(
            image = image,
            center = RectF32.ofLTRB(2f, 2f, 4f, 4f),
            dst = RectF32.ofLTRB(2f, 3f, 26f, 21f),
            paint = null,
            transform = Matrix3x3F32.Identity,
            clip = ClipStack.WideOpen,
        )

        val buildResult = GPUPreparedSurfaceFrameBuilder.build(imageRequest(listOf(operation)))
        val ready = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            buildResult,
            buildResult.toString(),
        )
        val packets = ready.taskList.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
        val semantics = packets.map { packet ->
            assertIs<GPUDrawSemanticPayload.SampledImage>(packet.semanticPayload)
        }
        val uploads = ready.taskList.tasks.filterIsInstance<GPUTask.Upload>()
            .mapNotNull(GPUTask.Upload::imageResourcePlan)

        assertEquals((0..8).toList(), packets.map(GPUDrawPacket::commandIdValue))
        assertEquals(9, ready.visualOperationCount)
        assertTrue(semantics.all { it.sampling == GPUPreparedImageSampling.Linear })
        assertEquals(1, semantics.map { it.artifact.key }.toSet().size)
        assertEquals(1, uploads.size)
        assertEquals(semantics.first().artifact.key, uploads.single().artifactKey)
    }

    @Test
    fun `prepared image nine hard scissor applies to every expanded packet exactly`() {
        val image = imageNine("builder-nine-scissor-refusal")
        val operation = DisplayOp.DrawImageNine(
            image = image,
            center = RectF32.ofLTRB(2f, 2f, 4f, 4f),
            dst = RectF32.ofLTRB(2f, 3f, 26f, 21f),
            paint = null,
            transform = Matrix3x3F32.Identity,
            clip = ClipStack.DeviceRect(
                RectF32.ofLTRB(6f, 7f, 18f, 16f),
                antiAlias = false,
            ),
        )

        val ready = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            GPUPreparedSurfaceFrameBuilder.build(imageRequest(listOf(operation))),
        )
        val packets = ready.taskList.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
        assertEquals(9, packets.size)
        assertTrue(
            packets.all {
                it.clipCoveragePlan ==
                    GPUClipCoveragePlan.Scissor(
                        org.graphiks.kanvas.gpu.renderer.clips.GPUBounds(6f, 7f, 18f, 16f),
                    )
            },
        )
        assertTrue(
            packets.all {
                it.clipExecutionPlan ==
                    GPUClipExecutionPlan.ScissorOnly(GPUPixelBounds(6, 7, 18, 16))
            },
        )
    }

    @Test
    fun `prepared mixed lattice preserves sampled core order and omits transparent cell`() {
        val image = imageNine("builder-mixed-lattice")
        val operation = DisplayOp.DrawImageLattice(
            image = image,
            lattice = Lattice(
                xDivs = listOf(2, 4),
                yDivs = emptyList(),
                colors = listOf(
                    ColorARGB.Transparent,
                    ColorARGB.of(128, 128, 64, 32),
                    ColorARGB.Transparent,
                ),
                flags = listOf(
                    LatticeFlags.DEFAULT,
                    LatticeFlags.FIXED_COLOR,
                    LatticeFlags.TRANSPARENT,
                ),
            ),
            dst = RectF32.ofLTRB(2f, 4f, 26f, 12f),
            paint = Paint.fill(ColorARGB.of(128, 30, 40, 50)).copy(antiAlias = false),
            transform = Matrix3x3F32.translation(1f, 2f),
            clip = ClipStack.WideOpen,
            sampling = SamplingOptions.NEAREST,
        )

        val buildResult = GPUPreparedSurfaceFrameBuilder.build(imageRequest(listOf(operation)))
        val ready = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            buildResult,
            buildResult.toString(),
        )
        val packets = ready.taskList.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
        val uploads = ready.taskList.tasks.filterIsInstance<GPUTask.Upload>()
            .mapNotNull(GPUTask.Upload::imageResourcePlan)

        assertEquals(listOf(0, 1), packets.map(GPUDrawPacket::commandIdValue))
        assertIs<GPUDrawSemanticPayload.SampledImage>(packets[0].semanticPayload).also {
            assertEquals(GPUPreparedImageSampling.Nearest, it.sampling)
        }
        val fixed = assertIs<GPUDrawSemanticPayload.CorePrimitive>(packets[1].semanticPayload)
        val fixedAlpha = 64f / 255f
        assertEquals(
            srgbToLinear(128f / 255f) * fixedAlpha,
            fixed.premultipliedRgba[0],
            1e-6f,
        )
        assertEquals(
            srgbToLinear(64f / 255f) * fixedAlpha,
            fixed.premultipliedRgba[1],
            1e-6f,
        )
        assertEquals(
            srgbToLinear(32f / 255f) * fixedAlpha,
            fixed.premultipliedRgba[2],
            1e-6f,
        )
        assertEquals(fixedAlpha, fixed.premultipliedRgba[3], 1e-6f)
        assertEquals(1, uploads.size)
    }

    @Test
    fun `prepared fixed color lattice uses no image upload`() {
        val operation = DisplayOp.DrawImageLattice(
            image = imageNine("builder-fixed-lattice"),
            lattice = Lattice(
                xDivs = listOf(2),
                yDivs = emptyList(),
                colors = listOf(ColorARGB.Green, ColorARGB.Blue),
                flags = listOf(LatticeFlags.FIXED_COLOR, LatticeFlags.FIXED_COLOR),
            ),
            dst = RectF32.ofLTRB(2f, 4f, 18f, 12f),
            paint = Paint.fill(ColorARGB.White).copy(antiAlias = false),
            transform = Matrix3x3F32.Identity,
            clip = ClipStack.WideOpen,
        )

        val buildResult = GPUPreparedSurfaceFrameBuilder.build(imageRequest(listOf(operation)))
        val ready = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            buildResult,
            buildResult.toString(),
        )
        val packets = ready.taskList.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
        val uploads = ready.taskList.tasks.filterIsInstance<GPUTask.Upload>()
            .mapNotNull(GPUTask.Upload::imageResourcePlan)

        assertEquals(listOf(0, 1), packets.map(GPUDrawPacket::commandIdValue))
        assertTrue(packets.all { it.semanticPayload is GPUDrawSemanticPayload.CorePrimitive })
        assertTrue(uploads.isEmpty())
    }

    @Test
    fun `analytic antialiased rect semantic uses the recorded packet blend authority`() {
        val operation = DisplayOp.DrawRect(
            RECT,
            Paint.fill(ColorARGB.Red).copy(antiAlias = false),
            Matrix3x3F32.Identity,
            ClipStack.WideOpen,
        )

        val ready = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            GPUPreparedSurfaceFrameBuilder.build(request(listOf(operation))),
        )
        val packet = ready.taskList.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
            .single()
        val semantic = assertIs<GPUDrawSemanticPayload.CorePrimitive>(packet.semanticPayload)

        assertEquals(requireNotNull(packet.blendPlan).canonicalIdentity(), semantic.blendPlanIdentity)
    }

    @Test
    fun `hardware sRGB store admits fractional alpha and coverage authorities`() {
        val nonPrimary = ColorARGB.of(alpha = 255, red = 40, green = 120, blue = 208)
        val fractionalCases = listOf(
            "material-alpha" to
                rect(color = ColorARGB.of(alpha = 160, red = 40, green = 120, blue = 208)),
            "rect-aa" to
                rect(color = nonPrimary).copy(
                    paint = Paint.fill(nonPrimary).copy(antiAlias = true),
                ),
            "rrect-aa" to
                DisplayOp.DrawRRect(
                    RRectF32.of(RECT, radius = 2f),
                    Paint.fill(nonPrimary).copy(antiAlias = true),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            "clip-aa" to
                rect(color = nonPrimary).copy(
                    clip = ClipStack.DeviceRect(
                        RectF32.ofLTRB(1.5f, 1.5f, 14.5f, 12.5f),
                        antiAlias = true,
                    ),
                ),
        )

        fractionalCases.forEach { (label, operation) ->
            val buildRequest = request(listOf(operation))
            val ready = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
                GPUPreparedSurfaceFrameBuilder.build(buildRequest),
                label,
            )
            val renders = ready.taskList.tasks.filterIsInstance<GPUTask.Render>()
            val packets = renders.flatMap(GPUTask.Render::drawPackets)

            assertTrue(packets.isNotEmpty(), label)
            assertTrue(renders.all { it.target == buildRequest.target }, label)
            assertEquals(
                setOf("target.rgba8unorm-srgb.single-sample"),
                packets.map(GPUDrawPacket::targetStateHash).toSet(),
                label,
            )
            if (label == "material-alpha") {
                val semantic = assertIs<GPUDrawSemanticPayload.CorePrimitive>(
                    packets.single().semanticPayload,
                )
                val alpha = 160f / 255f
                assertEquals(srgbToLinear(40f / 255f) * alpha, semantic.premultipliedRgba[0], 1e-6f)
                assertEquals(srgbToLinear(120f / 255f) * alpha, semantic.premultipliedRgba[1], 1e-6f)
                assertEquals(srgbToLinear(208f / 255f) * alpha, semantic.premultipliedRgba[2], 1e-6f)
                assertEquals(alpha, semantic.premultipliedRgba[3], 1e-6f)
            }
        }

        assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            GPUPreparedSurfaceFrameBuilder.build(request(listOf(rect(color = nonPrimary)))),
        )
        assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            GPUPreparedSurfaceFrameBuilder.build(
                request(listOf(
                    rect(color = nonPrimary).copy(
                        clip = ClipStack.DeviceRect(RectF32.ofLTRB(1f, 1f, 14f, 12f), antiAlias = false),
                    ),
                )),
            ),
        )
    }

    @Test
    fun `AA path promotion reports missing multisample color capability`() {
        val nonPrimary = ColorARGB.of(alpha = 255, red = 40, green = 120, blue = 208)
        val unsupportedAaPath = assertIs<GPUPreparedSurfaceFrameBuildResult.Refused>(
            GPUPreparedSurfaceFrameBuilder.build(
                request(listOf(
                    DisplayOp.DrawPath(
                        triangle(),
                        Paint.fill(nonPrimary).copy(antiAlias = true),
                        Matrix3x3F32.Identity,
                        ClipStack.WideOpen,
                    ),
                )),
            ),
        )
        assertEquals(
            "unsupported.core_primitive.coverage_sample.color_capability",
            unsupportedAaPath.diagnostic.code.value,
        )
    }

    @Test
    fun `public frame accepts uniformly scaled non-AA FillPath in native stencil cover`() {
        val operation = DisplayOp.DrawPath(
            Path().apply {
                moveTo(8f, 8f)
                lineTo(40f, 8f)
                lineTo(8f, 40f)
                close()
            },
            Paint.fill(ColorARGB.Blue).copy(antiAlias = false),
            Matrix3x3F32(sx = 1.5f, sy = 1.5f),
            ClipStack.WideOpen,
        )
        val ready = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            GPUPreparedSurfaceFrameBuilder.build(
                request(listOf(operation)).copy(
                    targetFacts = GPUTargetFacts(64, 64, "rgba8unorm-srgb"),
                    targetBounds = GPUPixelBounds(0, 0, 64, 64),
                ),
            ),
        )
        val packets = ready.taskList.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
        assertTrue(ready.taskList.tasks.none { it is GPUTask.Refused })
        assertTrue(packets.isNotEmpty())
    }

    @Test
    fun `image path inventory candidate requires identity transform`() {
        val image = atlasImage("transformed-image-path")
        val operation = DisplayOp.DrawPath(
            Path().addRect(RectF32.ofLTRB(1f, 1f, 3f, 3f)),
            Paint(shader = Shader.Image(image)).copy(antiAlias = false),
            Matrix3x3F32.translation(4f, 2f),
            ClipStack.WideOpen,
        )

        assertFalse(operation.isPreparedImageVisualCandidate())
        assertTrue(operation.copy(transform = Matrix3x3F32.Identity).isPreparedImageVisualCandidate())
    }

    private fun srgbToLinear(encoded: Float): Float = if (encoded <= 0.04045f) {
        encoded / 12.92f
    } else {
        ((encoded + 0.055f) / 1.055f).pow(2.4f)
    }

    @Test
    fun `rect src over with state events preserves exact frame envelope ids provenance and counts`() {
        val operations = listOf(
            DisplayOp.SetTransform(Matrix3x3F32.translation(2f, 3f)),
            DisplayOp.SetClip(ClipStack.WideOpen),
            DisplayOp.Annotation(
                RECT,
                GPU_FRAME_PROVENANCE_ANNOTATION_KEY,
                org.graphiks.kanvas.gpu.renderer.commands.GPUFrameProvenance.GmContent.annotationValue,
            ),
            rect(),
        )
        val request = request(operations)

        val ready = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            GPUPreparedSurfaceFrameBuilder.build(request),
        )

        assertEquals(1, ready.visualOperationCount)
        assertEquals(3, ready.stateEventCount)
        assertEquals(request.frameId, ready.taskList.frameId)
        assertEquals(request.deviceGeneration, ready.taskList.capabilitySeal.deviceGeneration)
        assertEquals(
            request.recordingId,
            ready.taskList.recordingSeals.single().recordingId,
        )
        val preparations = ready.taskList.tasks.filterIsInstance<GPUTask.PrepareResources>()
            .flatMap(GPUTask.PrepareResources::requests)
        val sceneTarget = preparations.single { it.role == GPUFrameResourceRole.SceneTarget }
        assertEquals(request.target, sceneTarget.resource)
        assertEquals(1, preparations.count { it.role == GPUFrameResourceRole.SceneTarget })
        val renders = ready.taskList.tasks.filterIsInstance<GPUTask.Render>()
        assertTrue(renders.isNotEmpty())
        assertTrue(renders.all { it.target == request.target })
        val semantic = assertIs<GPUDrawSemanticPayload.CorePrimitive>(
            renders.flatMap(GPUTask.Render::drawPackets).single().semanticPayload,
        )
        assertEquals(
            org.graphiks.kanvas.gpu.renderer.commands.GPUFrameProvenance.GmContent,
            semantic.frameProvenance,
        )
        val readback = assertIs<GPUTask.Readback>(ready.taskList.tasks.last())
        assertEquals(request.readbackRequestId, readback.request.requestId)
        assertEquals(request.target, readback.source)
        val terminal = ready.taskList.dependencies.last()
        assertEquals(renders.last().taskId, terminal.fromTaskId)
        assertEquals(readback.taskId, terminal.toTaskId)
    }

    @Test
    fun `runtime-only path stencil with scissor preserves two packet roles and exact requested target`() {
        val path = triangle()
        val clippedPath = DisplayOp.DrawPath(
            path,
            Paint.fill(ColorARGB.Blue).copy(antiAlias = false),
            Matrix3x3F32.Identity,
            ClipStack.DeviceRect(RectF32.ofLTRB(2f, 3f, 20f, 18f), antiAlias = false),
        )
        val request = request(listOf(clippedPath), capabilities = capabilities(pathPrepared = false))

        val ready = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            GPUPreparedSurfaceFrameBuilder.build(request),
        )
        val render = ready.taskList.tasks.filterIsInstance<GPUTask.Render>().single()

        assertEquals(request.target, render.target)
        assertEquals(
            listOf(GPUDrawPacketRole.PathStencilProducer, GPUDrawPacketRole.PathStencilCover),
            render.drawPackets.map { it.role },
        )
        assertEquals(listOf(0, 0), render.drawPackets.map { it.commandIdValue })
        assertEquals(1, ready.visualOperationCount)
        assertEquals(0, ready.stateEventCount)
    }

    @Test
    fun `mixed direct path direct frame records every visual once in source order`() {
        val operations = listOf(
            rect(RectF32.ofLTRB(1f, 1f, 7f, 7f), ColorARGB.Red),
            DisplayOp.DrawPath(
                triangle(),
                Paint.fill(ColorARGB.Green).copy(antiAlias = false),
                Matrix3x3F32.Identity,
                ClipStack.WideOpen,
            ),
            rect(RectF32.ofLTRB(20f, 12f, 28f, 20f), ColorARGB.Blue),
        )

        val ready = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            GPUPreparedSurfaceFrameBuilder.build(request(operations)),
        )
        val packets = ready.taskList.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)

        assertEquals(listOf(0, 1, 1, 2), packets.map { it.commandIdValue })
        assertEquals(3, ready.visualOperationCount)
        assertEquals(0, ready.stateEventCount)
        assertTrue(packets.all { it.semanticPayload is GPUDrawSemanticPayload.CorePrimitive })
    }

    @Test
    fun `bounded radial and sweep Canvas materials build CorePrimitive frames when facts are present`() {
        val stops = listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue))
        val operations = listOf(
            DisplayOp.DrawRect(
                RectF32.ofLTRB(2f, 2f, 14f, 14f),
                Paint(shader = Shader.RadialGradient(Point2F32(8f, 8f), 8f, stops)).copy(antiAlias = false),
                Matrix3x3F32.Identity,
                ClipStack.WideOpen,
            ),
            DisplayOp.DrawRect(
                RectF32.ofLTRB(16f, 2f, 30f, 14f),
                Paint(shader = Shader.SweepGradient(Point2F32(23f, 8f), stops = stops)).copy(antiAlias = false),
                Matrix3x3F32.Identity,
                ClipStack.WideOpen,
            ),
        )

        val ready = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            GPUPreparedSurfaceFrameBuilder.build(request(operations)),
        )
        val materials = ready.taskList.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
            .map { assertIs<GPUDrawSemanticPayload.CorePrimitive>(it.semanticPayload).material }

        assertTrue(materials.any { it is org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveMaterialPayload.RadialGradient })
        assertTrue(materials.any { it is org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveMaterialPayload.SweepGradient })
    }

    @Test
    fun `refusal matrix preserves true diagnostics while sRGB translucent solid and bounded linear are ready`() {
        val gradient = Shader.LinearGradient(
            Point2F32(0f, 0f),
            Point2F32(8f, 8f),
            listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
        )
        val complexClip = ClipStack.Complex(
            listOf(
                org.graphiks.kanvas.canvas.ClipStackOp.RectOp(
                    RectF32.ofLTRB(1f, 1f, 24f, 20f), ClipOp.INTERSECT, antiAlias = true,
                ),
                org.graphiks.kanvas.canvas.ClipStackOp.RectOp(
                    RectF32.ofLTRB(4f, 4f, 8f, 8f), ClipOp.DIFFERENCE, antiAlias = true,
                ),
            ),
        )
        val translucentRequest =
            request(listOf(rect(color = ColorARGB.of(alpha = 160, red = 40, green = 120, blue = 208))))
        val translucentReady = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            GPUPreparedSurfaceFrameBuilder.build(translucentRequest),
        )
        val translucentPackets = translucentReady.taskList.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
        assertTrue(translucentPackets.isNotEmpty())
        assertEquals(
            setOf("target.rgba8unorm-srgb.single-sample"),
            translucentPackets.map(GPUDrawPacket::targetStateHash).toSet(),
        )

        val linearReady = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            GPUPreparedSurfaceFrameBuilder.build(
                request(listOf(rect().copy(paint = Paint.fill(ColorARGB.White).copy(shader = gradient)))),
            ),
        )
        val linearMaterial = linearReady.taskList.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
            .map { assertIs<GPUDrawSemanticPayload.CorePrimitive>(it.semanticPayload).material }
            .single()
        assertIs<org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveMaterialPayload.LinearGradient>(
            linearMaterial,
        )

        val squareLine = request(listOf(DisplayOp.DrawPoints(
                PointMode.LINES,
                listOf(Point2F32(2f, 2f), Point2F32(12f, 2f)),
                Paint.stroke(ColorARGB.Red, 2f).copy(strokeCap = StrokeCap.SQUARE, antiAlias = false),
                Matrix3x3F32.Identity,
                ClipStack.WideOpen,
            )))
        assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(GPUPreparedSurfaceFrameBuilder.build(squareLine))

        val cases = listOf(
            request(listOf(rect().copy(clip = complexClip)), capabilities = capabilities(boundedClip = false)) to
                "unsupported.clip.mask_unavailable",
            request(listOf(rect()), capabilities = capabilities(fillRect = false)) to
                "unsupported.pipeline.capability_missing",
        )

        cases.forEach { (request, expectedCode) ->
            val refused = assertIs<GPUPreparedSurfaceFrameBuildResult.Refused>(
                GPUPreparedSurfaceFrameBuilder.build(request),
                expectedCode,
            )
            assertEquals(expectedCode, refused.diagnostic.code.value)
        }
    }

    @Test
    fun `hard rect and aa clear rect split into two direct passes with per layout slabs`() {
        // The CLEAR fixture rect overrides the hard rect() paint with
        // Paint.fill(...), whose antiAlias defaults to true, so this frame mixes a hard
        // uniform32 rect with an analytic-shape uniform80 AA rect. The direct pass splits by
        // uniform layout: one render per layout group, each with its own slab. The single hard
        // CLEAR rect frame still builds Ready as one pass (pinned by the `clear and src hard
        // rects build ready` test).
        val ready = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            GPUPreparedSurfaceFrameBuilder.build(
                request(listOf(
                    rect(color = ColorARGB.Blue),
                    rect(color = ColorARGB.Red).copy(
                        paint = Paint.fill(ColorARGB.Red).copy(blendMode = BlendMode.CLEAR),
                    ),
                )),
            ),
        )
        val renders = ready.taskList.tasks.filterIsInstance<GPUTask.Render>()
        assertEquals(2, renders.size)
        assertEquals(1, renders[0].drawPackets.size)
        assertEquals(1, renders[1].drawPackets.size)
        assertEquals(
            GPULoadStorePlan("clear", GPUStorePlan.Store),
            renders[0].loadStore,
        )
        assertEquals(
            GPULoadStorePlan("load", GPUStorePlan.Store),
            renders[1].loadStore,
        )
        val preparations = ready.taskList.tasks.filterIsInstance<GPUTask.PrepareResources>()
            .flatMap(GPUTask.PrepareResources::requests)
        assertTrue(preparations.any { it.diagnosticLabel == "core-primitive.uniforms" })
        assertTrue(preparations.any { it.diagnosticLabel == "core-primitive.analytic-shape-uniforms" })
        assertTrue(
            ready.taskList.dependencies.any {
                it.fromTaskId == renders[0].taskId && it.toTaskId == renders[1].taskId
            },
        )
    }

    @Test
    fun `two analytic rects with mixed blend modes route the clear consumer through the dst read formula`() {
        // Both rects use Paint.fill(...) defaults (antiAlias = true), so they share the analytic
        // shape uniform80 layout. CLEAR cannot be expressed by the coverage-modulating analytic
        // shader (geometric AA interpolation), so it routes through the dst-read formula:
        // the frame keeps the SRC_OVER destination packet and orders a
        // destination snapshot for the CLEAR consumer instead of sealing one mixed fixed-function
        // multi-key pass.
        val mixed = request(listOf(
            rect().copy(paint = Paint.fill(ColorARGB.Red).copy(blendMode = BlendMode.SRC_OVER)),
            rect().copy(paint = Paint.fill(ColorARGB.Red).copy(blendMode = BlendMode.CLEAR)),
        ))

        val ready = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            GPUPreparedSurfaceFrameBuilder.build(mixed),
        )
        val packets = ready.taskList.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
        assertEquals(2, packets.size)
        assertTrue(
            packets.any { (it.blendPlan as? GPUBlendPlan.FixedFunctionBlend)?.mode == GPUBlendMode.SRC_OVER },
        )
        val clear = packets.mapNotNull { packet -> packet.blendPlan as? GPUBlendPlan.ShaderBlendWithDstRead }
            .single { it.mode == GPUBlendMode.CLEAR }
        assertEquals("clear@v1", clear.formulaId)
        assertTrue(ready.taskList.tasks.any { it is GPUTask.DestinationSnapshots })
    }

    @Test
    fun `clear and src hard rects build ready with fixed function blend packets`() {
        val clear = request(listOf(rect().copy(
            paint = Paint.fill(ColorARGB.Red).copy(antiAlias = false, blendMode = BlendMode.CLEAR),
        )))
        val clearResult = GPUPreparedSurfaceFrameBuilder.build(clear)
        val clearReady = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            clearResult,
            (clearResult as? GPUPreparedSurfaceFrameBuildResult.Refused)
                ?.let { "${it.diagnostic.code.value}: ${it.diagnostic.message}" }.orEmpty(),
        )
        val clearBlends = clearReady.taskList.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
            .mapNotNull { (it.blendPlan as? GPUBlendPlan.FixedFunctionBlend)?.mode }
        assertTrue(GPUBlendMode.CLEAR in clearBlends)

        val src = request(listOf(rect().copy(
            paint = Paint.fill(ColorARGB.Red).copy(antiAlias = false, blendMode = BlendMode.SRC),
        )))
        val srcResult = GPUPreparedSurfaceFrameBuilder.build(src)
        val srcReady = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            srcResult,
            (srcResult as? GPUPreparedSurfaceFrameBuildResult.Refused)
                ?.let { "${it.diagnostic.code.value}: ${it.diagnostic.message}" }.orEmpty(),
        )
        val srcBlends = srcReady.taskList.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
            .mapNotNull { (it.blendPlan as? GPUBlendPlan.FixedFunctionBlend)?.mode }
        assertTrue(GPUBlendMode.SRC in srcBlends)
    }

    @Test
    fun `scalar src rect routes through the dst read formula with a destination snapshot`() {
        // Paint.fill defaults antiAlias=true, so this is the scalar-coverage SRC rect. SRC cannot
        // be expressed by the coverage-modulating analytic shader (geometric AA interpolation), so
        // it routes through the dst-read formula, mirroring the MULTIPLY case below.
        val result = GPUPreparedSurfaceFrameBuilder.build(
            request(listOf(rect().copy(paint = Paint.fill(ColorARGB.Red).copy(blendMode = BlendMode.SRC)))),
        )
        val ready = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            result,
            (result as? GPUPreparedSurfaceFrameBuildResult.Refused)
                ?.let { "${it.diagnostic.code.value}: ${it.diagnostic.message}" }.orEmpty(),
        )
        val snapshotTasks = ready.taskList.tasks.filterIsInstance<GPUTask.DestinationSnapshots>()
        assertEquals(1, snapshotTasks.size)
        val packets = ready.taskList.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
        val blend = packets.mapNotNull { it.blendPlan as? GPUBlendPlan.ShaderBlendWithDstRead }.single()
        assertEquals(GPUBlendMode.SRC, blend.mode)
        assertEquals("src@v1", blend.formulaId)
    }

    @Test
    fun `multiply rect builds ready with destination snapshot and shader with destination blend`() {
        val result = GPUPreparedSurfaceFrameBuilder.build(
            request(listOf(rect().copy(paint = Paint.fill(ColorARGB.Red).copy(blendMode = BlendMode.MULTIPLY)))),
        )
        val ready = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            result,
            (result as? GPUPreparedSurfaceFrameBuildResult.Refused)
                ?.let { "${it.diagnostic.code.value}: ${it.diagnostic.message}" }.orEmpty(),
        )
        val snapshotTasks = ready.taskList.tasks.filterIsInstance<GPUTask.DestinationSnapshots>()
        assertEquals(1, snapshotTasks.size)
        val packets = ready.taskList.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
        val blend = packets.mapNotNull { it.blendPlan as? GPUBlendPlan.ShaderBlendWithDstRead }.single()
        assertEquals("multiply", blend.mode.gpuLabel)
        assertEquals("multiply@v1", blend.formulaId)
        assertEquals(1, ready.destinationReadEvidence.size)
        assertEquals(listOf("multiply"), ready.destinationReadEvidence.map { it.modeLabel })
    }

    @Test
    fun `readback capability refusal is propagated unchanged from the prepared planner`() {
        val withoutReadback = capabilities(readback = false)

        val refused = assertIs<GPUPreparedSurfaceFrameBuildResult.Refused>(
            GPUPreparedSurfaceFrameBuilder.build(
                request(listOf(rect()), capabilities = withoutReadback),
            ),
        )

        assertEquals("unsupported.readback.capability_unavailable", refused.diagnostic.code.value)
        assertEquals(GPUDiagnosticDomain.Execution, refused.diagnostic.domain)
        assertEquals(GPUDiagnosticSeverity.Error, refused.diagnostic.severity)
        assertEquals(
            "The selected capability snapshot does not expose renderer readback.",
            refused.diagnostic.message,
        )
        assertEquals(mapOf("rendererFeatures" to "render-pass"), refused.diagnostic.facts)
    }

    @Test
    fun `target bounds format and ambiguous ids refuse before mapping`() {
        val base = request(listOf(rect()))
        val cases = listOf(
            base.copy(targetBounds = GPUPixelBounds(1, 0, 33, 24)) to
                "invalid.surface.prepared.target-bounds",
            base.copy(targetFacts = GPUTargetFacts(31, 24, "rgba8unorm-srgb")) to
                "invalid.surface.prepared.target-bounds",
            base.copy(targetFacts = GPUTargetFacts(32, 24, "bgra8unorm")) to
                "invalid.surface.prepared.target-format",
            base.copy(target = GPUFrameTargetRef(base.readbackRequestId.value)) to
                "invalid.surface.prepared.frame-identities",
        )

        cases.forEach { (request, expectedCode) ->
            val refused = assertIs<GPUPreparedSurfaceFrameBuildResult.Refused>(
                GPUPreparedSurfaceFrameBuilder.build(request),
            )
            assertEquals(expectedCode, refused.diagnostic.code.value)
        }
    }

    @Test
    fun `same image object with repeated source id keeps two command packets and one immutable artifact`() {
        val image = Image(
            width = 2,
            height = 1,
            sourceId = "repeated-source",
            pixels = byteArrayOf(1, 2, 3, -1, 4, 5, 6, -1),
            alphaType = AlphaType.PREMUL,
        )
        val operations = listOf(
            drawImage(image, RectF32.ofLTRB(1f, 1f, 5f, 3f)),
            drawImage(image, RectF32.ofLTRB(7f, 2f, 15f, 6f)),
        )

        val buildResult = GPUPreparedSurfaceFrameBuilder.build(imageRequest(operations))
        val ready = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            buildResult,
            buildResult.toString(),
        )
        val packets = ready.taskList.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
        val artifacts = ready.taskList.tasks.filterIsInstance<GPUTask.Upload>()
            .mapNotNull(GPUTask.Upload::imageResourcePlan)

        assertEquals(2, packets.size)
        assertEquals(1, artifacts.size)
        assertNotEquals(
            assertIs<GPUDrawSemanticPayload.SampledImage>(packets[0].semanticPayload).canonicalHash,
            assertIs<GPUDrawSemanticPayload.SampledImage>(packets[1].semanticPayload).canonicalHash,
        )
    }

    @Test
    fun `distinct images with equal source id keep two artifact keys and exact command association`() {
        val first = Image(
            width = 1,
            height = 1,
            sourceId = "shared-provenance",
            pixels = byteArrayOf(1, 2, 3, -1),
            alphaType = AlphaType.PREMUL,
        )
        val second = Image(
            width = 1,
            height = 1,
            sourceId = "shared-provenance",
            pixels = byteArrayOf(7, 8, 9, -1),
            alphaType = AlphaType.PREMUL,
        )
        val operations = listOf(
            drawImage(first, RectF32.ofLTRB(1f, 1f, 5f, 3f)),
            drawImage(second, RectF32.ofLTRB(6f, 1f, 8f, 5f)),
        )

        val buildResult = GPUPreparedSurfaceFrameBuilder.build(imageRequest(operations))
        val ready = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            buildResult,
            buildResult.toString(),
        )
        val packets = ready.taskList.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
        val semantics = packets.map { packet ->
            assertIs<GPUDrawSemanticPayload.SampledImage>(packet.semanticPayload)
        }
        val artifacts = ready.taskList.tasks.filterIsInstance<GPUTask.Upload>()
            .mapNotNull(GPUTask.Upload::imageResourcePlan)
            .map { plan -> plan.bindingRequests.single().artifactKey }

        assertEquals(2, packets.size)
        assertEquals(2, artifacts.toSet().size)
        assertContentEquals(first.pixels, semantics[0].artifact.tightRgba8BytesForUpload())
        assertContentEquals(second.pixels, semantics[1].artifact.tightRgba8BytesForUpload())
    }

    @Test
    fun `blank source provenance keeps exact image bytes by command and operation index`() {
        val first = Image(
            width = 1,
            height = 1,
            sourceId = "",
            pixels = byteArrayOf(11, 12, 13, -1),
            alphaType = AlphaType.PREMUL,
        )
        val second = Image(
            width = 1,
            height = 1,
            sourceId = "",
            pixels = byteArrayOf(21, 22, 23, -1),
            alphaType = AlphaType.PREMUL,
        )

        val buildResult = GPUPreparedSurfaceFrameBuilder.build(
            imageRequest(
                listOf(
                    drawImage(first, RectF32.ofLTRB(1f, 1f, 5f, 5f)),
                    drawImage(second, RectF32.ofLTRB(7f, 1f, 11f, 5f)),
                ),
            ),
        )
        val ready = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            buildResult,
            buildResult.toString(),
        )
        val semantics = ready.taskList.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
            .map { packet -> assertIs<GPUDrawSemanticPayload.SampledImage>(packet.semanticPayload) }

        assertEquals(listOf(0, 1), ready.taskList.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
            .map { packet -> packet.commandIdValue })
        assertContentEquals(first.pixels, semantics[0].artifact.tightRgba8BytesForUpload())
        assertContentEquals(second.pixels, semantics[1].artifact.tightRgba8BytesForUpload())
        assertNotEquals(semantics[0].artifact.key, semantics[1].artifact.key)
    }

    @Test
    fun `core image core image preserves exact paint order and artifact association`() {
        val first = Image(
            width = 1,
            height = 1,
            sourceId = "mixed-shared-provenance",
            pixels = byteArrayOf(31, 32, 33, -1),
            alphaType = AlphaType.PREMUL,
        )
        val second = Image(
            width = 1,
            height = 1,
            sourceId = "mixed-shared-provenance",
            pixels = byteArrayOf(41, 42, 43, -1),
            alphaType = AlphaType.PREMUL,
        )
        val operations: List<DisplayOp> = listOf(
            rect(RectF32.ofLTRB(1f, 1f, 5f, 5f), ColorARGB.Red),
            drawImage(first, RectF32.ofLTRB(6f, 1f, 10f, 5f)),
            rect(RectF32.ofLTRB(11f, 1f, 15f, 5f), ColorARGB.Blue),
            drawImage(second, RectF32.ofLTRB(16f, 1f, 20f, 5f)),
        )

        val buildResult = GPUPreparedSurfaceFrameBuilder.build(imageRequest(operations))
        val ready = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            buildResult,
            buildResult.toString(),
        )
        val packets = ready.taskList.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
        val imageSemantics = packets.mapNotNull { packet ->
            packet.semanticPayload as? GPUDrawSemanticPayload.SampledImage
        }
        val uploadPlans = ready.taskList.tasks.filterIsInstance<GPUTask.Upload>()
            .mapNotNull(GPUTask.Upload::imageResourcePlan)

        assertEquals(listOf(0, 1, 2, 3), packets.map { packet -> packet.commandIdValue })
        assertEquals(4, packets.size)
        assertEquals(4, packets.map { packet -> packet.commandIdValue }.toSet().size)
        assertIs<GPUDrawSemanticPayload.CorePrimitive>(packets[0].semanticPayload)
        assertIs<GPUDrawSemanticPayload.SampledImage>(packets[1].semanticPayload)
        assertIs<GPUDrawSemanticPayload.CorePrimitive>(packets[2].semanticPayload)
        assertIs<GPUDrawSemanticPayload.SampledImage>(packets[3].semanticPayload)
        assertContentEquals(first.pixels, imageSemantics[0].artifact.tightRgba8BytesForUpload())
        assertContentEquals(second.pixels, imageSemantics[1].artifact.tightRgba8BytesForUpload())
        assertEquals(
            imageSemantics.map { semantic -> semantic.artifact.key }.toSet(),
            uploadPlans.map { plan -> plan.artifactKey }.toSet(),
        )
        assertEquals(
            imageSemantics.map { semantic -> semantic.artifact.key }.toSet().size,
            uploadPlans.size,
        )
    }

    @Test
    fun `surface propagates remaining canonical image refusal codes and adds only boundary facts`() {
        val cases = listOf(
            Image(
                1,
                1,
                ColorType.RGBA_8888,
                "missing-pixels",
                pixels = null,
                alphaType = AlphaType.PREMUL,
            ) to GPUPreparedImageRefusalCodes.PIXELS_MISSING,
            Image(
                1,
                1,
                ColorType.RGB_888X,
                "unsupported-format",
                byteArrayOf(1, 1, 1, 0),
                alphaType = AlphaType.OPAQUE,
            ) to GPUPreparedImageRefusalCodes.PIXEL_FORMAT,
        )

        cases.forEach { (image, expectedCode) ->
            val refused = assertIs<GPUPreparedSurfaceFrameBuildResult.Refused>(
                GPUPreparedSurfaceFrameBuilder.build(
                    imageRequest(listOf(drawImage(image, RectF32.ofLTRB(1f, 1f, 5f, 5f)))),
                ),
            )

            assertEquals(expectedCode, refused.diagnostic.code.value)
            assertEquals("surface", refused.diagnostic.facts["boundary"])
            assertEquals("0", refused.diagnostic.facts["commandId"])
            assertEquals("0", refused.diagnostic.facts["operationIndex"])
            assertTrue(!refused.diagnostic.code.value.startsWith("unsupported.surface.prepared.image-source."))
        }
    }

    @Test
    fun `surface accepts a valid unpremultiplied image`() {
        val image = Image(
            width = 1,
            height = 1,
            colorType = ColorType.RGBA_8888,
            sourceId = "unpremultiplied",
            pixels = byteArrayOf(1, 2, 3, 4),
            alphaType = AlphaType.UNPREMUL,
        )

        val result = GPUPreparedSurfaceFrameBuilder.build(
            imageRequest(listOf(drawImage(image, RectF32.ofLTRB(1f, 1f, 5f, 5f)))),
        )
        val ready = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(result, result.toString())

        assertEquals(1, ready.visualOperationCount)
        assertTrue(ready.taskList.tasks.any { it is GPUTask.Render })
    }

    @Test
    fun `direct builder propagates DrawImage lowerer refusals transactionally`() {
        val image = Image(
            width = GPUPreparedImageTestFixtures.rgbaPremul2x2Width,
            height = GPUPreparedImageTestFixtures.rgbaPremul2x2Height,
            colorType = GPUPreparedImageTestFixtures.rgbaPremul2x2ColorType,
            sourceId = "builder-lowerer-refusal",
            pixels = GPUPreparedImageTestFixtures.rgbaPremul2x2Bytes,
            alphaType = AlphaType.PREMUL,
        )
        val base = drawImage(image, RectF32.ofLTRB(1f, 1f, 5f, 5f))
        val cases = listOf(
            base.copy(
                paint = Paint.fill(ColorARGB.White).copy(
                    shader = Shader.Image(image, sampling = SamplingOptions.Cubic.Mitchell),
                ),
            ) to GPUPreparedImageRefusalCodes.SAMPLING_CUBIC,
            base.copy(
                transform = Matrix3x3F32.of(
                    1f, 0f, 0f,
                    0f, 1f, 0f,
                    0.001f, 0f, 1f,
                ),
            ) to GPUPreparedImageRefusalCodes.PERSPECTIVE_SAMPLING,
            base.copy(
                transform = Matrix3x3F32.of(
                    0f, 0f, 0f,
                    0f, 0f, 0f,
                ),
            ) to GPUPreparedImageRefusalCodes.PERSPECTIVE_SAMPLING,
            base.copy(
                paint = Paint.fill(ColorARGB.White).copy(blendMode = BlendMode.MULTIPLY),
            ) to GPUPreparedImageRefusalCodes.NATIVE_BINDING,
        )

        cases.forEach { (operation, expectedCode) ->
            val refused = assertIs<GPUPreparedSurfaceFrameBuildResult.Refused>(
                GPUPreparedSurfaceFrameBuilder.build(imageRequest(listOf(operation))),
            )
            assertEquals(expectedCode, refused.diagnostic.code.value)
            assertEquals("surface", refused.diagnostic.facts["boundary"])
            assertEquals("0", refused.diagnostic.facts["operationIndex"])
        }
    }

    @Test
    fun `unexpected construction exception records its class and message in stable contract refusal facts`() {
        val base = request(listOf(rect()))
        val unstableOperations = object : AbstractList<DisplayOp>() {
            override val size: Int = 1

            override fun get(index: Int): DisplayOp =
                throw IllegalStateException("runtime-specific detail must not escape")
        }

        val refused = assertIs<GPUPreparedSurfaceFrameBuildResult.Refused>(
            GPUPreparedSurfaceFrameBuilder.build(
                base.copy(candidate = base.candidate.copy(operations = unstableOperations)),
            ),
        )

        assertEquals("invalid.surface.prepared.frame-build-contract", refused.diagnostic.code.value)
        assertEquals(
            mapOf(
                "failureClass" to IllegalStateException::class.java.name,
                "failureMessage" to "runtime-specific detail must not escape",
            ),
            refused.diagnostic.facts,
        )
        assertEquals(
            "Prepared Surface frame construction violated an internal contract.",
            refused.diagnostic.message,
        )
    }

    @Test
    fun `typed construction failure preserves the original diagnostic`() {
        val base = request(listOf(rect()))
        val underlying = GPUDiagnostic(
            code = GPUDiagnosticCode("invalid.test.prepared-frame-build"),
            domain = GPUDiagnosticDomain.Pipelines,
            severity = GPUDiagnosticSeverity.Error,
            message = "The prepared pipeline key did not match the frame layout.",
            facts = mapOf(
                "pipelineKey" to "solid:clip-mask",
                "layout" to "dynamic-uniforms",
            ),
        )
        val unstableOperations = object : AbstractList<DisplayOp>() {
            override val size: Int = 1

            override fun get(index: Int): DisplayOp =
                throw GPUPreparedSurfaceTerminalException(underlying)
        }

        val refused = assertIs<GPUPreparedSurfaceFrameBuildResult.Refused>(
            GPUPreparedSurfaceFrameBuilder.build(
                base.copy(candidate = base.candidate.copy(operations = unstableOperations)),
            ),
        )

        assertEquals(underlying, refused.diagnostic)
    }

    @Test
    fun `production builder has no coordinator backend native or inventory interaction`() {
        val source = File(
            "src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameBuilder.kt",
        ).readText()

        listOf(
            "GPUFrameCoordinator",
            "GPUBackend",
            "nativeHandle",
            "GPUFramePathApiInventory",
            "GPUFramePlanner",
        ).forEach { forbidden ->
            assertTrue(forbidden !in source, forbidden)
        }
    }

    private fun request(
        operations: List<DisplayOp>,
        capabilities: GPUCapabilities = capabilities(),
    ): GPUPreparedSurfaceFrameBuildRequest {
        val candidate = assertIs<GPUPreparedSurfaceEligibility.Candidate>(
            GPUPreparedSurfaceFrameGate.classify(operations, RenderConfig.DEFAULT),
        )
        return GPUPreparedSurfaceFrameBuildRequest(
            candidate = candidate,
            targetFacts = GPUTargetFacts(32, 24, "rgba8unorm-srgb"),
            targetBounds = GPUPixelBounds(0, 0, 32, 24),
            capabilities = capabilities,
            deviceGeneration = GPUDeviceGenerationID(11),
            target = GPUFrameTargetRef("surface-frame-target"),
            recordingId = GPURecordingID("surface-frame-recording"),
            frameId = GPUFrameID(77),
            readbackRequestId = GPUReadbackRequestID("surface-frame-readback"),
        )
    }

    private fun imageRequest(operations: List<DisplayOp>): GPUPreparedSurfaceFrameBuildRequest {
        val base = request(listOf(rect()))
        return base.copy(
            candidate = GPUPreparedSurfaceEligibility.Candidate(
                operations = operations,
                config = base.candidate.config,
                color = base.candidate.color,
            ),
            capabilities = GPUCapabilities(
                implementation = base.capabilities.implementation,
                facts = base.capabilities.facts.filterNot { fact ->
                    fact.name == "first_slice.bitmap_rect.native"
                },
                knownUnsupportedFacts = base.capabilities.knownUnsupportedFacts,
                snapshotId = "${base.capabilities.snapshotId}:prepared-image",
                limits = base.capabilities.limits,
                textureFormatSampleSupport = base.capabilities.textureFormatSampleSupport,
                rendererFeatures = base.capabilities.rendererFeatures,
                copyAsDrawCapability = base.capabilities.copyAsDrawCapability,
            ),
        )
    }

    private fun drawImage(image: Image, dst: RectF32): DisplayOp.DrawImage = DisplayOp.DrawImage(
        image = image,
        src = RectF32.ofLTRB(0f, 0f, image.width.toFloat(), image.height.toFloat()),
        dst = dst,
        paint = null,
        transform = Matrix3x3F32.Identity,
        clip = ClipStack.WideOpen,
    )

    private fun imageNine(sourceId: String): Image = Image(
        width = GPUPreparedImageTestFixtures.imageNine6x6Width,
        height = GPUPreparedImageTestFixtures.imageNine6x6Height,
        colorType = GPUPreparedImageTestFixtures.imageNine6x6ColorType,
        sourceId = sourceId,
        pixels = GPUPreparedImageTestFixtures.imageNine6x6Bytes,
        alphaType = AlphaType.PREMUL,
    )

    private fun atlasImage(sourceId: String): Image = Image(
        width = GPUPreparedImageTestFixtures.atlas4x4Width,
        height = GPUPreparedImageTestFixtures.atlas4x4Height,
        colorType = GPUPreparedImageTestFixtures.atlas4x4ColorType,
        sourceId = sourceId,
        pixels = GPUPreparedImageTestFixtures.atlas4x4Bytes,
        alphaType = AlphaType.PREMUL,
    )

    private fun capabilities(
        fillRect: Boolean = true,
        boundedClip: Boolean = true,
        readback: Boolean = true,
        pathPrepared: Boolean = true,
    ): GPUCapabilities {
        val base = GPUProductFlagConfig(boundedClipEnabled = boundedClip).buildCapabilities()
        val baseFacts = base.facts.filterNot { fact ->
            !pathPrepared && fact.name == "first_slice.path_fill.native"
        }
        val extra = buildList {
            if (fillRect) add(capability("first_slice.fill_rect.native"))
            add(capability(PATH_FILL_STENCIL_COVER))
        }
        return GPUCapabilities(
            implementation = base.implementation,
            facts = baseFacts + extra,
            knownUnsupportedFacts = base.knownUnsupportedFacts,
            snapshotId =
                "${base.snapshotId}:prepared-surface-builder-test:" +
                    "$fillRect:$boundedClip:$readback:$pathPrepared",
            limits = GPULimits(
                maxTextureDimension2D = 8192,
                copyBytesPerRowAlignment = 256,
                minUniformBufferOffsetAlignment = 256,
                maxBufferSize = 1L shl 30,
                maxDynamicUniformBuffersPerPipelineLayout = 1,
            ),
            rendererFeatures = buildSet {
                add(GPURendererFeature.RenderPass)
                if (readback) add(GPURendererFeature.Readback)
            },
        )
    }

    private fun capabilitiesWithLinearFact(): GPUCapabilities {
        val base = capabilities()
        return GPUCapabilities(
            implementation = base.implementation,
            facts = base.facts.filterNot { it.name == "first_slice.linear_gradient.native" } +
                capability("first_slice.linear_gradient.native"),
            knownUnsupportedFacts = base.knownUnsupportedFacts,
            snapshotId = "${base.snapshotId}:linear-fact-injected",
            limits = base.limits,
            textureFormatSampleSupport = base.textureFormatSampleSupport,
            rendererFeatures = base.rendererFeatures,
            copyAsDrawCapability = base.copyAsDrawCapability,
        )
    }

    private fun capability(name: String) = GPUCapabilityFact(
        name = name,
        source = "test",
        value = "supported",
        affectsValidity = true,
        evidenceLabel = "test:$name",
    )

    private fun rect(
        bounds: RectF32 = RECT,
        color: ColorARGB = ColorARGB.Red,
    ): DisplayOp.DrawRect = DisplayOp.DrawRect(
        bounds,
        Paint.fill(color).copy(antiAlias = false),
        Matrix3x3F32.Identity,
        ClipStack.WideOpen,
    )

    private fun vertices(): DisplayOp.DrawVertices = DisplayOp.DrawVertices(
        vertices = Vertices(
            mode = VertexMode.TRIANGLES,
            positions = listOf(Point2F32(1f, 1f), Point2F32(8f, 1f), Point2F32(1f, 8f)),
        ),
        paint = Paint.fill(ColorARGB.Red),
        transform = Matrix3x3F32.Identity,
        clip = ClipStack.WideOpen,
    )

    private fun triangle(): Path = Path().apply {
        moveTo(3f, 3f)
        lineTo(18f, 4f)
        lineTo(10f, 17f)
        close()
    }

    private companion object {
        val RECT = RectF32.ofLTRB(2f, 3f, 12f, 11f)
    }
}
