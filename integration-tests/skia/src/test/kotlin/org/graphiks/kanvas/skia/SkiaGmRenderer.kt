package org.graphiks.kanvas.skia

import org.graphiks.kanvas.RenderOptions
import org.graphiks.kanvas.Canvas
import org.graphiks.kanvas.Surface
import org.graphiks.kanvas.skia.GmCanvas

object SkiaGmRenderer {
    private const val DEFAULT_WIDTH = 800
    private const val DEFAULT_HEIGHT = 600
    private const val PATH_VERTEX_BUDGET = 16384

    fun render(gm: SkiaGm, width: Int = gm.width, height: Int = gm.height): SkiaRenderResult {
        val surface = Surface(width = width, height = height)
        val canvas = Canvas(surface, RenderOptions(maxPathVertices = PATH_VERTEX_BUDGET))
        val gmCanvas = GmCanvas(canvas, width, height)
        gm.draw(gmCanvas, width, height)
        val result = surface.renderToRgba()
        return SkiaRenderResult(
            rgba = result.rgba,
            width = width,
            height = height,
            dispatchedCount = result.dispatchedCount,
            refusedCount = result.refusedCount,
            diagnostics = result.diagnostics,
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
