package org.graphiks.kanvas.surface.gpu

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.product.GPUProductFlagConfig
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.PointMode
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect

class GPUPreparedSurfaceFrameBuilderTest {
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
    fun `encoded premul sRGB refuses exact fractional alpha authorities but admits hard coverage`() {
        val nonPrimary = Color.fromArgb(a = 255, r = 40, g = 120, b = 208)
        val fractionalCases = listOf(
            Triple(
                "material-alpha",
                rect(color = Color.fromArgb(a = 160, r = 40, g = 120, b = 208)),
                "unsupported.surface.prepared.encoded-premul-srgb.translucent-solid",
            ),
            Triple(
                "rect-aa",
                rect(color = nonPrimary).copy(paint = Paint.fill(nonPrimary).copy(antiAlias = true)),
                "unsupported.surface.prepared.encoded-premul-srgb.fractional-coverage",
            ),
            Triple("rrect-aa", DisplayOp.DrawRRect(
                RRect(RECT, radius = 2f),
                Paint.fill(nonPrimary).copy(antiAlias = true),
                Matrix33.identity(),
                ClipStack.WideOpen,
            ), "unsupported.surface.prepared.encoded-premul-srgb.fractional-coverage"),
            Triple(
                "clip-aa",
                rect(color = nonPrimary).copy(
                    clip = ClipStack.DeviceRect(Rect.fromLTRB(1.5f, 1.5f, 14.5f, 12.5f), antiAlias = true),
                ),
                "unsupported.surface.prepared.encoded-premul-srgb.fractional-coverage",
            ),
        )

        fractionalCases.forEach { (label, operation, expectedCode) ->
            val refusal = assertIs<GPUPreparedSurfaceFrameBuildResult.Refused>(
                GPUPreparedSurfaceFrameBuilder.build(request(listOf(operation))),
                label,
            )
            assertEquals(expectedCode, refusal.diagnostic.code.value, label)
            assertEquals("0", refusal.diagnostic.facts["commandId"], label)
            if (label != "material-alpha") assertTrue(refusal.diagnostic.facts["authority"].orEmpty().isNotBlank())
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
    fun `pure refusal matrix preserves original mapper semantic and prepared diagnostics`() {
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
        val cases = listOf(
            request(listOf(rect(color = Color.fromArgb(a = 160, r = 40, g = 120, b = 208)))) to
                "unsupported.surface.prepared.encoded-premul-srgb.translucent-solid",
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
            base.copy(targetFacts = GPUTargetFacts(31, 24, "rgba8unorm")) to
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
            targetFacts = GPUTargetFacts(32, 24, "rgba8unorm"),
            targetBounds = GPUPixelBounds(0, 0, 32, 24),
            capabilities = capabilities,
            deviceGeneration = GPUDeviceGenerationID(11),
            target = GPUFrameTargetRef("surface-frame-target"),
            recordingId = GPURecordingID("surface-frame-recording"),
            frameId = GPUFrameID(77),
            readbackRequestId = GPUReadbackRequestID("surface-frame-readback"),
        )
    }

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
