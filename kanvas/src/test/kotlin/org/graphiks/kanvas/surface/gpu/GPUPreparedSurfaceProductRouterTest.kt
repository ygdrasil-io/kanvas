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
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.PixelFormat
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

class GPUPreparedSurfaceProductRouterTest {
    @Test
    fun `gate legacy and BGRA never call the execution port`() {
        var calls = 0
        val port = GPUPreparedSurfaceExecutionPort {
            calls++
            error("must not execute")
        }
        val image = Image.placeholder(1, 1)
        val imageOp = DisplayOp.DrawImage(image, RECT, RECT, null, Matrix33.identity(), ClipStack.WideOpen)

        val imageRoute = GPUPreparedSurfaceProductRouter.route(
            listOf(imageOp), 4, 4, PixelFormat.RGBA8, RenderConfig.DEFAULT, port,
        )
        val bgraRoute = GPUPreparedSurfaceProductRouter.route(
            listOf(rect()), 4, 4, PixelFormat.BGRA8, RenderConfig.DEFAULT, port,
        )

        assertEquals("legacy.surface.prepared.family.images", assertIs<GPUPreparedSurfaceProductRoute.Legacy>(imageRoute).code)
        assertEquals("legacy.surface.prepared.pixel-format.bgra8", assertIs<GPUPreparedSurfaceProductRoute.Legacy>(bgraRoute).code)
        assertEquals(0, calls)
    }

    @Test
    fun `before-entry refusal is legacy while terminal failure remains terminal`() {
        val refusal = diagnostic("unsupported.test.builder", "builder refusal")
        val terminal = diagnostic("failed.test.terminal", "terminal failure")

        val legacy = GPUPreparedSurfaceProductRouter.route(
            listOf(rect()), 4, 4, PixelFormat.RGBA8, RenderConfig.DEFAULT,
            GPUPreparedSurfaceExecutionPort {
                GPUPreparedSurfaceExecutionResult.BeforePreparedEntryRefused(refusal)
            },
        )
        val failed = GPUPreparedSurfaceProductRouter.route(
            listOf(rect()), 4, 4, PixelFormat.RGBA8, RenderConfig.DEFAULT,
            GPUPreparedSurfaceExecutionPort {
                GPUPreparedSurfaceExecutionResult.TerminalFailure(terminal)
            },
        )

        assertEquals(refusal.code.value, assertIs<GPUPreparedSurfaceProductRoute.Legacy>(legacy).code)
        assertEquals(terminal, assertIs<GPUPreparedSurfaceProductRoute.Terminal>(failed).diagnostic)
    }

    @Test
    fun `success converts exact pixels dimensions stats and evidence`() {
        val source = byteArrayOf(1, 2, 3, 4)
        val evidence = evidence(draws = 7, drawIndexed = 5, pipelineBinds = 3)
        val route = GPUPreparedSurfaceProductRouter.route(
            listOf(rect()), 1, 1, PixelFormat.RGBA8, RenderConfig.DEFAULT,
            GPUPreparedSurfaceExecutionPort {
                GPUPreparedSurfaceExecutionResult.Succeeded(source, 1, 2, evidence)
            },
        )
        source[0] = 99

        val prepared = assertIs<GPUPreparedSurfaceProductRoute.Prepared>(route)
        assertContentEquals(ubyteArrayOf(1u, 2u, 3u, 4u), prepared.result.pixels)
        assertEquals(1, prepared.result.width)
        assertEquals(1, prepared.result.height)
        assertEquals(PixelFormat.RGBA8, prepared.result.format)
        assertEquals(1, prepared.result.stats.opsDispatched)
        assertEquals(0, prepared.result.stats.opsRefused)
        assertEquals(3, prepared.result.stats.pipelineCount)
        assertEquals(12, prepared.result.stats.drawCallCount)
        assertEquals(1f, prepared.result.stats.coverage)
        assertEquals(false, prepared.result.stats.coverageMeasured)
        assertEquals(evidence, prepared.evidence)
    }

    @Test
    fun `native stats overflow is a stable terminal diagnostic`() {
        val route = GPUPreparedSurfaceProductRouter.route(
            listOf(rect()), 1, 1, PixelFormat.RGBA8, RenderConfig.DEFAULT,
            GPUPreparedSurfaceExecutionPort {
                GPUPreparedSurfaceExecutionResult.Succeeded(
                    byteArrayOf(1, 2, 3, 4),
                    1,
                    0,
                    evidence(draws = Long.MAX_VALUE, drawIndexed = 1),
                )
            },
        )

        val diagnostic = assertIs<GPUPreparedSurfaceProductRoute.Terminal>(route).diagnostic
        assertEquals("invalid.surface.prepared.render-stats-overflow", diagnostic.code.value)
        assertEquals("drawCallCount", diagnostic.facts["field"])
    }

    private fun evidence(
        draws: Long = 1,
        drawIndexed: Long = 0,
        pipelineBinds: Long = 1,
    ) = GPUPreparedSurfaceExecutionEvidence(
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
        draws = draws,
        drawIndexed = drawIndexed,
        pipelineBinds = pipelineBinds,
        activeNativePayloads = 0,
        outputOwnedNativePayloads = 0,
        quarantinedNativePayloads = 0,
        retentionRegistrations = 1,
        retentionCompletions = 1,
        retentionQuarantines = 0,
        distinctRetentionTickets = 1,
    )

    private fun rect() = DisplayOp.DrawRect(
        RECT,
        Paint.fill(Color.RED).copy(antiAlias = false),
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
        val RECT = Rect.fromLTRB(0f, 0f, 4f, 4f)
    }
}
