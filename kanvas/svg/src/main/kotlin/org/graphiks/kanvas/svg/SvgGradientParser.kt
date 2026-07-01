package org.graphiks.kanvas.svg

import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point

class SvgGradientParser {
    private val paintParser = SvgPaintParser()

    fun parseLinearGradient(
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        stops: List<SvgStop>,
    ): Shader.LinearGradient {
        val gradientStops = stops.map { stop ->
            val colorInt = paintParser.parseColor(stop.stopColor)
            val r = (colorInt shr 16 and 0xFF) / 255f
            val g = (colorInt shr 8 and 0xFF) / 255f
            val b = (colorInt and 0xFF) / 255f
            GradientStop(stop.offset ?: 0f, Color.fromRGBA(r, g, b))
        }
        return Shader.LinearGradient(
            start = Point(x1, y1),
            end = Point(x2, y2),
            stops = gradientStops,
        )
    }

    fun parseRadialGradient(
        cx: Float, cy: Float, r: Float,
        stops: List<SvgStop>,
    ): Shader.RadialGradient {
        val gradientStops = stops.map { stop ->
            val colorInt = paintParser.parseColor(stop.stopColor)
            val r = (colorInt shr 16 and 0xFF) / 255f
            val g = (colorInt shr 8 and 0xFF) / 255f
            val b = (colorInt and 0xFF) / 255f
            GradientStop(stop.offset ?: 0f, Color.fromRGBA(r, g, b))
        }
        return Shader.RadialGradient(
            center = Point(cx, cy),
            radius = r,
            stops = gradientStops,
        )
    }
}
