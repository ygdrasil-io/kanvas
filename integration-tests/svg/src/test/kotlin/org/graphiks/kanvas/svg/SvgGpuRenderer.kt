package org.graphiks.kanvas.svg

import org.graphiks.kanvas.Canvas
import org.graphiks.kanvas.Surface

/**
 * Renderer GPU dédié pour SVG, utilisant Kanvas + wgpu4k.
 * S'inspire de RectOnlyOffscreenRenderer.kt dans gpu-renderer-scenes.
 */
object SvgGpuRenderer {
    private const val DEFAULT_WIDTH = 800
    private const val DEFAULT_HEIGHT = 600

    /**
     * Rend un SVG parsé via Kanvas + wgpu4k et retourne un ByteArray RGBA.
     * Utilise Surface.renderToRgba() qui utilise wgpu4k en interne.
     */
    fun renderToRgba(
        svg: Svg,
        width: Int = DEFAULT_WIDTH,
        height: Int = DEFAULT_HEIGHT
    ): Triple<ByteArray, Int, Int> {
        val surface = Surface(width = width, height = height)
        val canvas = Canvas(surface)
        val renderer = SvgRenderer(canvas)
        renderer.render(svg)
        val result = surface.renderToRgba()
        return Triple(result.rgba, width, height)
    }

    /**
     * Rend un SVG à partir de son contenu (String) et retourne un ByteArray RGBA.
     * Parse d'abord le SVG, puis le rend via renderToRgba().
     */
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
