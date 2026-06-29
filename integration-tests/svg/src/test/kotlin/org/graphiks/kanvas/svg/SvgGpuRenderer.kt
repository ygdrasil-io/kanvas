package org.graphiks.kanvas.svg

import org.graphiks.kanvas.Canvas
import org.graphiks.kanvas.Surface

object SvgGpuRenderer {
    private const val DEFAULT_WIDTH = 800
    private const val DEFAULT_HEIGHT = 600
    private const val PATH_VERTEX_BUDGET = 16384

    fun renderToRgba(
        svg: Svg,
        width: Int = DEFAULT_WIDTH,
        height: Int = DEFAULT_HEIGHT
    ): Triple<ByteArray, Int, Int> {
        val surface = Surface(width = width, height = height)
        val canvas = Canvas(surface, maxPathVertices = PATH_VERTEX_BUDGET)
        val renderer = SvgRenderer(canvas)
        renderer.render(svg)
        val result = surface.renderToRgba()
        return Triple(result.rgba, width, height)
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
