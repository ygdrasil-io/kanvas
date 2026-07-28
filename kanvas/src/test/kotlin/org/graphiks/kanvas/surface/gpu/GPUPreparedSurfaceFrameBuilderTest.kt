package org.graphiks.kanvas.surface.gpu

import java.io.File
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
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
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUPreparedImageRefusalCodes
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedAtlasSourceBlend
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageSampling
import org.graphiks.kanvas.gpu.renderer.product.GPUProductFlagConfig
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
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
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Lattice
import org.graphiks.kanvas.types.LatticeFlags
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.PointMode
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect

class GPUPreparedSurfaceFrameBuilderTest {
    @Test
    fun `prepared atlas expands to ordered sampled packets sharing one artifact with distinct uniforms`() {
        val atlas = atlasImage("builder-atlas")
        val operation = DisplayOp.DrawAtlas(
            atlas = atlas,
            transforms = listOf(
                Matrix33.translate(2f, 3f),
                Matrix33.translate(12f, 5f) * Matrix33.skew(0.25f, 0f),
            ),
            texRects = listOf(
                Rect.fromLTRB(0f, 0f, 2f, 2f),
                Rect.fromLTRB(2f, 0f, 4f, 2f),
            ),
            colors = listOf(Color.RED, Color.fromArgb(128, 0, 128, 0)),
            blendMode = BlendMode.MODULATE,
            paint = Paint.fill(Color.fromArgb(192, 255, 255, 255)),
            transform = Matrix33.identity(),
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
                Matrix33.translate(2f, 3f),
                Matrix33.translate(12f, 5f),
            ),
            texRects = listOf(
                Rect.fromLTRB(0f, 0f, 2f, 2f),
                Rect.fromLTRB(2f, 0f, 4f, 2f),
            ),
            colors = listOf(Color.RED, Color.GREEN),
            blendMode = BlendMode.SRC,
            paint = Paint.fill(Color.WHITE),
            transform = Matrix33.identity(),
            clip = ClipStack.DeviceRect(
                rect = Rect.fromLTRB(4f, 6f, 14f, 15f),
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
        fun atlasWithClip(rect: Rect) = DisplayOp.DrawAtlas(
            atlas = atlasImage("builder-atlas-scissor-total"),
            transforms = listOf(Matrix33.translate(2f, 3f)),
            texRects = listOf(Rect.fromLTRB(0f, 0f, 2f, 2f)),
            colors = listOf(Color.RED),
            blendMode = BlendMode.SRC,
            paint = Paint.fill(Color.WHITE),
            transform = Matrix33.identity(),
            clip = ClipStack.DeviceRect(rect, antiAlias = false),
        )

        val ready = assertIs<GPUPreparedSurfaceFrameBuildResult.Ready>(
            GPUPreparedSurfaceFrameBuilder.build(
                imageRequest(listOf(atlasWithClip(Rect.fromLTRB(-4f, 6f, 40f, 30f)))),
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
            Rect.fromLTRB(16f, 6f, 4f, 15f),
            Rect.fromLTRB(4f, 6f, Float.POSITIVE_INFINITY, 15f),
            Rect.fromLTRB(40f, 6f, 50f, 15f),
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
            center = Rect.fromLTRB(2f, 2f, 4f, 4f),
            dst = Rect.fromLTRB(2f, 3f, 26f, 21f),
            paint = null,
            transform = Matrix33.identity(),
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
    fun `prepared image nine hard scissor refuses the whole expanded frame exactly`() {
        val image = imageNine("builder-nine-scissor-refusal")
        val operation = DisplayOp.DrawImageNine(
            image = image,
            center = Rect.fromLTRB(2f, 2f, 4f, 4f),
            dst = Rect.fromLTRB(2f, 3f, 26f, 21f),
            paint = null,
            transform = Matrix33.identity(),
            clip = ClipStack.DeviceRect(
                Rect.fromLTRB(6f, 7f, 18f, 16f),
                antiAlias = false,
            ),
        )

        val refused = assertIs<GPUPreparedSurfaceFrameBuildResult.Refused>(
            GPUPreparedSurfaceFrameBuilder.build(imageRequest(listOf(operation))),
        )

        assertEquals("unsupported.surface.prepared.image-clip", refused.diagnostic.code.value)
        assertEquals("0", refused.diagnostic.facts["commandId"])
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
                    Color.TRANSPARENT,
                    Color.fromArgb(128, 128, 64, 32),
                    Color.TRANSPARENT,
                ),
                flags = listOf(
                    LatticeFlags.DEFAULT,
                    LatticeFlags.FIXED_COLOR,
                    LatticeFlags.TRANSPARENT,
                ),
            ),
            dst = Rect.fromLTRB(2f, 4f, 26f, 12f),
            paint = Paint.fill(Color.fromArgb(128, 30, 40, 50)).copy(antiAlias = false),
            transform = Matrix33.translate(1f, 2f),
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
                colors = listOf(Color.GREEN, Color.BLUE),
                flags = listOf(LatticeFlags.FIXED_COLOR, LatticeFlags.FIXED_COLOR),
            ),
            dst = Rect.fromLTRB(2f, 4f, 18f, 12f),
            paint = Paint.fill(Color.WHITE).copy(antiAlias = false),
            transform = Matrix33.identity(),
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
            Paint.fill(Color.RED).copy(antiAlias = false),
            Matrix33.identity(),
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
        val nonPrimary = Color.fromArgb(a = 255, r = 40, g = 120, b = 208)
        val fractionalCases = listOf(
            "material-alpha" to
                rect(color = Color.fromArgb(a = 160, r = 40, g = 120, b = 208)),
            "rect-aa" to
                rect(color = nonPrimary).copy(
                    paint = Paint.fill(nonPrimary).copy(antiAlias = true),
                ),
            "rrect-aa" to
                DisplayOp.DrawRRect(
                    RRect(RECT, radius = 2f),
                    Paint.fill(nonPrimary).copy(antiAlias = true),
                    Matrix33.identity(),
                    ClipStack.WideOpen,
                ),
            "clip-aa" to
                rect(color = nonPrimary).copy(
                    clip = ClipStack.DeviceRect(
                        Rect.fromLTRB(1.5f, 1.5f, 14.5f, 12.5f),
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
                        clip = ClipStack.DeviceRect(Rect.fromLTRB(1f, 1f, 14f, 12f), antiAlias = false),
                    ),
                )),
            ),
        )
        val unsupportedAaPath = assertIs<GPUPreparedSurfaceFrameBuildResult.Refused>(
            GPUPreparedSurfaceFrameBuilder.build(
                request(listOf(
                    DisplayOp.DrawPath(
                        triangle(),
                        Paint.fill(nonPrimary).copy(antiAlias = true),
                        Matrix33.identity(),
                        ClipStack.WideOpen,
                    ),
                )),
            ),
        )
        assertEquals(
            "invalid.core_primitive.coverage_sample.stencil_aa_requires_multisample",
            unsupportedAaPath.diagnostic.code.value,
        )
    }

    private fun srgbToLinear(encoded: Float): Float = if (encoded <= 0.04045f) {
        encoded / 12.92f
    } else {
        ((encoded + 0.055f) / 1.055f).pow(2.4f)
    }

    @Test
    fun `rect src over with state events preserves exact frame envelope ids provenance and counts`() {
        val operations = listOf(
            DisplayOp.SetTransform(Matrix33.translate(2f, 3f)),
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
            Paint.fill(Color.BLUE).copy(antiAlias = false),
            Matrix33.identity(),
            ClipStack.DeviceRect(Rect.fromLTRB(2f, 3f, 20f, 18f), antiAlias = false),
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
            rect(Rect.fromLTRB(1f, 1f, 7f, 7f), Color.RED),
            DisplayOp.DrawPath(
                triangle(),
                Paint.fill(Color.GREEN).copy(antiAlias = false),
                Matrix33.identity(),
                ClipStack.WideOpen,
            ),
            rect(Rect.fromLTRB(20f, 12f, 28f, 20f), Color.BLUE),
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
    fun `refusal matrix preserves true diagnostics while sRGB translucent solid is ready`() {
        val gradient = Shader.LinearGradient(
            Point(0f, 0f),
            Point(8f, 8f),
            listOf(GradientStop(0f, Color.RED), GradientStop(1f, Color.BLUE)),
        )
        val complexClip = ClipStack.Complex(
            listOf(
                org.graphiks.kanvas.canvas.ClipStackOp.RectOp(
                    Rect.fromLTRB(1f, 1f, 24f, 20f), ClipOp.INTERSECT, antiAlias = true,
                ),
                org.graphiks.kanvas.canvas.ClipStackOp.RectOp(
                    Rect.fromLTRB(4f, 4f, 8f, 8f), ClipOp.DIFFERENCE, antiAlias = true,
                ),
            ),
        )
        val translucentRequest =
            request(listOf(rect(color = Color.fromArgb(a = 160, r = 40, g = 120, b = 208))))
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

        val cases = listOf(
            request(listOf(rect().copy(paint = Paint.fill(Color.WHITE).copy(shader = gradient)))) to
                "unsupported.core_primitive.material.non_solid",
            request(listOf(rect().copy(paint = Paint.fill(Color.RED).copy(blendMode = BlendMode.SRC)))) to
                "unsupported.destination_read.required",
            request(listOf(
                rect(color = Color.BLUE),
                rect(color = Color.RED).copy(paint = Paint.fill(Color.RED).copy(blendMode = BlendMode.CLEAR)),
            )) to
                "unsupported.native-core-primitive.blend",
            request(listOf(DisplayOp.DrawPoints(
                PointMode.LINES,
                listOf(Point(2f, 2f), Point(12f, 2f)),
                Paint.stroke(Color.RED, 2f).copy(strokeCap = StrokeCap.SQUARE, antiAlias = false),
                Matrix33.identity(),
                ClipStack.WideOpen,
            ))) to "unsupported.geometry.path_key_nondeterministic",
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
            drawImage(image, Rect.fromLTRB(1f, 1f, 5f, 3f)),
            drawImage(image, Rect.fromLTRB(7f, 2f, 15f, 6f)),
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
            drawImage(first, Rect.fromLTRB(1f, 1f, 5f, 3f)),
            drawImage(second, Rect.fromLTRB(6f, 1f, 8f, 5f)),
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
                    drawImage(first, Rect.fromLTRB(1f, 1f, 5f, 5f)),
                    drawImage(second, Rect.fromLTRB(7f, 1f, 11f, 5f)),
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
            rect(Rect.fromLTRB(1f, 1f, 5f, 5f), Color.RED),
            drawImage(first, Rect.fromLTRB(6f, 1f, 10f, 5f)),
            rect(Rect.fromLTRB(11f, 1f, 15f, 5f), Color.BLUE),
            drawImage(second, Rect.fromLTRB(16f, 1f, 20f, 5f)),
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
    fun `surface propagates canonical image refusal codes and adds only boundary facts`() {
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
                ColorType.GRAY_8,
                "unsupported-format",
                byteArrayOf(1),
                alphaType = AlphaType.PREMUL,
            ) to GPUPreparedImageRefusalCodes.PIXEL_FORMAT,
            Image(
                1,
                1,
                ColorType.RGBA_8888,
                "unpremultiplied",
                byteArrayOf(1, 2, 3, 4),
                alphaType = AlphaType.UNPREMUL,
            ) to GPUPreparedImageRefusalCodes.ALPHA_INTERPRETATION,
        )

        cases.forEach { (image, expectedCode) ->
            val refused = assertIs<GPUPreparedSurfaceFrameBuildResult.Refused>(
                GPUPreparedSurfaceFrameBuilder.build(
                    imageRequest(listOf(drawImage(image, Rect.fromLTRB(1f, 1f, 5f, 5f)))),
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
    fun `direct builder propagates DrawImage lowerer refusals transactionally`() {
        val image = Image(
            width = GPUPreparedImageTestFixtures.rgbaPremul2x2Width,
            height = GPUPreparedImageTestFixtures.rgbaPremul2x2Height,
            colorType = GPUPreparedImageTestFixtures.rgbaPremul2x2ColorType,
            sourceId = "builder-lowerer-refusal",
            pixels = GPUPreparedImageTestFixtures.rgbaPremul2x2Bytes,
            alphaType = AlphaType.PREMUL,
        )
        val base = drawImage(image, Rect.fromLTRB(1f, 1f, 5f, 5f))
        val cases = listOf(
            base.copy(
                paint = Paint.fill(Color.WHITE).copy(
                    shader = Shader.Image(image, sampling = SamplingOptions.Cubic.Mitchell),
                ),
            ) to GPUPreparedImageRefusalCodes.SAMPLING_CUBIC,
            base.copy(
                transform = Matrix33.makeAll(
                    1f, 0f, 0f,
                    0f, 1f, 0f,
                    0.001f, 0f, 1f,
                ),
            ) to GPUPreparedImageRefusalCodes.PERSPECTIVE_SAMPLING,
            base.copy(
                transform = Matrix33.makeAll(
                    0f, 0f, 0f,
                    0f, 0f, 0f,
                ),
            ) to GPUPreparedImageRefusalCodes.PERSPECTIVE_SAMPLING,
            base.copy(
                paint = Paint.fill(Color.WHITE).copy(blendMode = BlendMode.MULTIPLY),
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
    fun `unexpected construction exception becomes a stable contract refusal without variable detail`() {
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
            mapOf("failureClass" to IllegalStateException::class.java.name),
            refused.diagnostic.facts,
        )
        assertTrue("runtime-specific detail" !in refused.diagnostic.message)
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

    private fun drawImage(image: Image, dst: Rect): DisplayOp.DrawImage = DisplayOp.DrawImage(
        image = image,
        src = Rect.fromLTRB(0f, 0f, image.width.toFloat(), image.height.toFloat()),
        dst = dst,
        paint = null,
        transform = Matrix33.identity(),
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

    private fun capability(name: String) = GPUCapabilityFact(
        name = name,
        source = "test",
        value = "supported",
        affectsValidity = true,
        evidenceLabel = "test:$name",
    )

    private fun rect(
        bounds: Rect = RECT,
        color: Color = Color.RED,
    ): DisplayOp.DrawRect = DisplayOp.DrawRect(
        bounds,
        Paint.fill(color).copy(antiAlias = false),
        Matrix33.identity(),
        ClipStack.WideOpen,
    )

    private fun triangle(): Path = Path().apply {
        moveTo(3f, 3f)
        lineTo(18f, 4f)
        lineTo(10f, 17f)
        close()
    }

    private companion object {
        val RECT = Rect.fromLTRB(2f, 3f, 12f, 11f)
    }
}
