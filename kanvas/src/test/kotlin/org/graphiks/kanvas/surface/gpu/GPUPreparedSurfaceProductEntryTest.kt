package org.graphiks.kanvas.surface.gpu

import java.util.Collections
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUPreparedImageRefusalCodes
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.PixelFormat
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.surface.RenderResult
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

class GPUPreparedSurfaceProductEntryTest {
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
                    operations = listOf(DisplayOp.FlushAndSnapshot(Rect.fromLTRB(0f, 0f, 1f, 1f))),
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
        Matrix33.identity(),
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
        val RECT = Rect.fromLTRB(0f, 0f, 1f, 1f)
        val EVIDENCE = GPUPreparedSurfaceExecutionEvidence(
            targetCreations = 1,
            targetCloses = 1,
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
