package org.graphiks.kanvas.svg

import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.surface.Surface

object SvgGpuRenderer {
    private const val DEFAULT_WIDTH = 800
    private const val DEFAULT_HEIGHT = 600

    fun renderToRgba(
        svg: Svg,
        width: Int = DEFAULT_WIDTH,
        height: Int = DEFAULT_HEIGHT
    ): Triple<ByteArray, Int, Int> {
        val surface = Surface(width = width, height = height)
        val canvas = surface.canvas()
        val renderer = SvgRenderer(canvas, targetWidth = width.toFloat(), targetHeight = height.toFloat())
        renderer.render(svg)
        val result = surface.render()
        return Triple(result.pixels.map { it.toByte() }.toByteArray(), width, height)
    }

    fun renderSvgContentToRgba(
        svgContent: String,
        width: Int = DEFAULT_WIDTH,
        height: Int = DEFAULT_HEIGHT
    ): Triple<ByteArray, Int, Int> {
        val parser = SvgParser()
        val svg = parser.parse(svgContent)
        return renderToRgba(svg, width, height)
    }
}
