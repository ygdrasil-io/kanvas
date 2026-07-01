package org.graphiks.kanvas.skia

import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.surface.Surface

object SkiaGmRenderer {
    private const val DEFAULT_WIDTH = 800
    private const val DEFAULT_HEIGHT = 600

    fun render(gm: SkiaGm, width: Int = gm.width, height: Int = gm.height, config: RenderConfig = RenderConfig.DEFAULT): SkiaRenderResult {
        val surface = Surface(width = width, height = height, config = config)
        val canvas = surface.canvas()
        val gmCanvas = GmCanvas(canvas, width, height)
        gm.draw(gmCanvas, width, height)
        val result = surface.render()
        return SkiaRenderResult(
            rgba = result.pixels.map { it.toByte() }.toByteArray(),
            width = width,
            height = height,
            dispatchedCount = result.stats.opsDispatched,
            refusedCount = result.stats.opsRefused,
            diagnostics = result.diagnostics.entries.map { "${it.code}: ${it.reason}" },
        )
    }
}

data class SkiaRenderResult(
    val rgba: ByteArray,
    val width: Int,
    val height: Int,
    val dispatchedCount: Int = 0,
    val refusedCount: Int = 0,
    val diagnostics: List<String> = emptyList(),
)
