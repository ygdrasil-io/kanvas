package org.graphiks.kanvas.skia

import org.graphiks.kanvas.RenderOptions
import org.graphiks.kanvas.Canvas
import org.graphiks.kanvas.Surface

object SkiaGmRenderer {
    private const val DEFAULT_WIDTH = 800
    private const val DEFAULT_HEIGHT = 600
    private const val PATH_VERTEX_BUDGET = 16384

    fun render(gm: SkiaGm, width: Int = gm.width, height: Int = gm.height): SkiaRenderResult {
        val surface = Surface(width = width, height = height)
        val canvas = Canvas(surface, RenderOptions(maxPathVertices = PATH_VERTEX_BUDGET))
        gm.draw(canvas, width, height)
        val result = surface.renderToRgba()
        return SkiaRenderResult(
            rgba = result.rgba,
            width = width,
            height = height,
        )
    }
}

data class SkiaRenderResult(
    val rgba: ByteArray,
    val width: Int,
    val height: Int,
)
