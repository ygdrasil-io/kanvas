package org.graphiks.kanvas.skia

import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.diagnostic.PipelineTracer
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.DebugLevel
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.surface.Surface
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32

object SkiaGmRenderer {
    private const val DEFAULT_WIDTH = 800
    private const val DEFAULT_HEIGHT = 600

    fun render(
        gm: SkiaGm,
        width: Int = gm.width,
        height: Int = gm.height,
        config: RenderConfig = RenderConfig.DEFAULT,
    ): SkiaRenderResult {
        val surface = Surface(width = width, height = height, config = config)
        val tracer = if (config.debugLevel >= DebugLevel.TRACE) PipelineTracer() else null
        surface.renderOpListener = tracer
        val canvas = surface.canvas()
        canvas.drawRect(RectF32(0f, 0f, width.toFloat(), height.toFloat()),
            Paint(color = ColorARGB.fromRGBA(1f, 1f, 1f, 1f), antiAlias = false))
        val gmCanvas = GmCanvas(canvas, width, height)
        gm.onOnceBeforeDraw(gmCanvas)
        gm.draw(gmCanvas, width, height)
        val result = surface.render()
        val ops = surface.snapshotOps()
        return SkiaRenderResult(
            rgba = result.pixels.map { it.toByte() }.toByteArray(),
            width = width,
            height = height,
            dispatchedCount = result.stats.opsDispatched,
            refusedCount = result.stats.opsRefused,
            diagnostics = result.diagnostics.entries.map { "${it.code}: ${it.reason}" },
            ops = ops,
            pipelineTracer = tracer,
        )
    }

    /**
     * Records a GM exactly as [render] does, but returns the terminal Surface diagnostic instead
     * of discarding its operation count behind the exception boundary. This is test-only evidence
     * for deliberately unsupported GM families; production rendering still fails closed.
     */
    fun renderTerminalAttempt(
        gm: SkiaGm,
        width: Int = gm.width,
        height: Int = gm.height,
        config: RenderConfig = RenderConfig.DEFAULT,
    ): SkiaRenderTerminalAttempt? {
        val surface = Surface(width = width, height = height, config = config)
        val canvas = surface.canvas()
        canvas.drawRect(
            RectF32(0f, 0f, width.toFloat(), height.toFloat()),
            Paint(color = ColorARGB.fromRGBA(1f, 1f, 1f, 1f), antiAlias = false),
        )
        val gmCanvas = GmCanvas(canvas, width, height)
        gm.onOnceBeforeDraw(gmCanvas)
        gm.draw(gmCanvas, width, height)
        val operationCount = surface.snapshotOps().size
        return try {
            surface.render()
            null
        } catch (failure: IllegalStateException) {
            SkiaRenderTerminalAttempt(
                operationCount = operationCount,
                diagnostic = failure.message.orEmpty(),
            )
        }
    }

    /** Captures the existing public Surface attempt for inventory evidence only. */
    fun inventoryEvidence(gm: SkiaGm, config: RenderConfig = RenderConfig.DEFAULT): InventoryRenderEvidence {
        val surface = Surface(width = gm.width, height = gm.height, config = config)
        try {
            val canvas = surface.canvas()
            canvas.drawRect(RectF32(0f, 0f, gm.width.toFloat(), gm.height.toFloat()),
                Paint(color = ColorARGB.fromRGBA(1f, 1f, 1f, 1f), antiAlias = false))
            val gmCanvas = GmCanvas(canvas, gm.width, gm.height)
            gm.onOnceBeforeDraw(gmCanvas)
            gm.draw(gmCanvas, gm.width, gm.height)
        } catch (failure: Exception) {
            return InventoryRenderEvidence(true, false, false, surface.snapshotOps().size,
                listOf(failure.message.orEmpty()), "setup-failure")
        }
        return try {
            val result = surface.render()
            InventoryRenderEvidence(
                attempted = true,
                renderSucceeded = true,
                terminalFailure = false,
                operationCount = surface.snapshotOps().size,
                diagnostics = result.diagnostics.entries.map { "${it.code}: ${it.reason}" },
                route = "gpu",
            )
        } catch (failure: Exception) {
            InventoryRenderEvidence(
                attempted = true,
                renderSucceeded = false,
                terminalFailure = true,
                operationCount = surface.snapshotOps().size,
                diagnostics = listOf(failure.message.orEmpty()),
                route = "failure",
            )
        }
    }
}

data class SkiaRenderResult(
    val rgba: ByteArray,
    val width: Int,
    val height: Int,
    val dispatchedCount: Int = 0,
    val refusedCount: Int = 0,
    val diagnostics: List<String> = emptyList(),
    val ops: List<DisplayOp> = emptyList(),
    val pipelineTracer: PipelineTracer? = null,
)

data class SkiaRenderTerminalAttempt(
    val operationCount: Int,
    val diagnostic: String,
)
