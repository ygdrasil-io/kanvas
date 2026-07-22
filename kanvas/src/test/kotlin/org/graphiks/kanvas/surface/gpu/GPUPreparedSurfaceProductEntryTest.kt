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
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.Diagnostics
import org.graphiks.kanvas.surface.PixelFormat
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.surface.RenderResult
import org.graphiks.kanvas.surface.RenderStats
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

class GPUPreparedSurfaceProductEntryTest {
    @Test
    fun `trace records exactly one handle-free decision and cannot alter routing`() {
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        var legacyCalls = 0
        val legacy = GPUPreparedSurfaceLegacyPort { _, _, _, _, _, _ ->
            legacyCalls++
            LEGACY_RESULT
        }

        val result = GPUPreparedSurfaceProductEntry.render(
            operations = listOf(image()),
            width = 1,
            height = 1,
            format = PixelFormat.RGBA8,
            config = RenderConfig.DEFAULT,
            executionPort = GPUPreparedSurfaceExecutionPort { error("gate must refuse before execution") },
            legacyPort = legacy,
            trace = GPUPreparedSurfaceRouteTrace { decisions += it },
        )

        assertSame(LEGACY_RESULT, result)
        assertEquals(1, legacyCalls)
        assertEquals(1, decisions.size)
        assertEquals(
            "legacy.surface.prepared.family.images",
            assertIs<GPUPreparedSurfaceRouteDecision.Legacy>(decisions.single()).code,
        )

        val resultWithFailingTrace = GPUPreparedSurfaceProductEntry.render(
            operations = listOf(image()),
            width = 1,
            height = 1,
            format = PixelFormat.RGBA8,
            config = RenderConfig.DEFAULT,
            executionPort = GPUPreparedSurfaceExecutionPort { error("gate must refuse before execution") },
            legacyPort = legacy,
            trace = GPUPreparedSurfaceRouteTrace { throw IllegalStateException("observer failure") },
        )

        assertSame(LEGACY_RESULT, resultWithFailingTrace)
        assertEquals(2, legacyCalls)
    }

    @Test
    fun `terminal raises a typed exception with canonical stable message and no legacy`() {
        val diagnostic = diagnostic("failed.test.prepared", "Prepared frame failed canonically.")
        var legacyCalls = 0

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
                legacyPort = GPUPreparedSurfaceLegacyPort { _, _, _, _, _, _ ->
                    legacyCalls++
                    LEGACY_RESULT
                },
            )
        }.exceptionOrNull()

        val typed = assertIs<GPUPreparedSurfaceTerminalException>(failure)
        assertSame(diagnostic, typed.diagnostic)
        assertEquals("failed.test.prepared: Prepared frame failed canonically.", typed.message)
        assertEquals(0, legacyCalls)
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
    fun `owner serializes prepared with gate legacy`() {
        val probe = ConcurrencyProbe()
        val execution = GPUPreparedSurfaceExecutionPort { probe.use { preparedResult() } }
        val legacy = GPUPreparedSurfaceLegacyPort { _, _, _, _, _, _ -> probe.use { LEGACY_RESULT } }

        runConcurrently(
            { renderPrepared(execution, legacy) },
            {
                GPUPreparedSurfaceProductEntry.render(
                    operations = listOf(image()),
                    width = 1,
                    height = 1,
                    format = PixelFormat.RGBA8,
                    config = RenderConfig.DEFAULT,
                    executionPort = execution,
                    legacyPort = legacy,
                )
            },
        )

        assertEquals(1, probe.maximum.get())
    }

    @Test
    fun `owner keeps builder refusal to legacy atomic against prepared work`() {
        val probe = ConcurrencyProbe()
        val call = AtomicInteger()
        val ids = Collections.synchronizedList(mutableListOf<Long>())
        val ordinal = AtomicLong()
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
        val legacy = GPUPreparedSurfaceLegacyPort { _, _, _, _, _, _ -> probe.use { LEGACY_RESULT } }

        runConcurrently(
            { renderPrepared(execution, legacy) },
            { renderPrepared(execution, legacy) },
        )

        assertEquals(1, probe.maximum.get())
        assertEquals(2, ids.size)
        assertNotEquals(ids[0], ids[1])
    }

    private fun renderPrepared(
        execution: GPUPreparedSurfaceExecutionPort,
        legacy: GPUPreparedSurfaceLegacyPort = GPUPreparedSurfaceLegacyPort { _, _, _, _, _, _ -> LEGACY_RESULT },
    ) = GPUPreparedSurfaceProductEntry.render(
        operations = listOf(rect()),
        width = 1,
        height = 1,
        format = PixelFormat.RGBA8,
        config = RenderConfig.DEFAULT,
        executionPort = execution,
        legacyPort = legacy,
    )

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

    private fun image() = DisplayOp.DrawImage(
        Image.placeholder(1, 1),
        RECT,
        RECT,
        null,
        Matrix33.identity(),
        ClipStack.WideOpen,
    )

    private fun diagnostic(code: String, message: String) = GPUDiagnostic(
        GPUDiagnosticCode(code),
        GPUDiagnosticDomain.Execution,
        GPUDiagnosticSeverity.Error,
        message,
    )

    private companion object {
        val RECT = Rect.fromLTRB(0f, 0f, 1f, 1f)
        val LEGACY_RESULT = RenderResult(
            pixels = ubyteArrayOf(0u, 0u, 0u, 0u),
            width = 1,
            height = 1,
            format = PixelFormat.RGBA8,
            diagnostics = Diagnostics(),
            stats = RenderStats(0, 0, 0, 0, 0f),
        )
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
        )
    }
}
