package org.graphiks.kanvas.surface.gpu

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.PixelFormat
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

@OptIn(ExperimentalUnsignedTypes::class)
class GPUPreparedTextNoFallbackTest {
    @Test
    fun `legacy adapter exposes only vertices and composites and rejects DrawText`() {
        val adapter = GPULegacyImmediatePathAdapter()

        assertContentEquals(
            listOf(LegacyDisplayOpFamily.Vertices, LegacyDisplayOpFamily.Composites),
            LegacyDisplayOpFamily.entries,
        )
        assertEquals(
            setOf(LegacyDisplayOpFamily.Vertices, LegacyDisplayOpFamily.Composites),
            GPULegacyImmediatePathAdapter.allowedFamilies,
        )
        assertFalse(adapter.accepts(text()))
        assertFailsWith<IllegalArgumentException> {
            adapter.recordInvocation(text())
        }
        assertEquals(0, adapter.dump().invocationCount)
        assertEquals(emptyMap(), adapter.dump().invocationsByFamily)
    }

    @Test
    fun `accepted text success crosses prepared execution seam and never calls legacy`() {
        val operations = listOf(
            DisplayOp.SetTransform(Matrix33.translate(1f, 2f)),
            DisplayOp.Annotation(RECT, "state", "before-text"),
            text(),
        )
        var preparedCalls = 0
        var legacyCalls = 0
        var admittedOperations: List<DisplayOp>? = null

        val result = GPUPreparedSurfaceProductEntry.render(
            operations = operations,
            width = 1,
            height = 1,
            format = PixelFormat.RGBA8,
            config = RenderConfig.DEFAULT,
            executionPort = GPUPreparedSurfaceExecutionPort { request ->
                preparedCalls++
                admittedOperations = request.candidate.operations
                GPUPreparedSurfaceExecutionResult.Succeeded(
                    byteArrayOf(1, 2, 3, 4),
                    visualOperationCount = 1,
                    stateEventCount = 2,
                    evidence = evidence(),
                )
            },
            legacyPort = GPUPreparedSurfaceLegacyPort { _, _, _, _, _, _ ->
                legacyCalls++
                error("accepted text must not continue through legacy")
            },
        )

        assertEquals(1, preparedCalls)
        assertEquals(operations, admittedOperations)
        assertContentEquals(ubyteArrayOf(1u, 2u, 3u, 4u), result.pixels)
        assertEquals(1, result.stats.opsDispatched)
        assertEquals(0, legacyCalls)
    }

    @Test
    fun `text refusal before prepared entry is terminal and never calls legacy`() {
        val refusal = diagnostic("unsupported.text.lowering.test")
        var legacyCalls = 0

        val failure = kotlin.runCatching {
            GPUPreparedSurfaceProductEntry.render(
                operations = listOf(text()),
                width = 1,
                height = 1,
                format = PixelFormat.RGBA8,
                config = RenderConfig.DEFAULT,
                executionPort = GPUPreparedSurfaceExecutionPort {
                    GPUPreparedSurfaceExecutionResult.BeforePreparedEntryRefused(refusal)
                },
                legacyPort = GPUPreparedSurfaceLegacyPort { _, _, _, _, _, _ ->
                    legacyCalls++
                    error("refused text must not continue through legacy")
                },
            )
        }.exceptionOrNull()

        val terminal = assertIs<GPUPreparedSurfaceTerminalException>(failure)
        assertEquals(refusal, terminal.diagnostic)
        assertEquals(0, legacyCalls)
    }

    @Test
    fun `post-admission text failure is terminal and never calls legacy`() {
        val refusal = diagnostic("failed.text.execution.test")
        var legacyCalls = 0

        val failure = kotlin.runCatching {
            GPUPreparedSurfaceProductEntry.render(
                operations = listOf(text()),
                width = 1,
                height = 1,
                format = PixelFormat.RGBA8,
                config = RenderConfig.DEFAULT,
                executionPort = GPUPreparedSurfaceExecutionPort {
                    GPUPreparedSurfaceExecutionResult.TerminalFailure(refusal)
                },
                legacyPort = GPUPreparedSurfaceLegacyPort { _, _, _, _, _, _ ->
                    legacyCalls++
                    error("failed text must not continue through legacy")
                },
            )
        }.exceptionOrNull()

        val terminal = assertIs<GPUPreparedSurfaceTerminalException>(failure)
        assertEquals(refusal, terminal.diagnostic)
        assertEquals(0, legacyCalls)
    }

    @Test
    fun `text image filter refusal remains terminal for FP-07 and never calls legacy`() {
        val refusal = diagnostic("unsupported.image-filter.text.fp07")
        var legacyCalls = 0

        val failure = kotlin.runCatching {
            GPUPreparedSurfaceProductEntry.render(
                operations = listOf(text(Paint.fill(Color.RED).copy(imageFilter = ImageFilter.Blur(1f, 1f)))),
                width = 1,
                height = 1,
                format = PixelFormat.RGBA8,
                config = RenderConfig.DEFAULT,
                executionPort = GPUPreparedSurfaceExecutionPort {
                    GPUPreparedSurfaceExecutionResult.BeforePreparedEntryRefused(refusal)
                },
                legacyPort = GPUPreparedSurfaceLegacyPort { _, _, _, _, _, _ ->
                    legacyCalls++
                    error("image-filtered text must not continue through legacy")
                },
            )
        }.exceptionOrNull()

        val terminal = assertIs<GPUPreparedSurfaceTerminalException>(failure)
        assertEquals(refusal, terminal.diagnostic)
        assertEquals(0, legacyCalls)
    }

    private fun text(paint: Paint = Paint.fill(Color.RED)) = DisplayOp.DrawText(
        TextBlob(emptyList()),
        0f,
        0f,
        paint,
        Matrix33.identity(),
        ClipStack.WideOpen,
    )

    private fun evidence() = GPUPreparedSurfaceExecutionEvidence(
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

    private fun diagnostic(code: String) = GPUDiagnostic(
        code = GPUDiagnosticCode(code),
        domain = GPUDiagnosticDomain.Execution,
        severity = GPUDiagnosticSeverity.Error,
        message = "Prepared text refusal.",
    )

    private companion object {
        val RECT = Rect.fromLTRB(0f, 0f, 1f, 1f)
    }
}
