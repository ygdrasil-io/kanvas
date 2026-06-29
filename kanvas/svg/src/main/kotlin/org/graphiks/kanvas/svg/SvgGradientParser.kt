package org.graphiks.kanvas.svg

import org.graphiks.kanvas.KanvasPoint
import org.graphiks.kanvas.Shader

class SvgGradientParser {
    fun parseLinearGradient(
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        stops: List<SvgStop>,
    ): Shader.LinearGradient {
        val colorStops = stops.map { stop ->
            val color = SvgPaintParser().parseColor(stop.stopColor)
            val r = (color shr 16 and 0xFF) / 255f
            val g = (color shr 8 and 0xFF) / 255f
            val b = (color and 0xFF) / 255f
            val offset = stop.offset ?: 0f
            Quadruple(offset, r, g, b)
        }
        return Shader.LinearGradient(
            start = KanvasPoint(x1, y1),
            end = KanvasPoint(x2, y2),
            stops = colorStops.map { Triple(it.second, it.third, it.fourth) },
            positions = colorStops.map { it.first },
        )
    }

    fun parseRadialGradient(
        cx: Float, cy: Float, r: Float,
        stops: List<SvgStop>,
    ): Shader.RadialGradient {
        val colorStops = stops.map { stop ->
            val color = SvgPaintParser().parseColor(stop.stopColor)
            val r = (color shr 16 and 0xFF) / 255f
            val g = (color shr 8 and 0xFF) / 255f
            val b = (color and 0xFF) / 255f
            val offset = stop.offset ?: 0f
            Quadruple(offset, r, g, b)
        }
        return Shader.RadialGradient(
            center = KanvasPoint(cx, cy),
            radius = r,
            stops = colorStops.map { Triple(it.second, it.third, it.fourth) },
            positions = colorStops.map { it.first },
        )
    }
}

data class Quadruple<out A, out B, out C, out D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)
