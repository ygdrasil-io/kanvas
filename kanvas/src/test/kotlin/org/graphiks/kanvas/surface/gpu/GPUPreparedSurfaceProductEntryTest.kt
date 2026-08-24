package org.graphiks.kanvas.surface.gpu

import java.util.Collections
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUPreparedImageRefusalCodes
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageArtifactResult
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.PixelFormat
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.surface.RenderResult
import org.graphiks.kanvas.types.Color
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.RectF32

@OptIn(ExperimentalUnsignedTypes::class)
class GPUPreparedSurfaceProductEntryTest {
    @Test
    fun `public entry accepts unpremultiplied BGRA and submits normalized RGBA upload`() {
        val sourceBytes = byteArrayOf(0x11, 0x22, 0x33, 0x44)
        val normalizedRgba = byteArrayOf(0x33, 0x22, 0x11, 0x44)
        val image = Image(
            width = 1,
            height = 1,
            colorType = ColorType.BGRA_8888,
            sourceId = "public-bgra-unpremultiplied",
            pixels = sourceBytes,
            alphaType = AlphaType.UNPREMUL,
        )
        val source = assertIs<GPUPreparedImageArtifactResult.Ready>(
            GPUPreparedSurfaceImageSource.prepare(image),
        )
        assertContentEquals(normalizedRgba, source.artifact.tightRgba8BytesForUpload())

        val operation = DisplayOp.DrawImage(
            image = image,
            src = RectF32.ofLTRB(0f, 0f, 1f, 1f),
            dst = RectF32.ofLTRB(0f, 0f, 4f, 4f),
            paint = null,
            transform = Matrix3x3F32.Identity,
            clip = ClipStack.WideOpen,
        )
        val harness = PreparedProductExecutionHarness(width = 8, height = 8)
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = listOf(operation),
            width = 8,
            height = 8,
            format = PixelFormat.BGRA8,
            config = RenderConfig.DEFAULT,
            executionPort = harness.port,
            trace = GPUPreparedSurfaceRouteTrace { decisions += it },
        )

        assertEquals(PixelFormat.BGRA8, result.format)
        assertEquals(1, result.stats.opsDispatched)
        assertContentEquals(harness.expectedRgba.toUByteArray(), result.pixels)
        val preparedDecision = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single())
        assertEquals(
            GPUPreparedSurfaceExecutionRouteMarker.PreparedSurfaceDirect,
            preparedDecision.evidence.routeMarker,
        )
        assertEquals(1, harness.backend.prepareCalls)
        assertEquals(1, harness.backend.session.submitCalls)
        assertEquals(GPUColorFormat.BGRA8Unorm, harness.backend.preparedRequests.single().colorFormat)
        assertEquals(
            GPUColorInterpretation.EncodedPremulSrgb,
            harness.backend.preparedRequests.single().colorInterpretation,
        )

        val taskList = harness.backend.session.submittedTaskLists.single()
        val upload = taskList.tasks.filterIsInstance<GPUTask.Upload>().single()
        val plan = assertNotNull(upload.imageResourcePlan)
        val semantic = assertIs<GPUDrawSemanticPayload.SampledImage>(
            taskList.tasks.filterIsInstance<GPUTask.Render>().single()
                .drawPackets.single().semanticPayload,
        )
        assertContentEquals(normalizedRgba, semantic.artifact.tightRgba8BytesForUpload())
        assertContentEquals(
            normalizedRgba,
            plan.uploadLayout.bytesForUpload().copyOfRange(0, normalizedRgba.size),
        )
        assertEquals(semantic.artifact.key, plan.artifactKey)
        assertEquals("RGBA8UnormSrgb", semantic.artifactUploadFormat)
        assertEquals("StraightEncodedSrgb", semantic.artifactUploadEncoding)
        assertEquals(GPUColorInterpretation.LinearPremul.value, semantic.shaderInterpretation)
        assertTrue(semantic.hasCanonicalHashIntegrity())
    }

    @Test
    fun `admitted image executes prepared and trace failure never changes the route`() {
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val harness = PreparedProductExecutionHarness(width = 8, height = 8)

        val result = GPUPreparedSurfaceProductEntry.render(
            operations = listOf(image()),
            width = 8,
            height = 8,
            format = PixelFormat.RGBA8,
            config = RenderConfig.DEFAULT,
            executionPort = harness.port,
            trace = GPUPreparedSurfaceRouteTrace { decisions += it },
        )

        assertEquals(harness.expectedRgba.toUByteArray().toList(), result.pixels.toList())
        assertEquals(1, decisions.size)
        assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single())

        val failingTraceHarness = PreparedProductExecutionHarness(width = 8, height = 8)
        val resultWithFailingTrace = GPUPreparedSurfaceProductEntry.render(
            operations = listOf(image()),
            width = 8,
            height = 8,
            format = PixelFormat.RGBA8,
            config = RenderConfig.DEFAULT,
            executionPort = failingTraceHarness.port,
            trace = GPUPreparedSurfaceRouteTrace { throw IllegalStateException("observer failure") },
        )

        assertEquals(failingTraceHarness.expectedRgba.toUByteArray().toList(), resultWithFailingTrace.pixels.toList())
    }

    @Test
    fun `invalid admitted image raises its exact prepared terminal`() {
        val invalidImage = preparedProductImage(
            sourceId = "entry-invalid-image",
            pixels = null,
        )
        val harness = PreparedProductExecutionHarness(width = 8, height = 8)

        val failure = kotlin.runCatching {
            GPUPreparedSurfaceProductEntry.render(
                operations = listOf(preparedProductImageOperations(invalidImage).first().first),
                width = 8,
                height = 8,
                format = PixelFormat.RGBA8,
                config = RenderConfig.DEFAULT,
                executionPort = harness.port,
            )
        }.exceptionOrNull()

        val typed = assertIs<GPUPreparedSurfaceTerminalException>(failure)
        assertEquals(GPUPreparedImageRefusalCodes.PIXELS_MISSING, typed.diagnostic.code.value)
        assertEquals(0, harness.backend.prepareCalls)
    }

    @Test
    fun `terminal raises a typed exception with canonical stable message`() {
        val diagnostic = diagnostic("failed.test.prepared", "Prepared frame failed canonically.")

        val failure = kotlin.runCatching {
            GPUPreparedSurfaceProductEntry.render(
                operations = listOf(rect()),
                width = 1,
                height = 1,
                format = PixelFormat.RGBA8,
                config = RenderConfig.DEFAULT,
                executionPort = GPUPreparedSurfaceExecutionPort {
                    GPUPreparedSurfaceExecutionResult.TerminalFailure(diagnostic)
                },
            )
        }.exceptionOrNull()

        val typed = assertIs<GPUPreparedSurfaceTerminalException>(failure)
        assertSame(diagnostic, typed.diagnostic)
        assertEquals("failed.test.prepared: Prepared frame failed canonically.", typed.message)
    }

    @Test
    fun `owner serializes prepared with prepared and gives distinct prepared ids`() {
        val probe = ConcurrencyProbe()
        val ids = Collections.synchronizedList(mutableListOf<Long>())
        val ordinal = AtomicLong()
        val execution = GPUPreparedSurfaceExecutionPort {
            probe.use {
                ids += ordinal.incrementAndGet()
                preparedResult()
            }
        }

        runConcurrently(
            { renderPrepared(execution) },
            { renderPrepared(execution) },
        )

        assertEquals(1, probe.maximum.get())
        assertEquals(2, ids.size)
        assertNotEquals(ids[0], ids[1])
    }

    @Test
    fun `owner serializes prepared with state event frames`() {
        val probe = ConcurrencyProbe()
        val execution = GPUPreparedSurfaceExecutionPort { probe.use { preparedResult() } }

        runConcurrently(
            { renderPrepared(execution) },
            {
                GPUPreparedSurfaceProductEntry.render(
                    operations = listOf(DisplayOp.FlushAndSnapshot(RectF32.ofLTRB(0f, 0f, 1f, 1f))),
                    width = 1,
                    height = 1,
                    format = PixelFormat.RGBA8,
                    config = RenderConfig.DEFAULT,
                    executionPort = execution,
                )
            },
        )

        assertEquals(1, probe.maximum.get())
    }

    @Test
    fun `owner keeps builder refusal terminal atomic against prepared work`() {
        val probe = ConcurrencyProbe()
        val call = AtomicInteger()
        val ids = Collections.synchronizedList(mutableListOf<Long>())
        val ordinal = AtomicLong()
        val outcomes = Collections.synchronizedList(mutableListOf<Any>())
        val execution = GPUPreparedSurfaceExecutionPort {
            probe.use {
                ids += ordinal.incrementAndGet()
                if (call.incrementAndGet() == 1) {
                    GPUPreparedSurfaceExecutionResult.BeforePreparedEntryRefused(
                        diagnostic("unsupported.test.builder", "Builder refused."),
                    )
                } else {
                    preparedResult()
                }
            }
        }

        runConcurrently(
            { outcomes += renderOutcome(execution) },
            { outcomes += renderOutcome(execution) },
        )

        assertEquals(1, probe.maximum.get())
        assertEquals(2, ids.size)
        assertNotEquals(ids[0], ids[1])
        val terminals = outcomes.filterIsInstance<GPUPreparedSurfaceTerminalException>()
        assertEquals(1, terminals.size)
        assertEquals("unsupported.test.builder", terminals.single().diagnostic.code.value)
        assertEquals(1, outcomes.filterIsInstance<RenderResult>().size)
    }

    private fun renderPrepared(
        execution: GPUPreparedSurfaceExecutionPort,
    ) = GPUPreparedSurfaceProductEntry.render(
        operations = listOf(rect()),
        width = 1,
        height = 1,
        format = PixelFormat.RGBA8,
        config = RenderConfig.DEFAULT,
        executionPort = execution,
    )

    private fun renderOutcome(execution: GPUPreparedSurfaceExecutionPort): Any =
        runCatching { renderPrepared(execution) }.let { result ->
            result.getOrNull() ?: result.exceptionOrNull()!!
        }

    private fun runConcurrently(first: () -> Unit, second: () -> Unit) {
        val executor = Executors.newFixedThreadPool(2)
        try {
            val futures = executor.invokeAll(listOf(Callable { first() }, Callable { second() }))
            futures.forEach { it.get(5, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }

    private class ConcurrencyProbe {
        private val active = AtomicInteger()
        val maximum = AtomicInteger()

        fun <T> use(block: () -> T): T {
            val current = active.incrementAndGet()
            maximum.updateAndGet { maxOf(it, current) }
            try {
                Thread.sleep(40)
                return block()
            } finally {
                active.decrementAndGet()
            }
        }
    }

    private fun preparedResult() = GPUPreparedSurfaceExecutionResult.Succeeded(
        byteArrayOf(1, 2, 3, 4),
        visualOperationCount = 1,
        stateEventCount = 0,
        evidence = EVIDENCE,
    )

    private fun rect() = DisplayOp.DrawRect(
        RECT,
        Paint.fill(Color.RED).copy(antiAlias = false),
        Matrix3x3F32.Identity,
        ClipStack.WideOpen,
    )

    private fun image() = preparedProductImageOperations().first().first

    private fun diagnostic(code: String, message: String) = GPUDiagnostic(
        GPUDiagnosticCode(code),
        GPUDiagnosticDomain.Execution,
        GPUDiagnosticSeverity.Error,
        message,
    )

    private companion object {
        val RECT = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        val EVIDENCE = GPUPreparedSurfaceExecutionEvidence(
            targetCreations = 1,
            targetCloses = 0,
            frameCoordinatorCreations = 1,
            encoders = 1,
            commandBuffers = 1,
            submits = 1,
            readbackCopies = 1,
            destinationSnapshotCreations = 0,
            destinationReadbackSnapshots = 0,
            renderPasses = 1,
            draws = 1,
            drawIndexed = 0,
            pipelineBinds = 1,
            activeNativePayloads = 0,
            outputOwnedNativePayloads = 0,
            quarantinedNativePayloads = 0,
            retentionRegistrations = 1,
            retentionCompletions = 1,
            retentionQuarantines = 0,
            distinctRetentionTickets = 1,
            routeMarker = GPUPreparedSurfaceExecutionRouteMarker.PreparedSurfaceDirect,
        )
    }
}
