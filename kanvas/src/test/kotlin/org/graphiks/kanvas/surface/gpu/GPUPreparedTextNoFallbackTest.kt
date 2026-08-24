package org.graphiks.kanvas.surface.gpu

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
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
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.RectF32

@OptIn(ExperimentalUnsignedTypes::class)
class GPUPreparedTextNoFallbackTest {
    @Test
    fun `accepted text success crosses prepared execution seam and never calls legacy`() {
        val operations = listOf(
            DisplayOp.SetTransform(Matrix3x3F32.translation(1f, 2f)),
            DisplayOp.Annotation(RECT, "state", "before-text"),
            text(),
        )
        var preparedCalls = 0
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
        )

        assertEquals(1, preparedCalls)
        assertEquals(operations, admittedOperations)
        assertContentEquals(ubyteArrayOf(1u, 2u, 3u, 4u), result.pixels)
        assertEquals(1, result.stats.opsDispatched)
    }

    @Test
    fun `text refusal before prepared entry is terminal`() {
        val refusal = diagnostic("unsupported.text.lowering.test")

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
            )
        }.exceptionOrNull()

        val terminal = assertIs<GPUPreparedSurfaceTerminalException>(failure)
        assertEquals(refusal, terminal.diagnostic)
    }

    @Test
    fun `post-admission text failure is terminal`() {
        val refusal = diagnostic("failed.text.execution.test")

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
            )
        }.exceptionOrNull()

        val terminal = assertIs<GPUPreparedSurfaceTerminalException>(failure)
        assertEquals(refusal, terminal.diagnostic)
    }

    @Test
    fun `text image filter refusal remains terminal for FP-07`() {
        val refusal = diagnostic("unsupported.image-filter.text.fp07")

        val failure = kotlin.runCatching {
            GPUPreparedSurfaceProductEntry.render(
                operations = listOf(text(Paint.fill(ColorARGB.Red).copy(imageFilter = ImageFilter.Blur(1f, 1f)))),
                width = 1,
                height = 1,
                format = PixelFormat.RGBA8,
                config = RenderConfig.DEFAULT,
                executionPort = GPUPreparedSurfaceExecutionPort {
                    GPUPreparedSurfaceExecutionResult.BeforePreparedEntryRefused(refusal)
                },
            )
        }.exceptionOrNull()

        val terminal = assertIs<GPUPreparedSurfaceTerminalException>(failure)
        assertEquals(refusal, terminal.diagnostic)
    }

    private fun text(paint: Paint = Paint.fill(ColorARGB.Red)) = DisplayOp.DrawText(
        TextBlob(emptyList()),
        0f,
        0f,
        paint,
        Matrix3x3F32.Identity,
        ClipStack.WideOpen,
    )

    private fun evidence() = GPUPreparedSurfaceExecutionEvidence(
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

    private fun diagnostic(code: String) = GPUDiagnostic(
        code = GPUDiagnosticCode(code),
        domain = GPUDiagnosticDomain.Execution,
        severity = GPUDiagnosticSeverity.Error,
        message = "Prepared text refusal.",
    )

    private companion object {
        val RECT = RectF32.ofLTRB(0f, 0f, 1f, 1f)
    }
}
