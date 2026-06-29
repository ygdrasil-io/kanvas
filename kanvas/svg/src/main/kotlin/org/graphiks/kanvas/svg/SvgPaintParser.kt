package org.graphiks.kanvas.svg

import org.graphiks.kanvas.Paint
import org.graphiks.kanvas.PaintStyle

class SvgPaintParser {
    private var gradientMap: Map<String, org.graphiks.kanvas.Shader> = emptyMap()

    fun setGradientMap(map: Map<String, org.graphiks.kanvas.Shader>) {
        this.gradientMap = map
    }

    fun parseFill(fill: String?, opacity: Float = 1f): Paint {
        if (fill == null) {
            val paint = Paint()
            paint.a = opacity
            paint.style = PaintStyle.FILL
            return paint
        }
        
        if (fill.startsWith("url(") && fill.endsWith(")")) {
            val id = fill.substring(5, fill.length - 1).trim()
            val shader = gradientMap[id]
            if (shader != null) {
                val paint = Paint()
                paint.shader = shader
                paint.a = opacity
                paint.style = PaintStyle.FILL
                return paint
            }
        }
        
        val paint = Paint()
        val color = parseColor(fill)
        paint.r = (color shr 16 and 0xFF) / 255f
        paint.g = (color shr 8 and 0xFF) / 255f
        paint.b = (color and 0xFF) / 255f
        paint.a = opacity
        paint.style = PaintStyle.FILL
        return paint
    }

    fun parseStroke(
        stroke: String? = null,
        strokeWidth: Float? = null,
        strokeOpacity: Float = 1f,
    ): Paint {
        if (stroke == null) {
            val paint = Paint()
            paint.strokeWidth = strokeWidth ?: 1f
            paint.a = strokeOpacity
            paint.style = PaintStyle.STROKE
            return paint
        }
        
        if (stroke.startsWith("url(") && stroke.endsWith(")")) {
            val id = stroke.substring(5, stroke.length - 1).trim()
            val shader = gradientMap[id]
            if (shader != null) {
                val paint = Paint()
                paint.shader = shader
                paint.strokeWidth = strokeWidth ?: 1f
                paint.a = strokeOpacity
                paint.style = PaintStyle.STROKE
                return paint
            }
        }
        
        val paint = Paint()
        val color = parseColor(stroke)
        paint.r = (color shr 16 and 0xFF) / 255f
        paint.g = (color shr 8 and 0xFF) / 255f
        paint.b = (color and 0xFF) / 255f
        paint.strokeWidth = strokeWidth ?: 1f
        paint.a = strokeOpacity
        paint.style = PaintStyle.STROKE
        return paint
    }

    fun parseColor(color: String): Int {
        return when {
            color.startsWith("#") -> parseHexColor(color)
            color.startsWith("rgb(") -> parseRgbColor(color)
            color.startsWith("rgba(") -> parseRgbaColor(color)
            else -> parseNamedColor(color)
        }
    }

    private fun parseHexColor(color: String): Int {
        val hex = color.substring(1)
        return when (hex.length) {
            3 -> {
                val r = hex.substring(0, 1).repeat(2).toInt(16)
                val g = hex.substring(1, 2).repeat(2).toInt(16)
                val b = hex.substring(2, 3).repeat(2).toInt(16)
                (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            6 -> {
                val r = hex.substring(0, 2).toInt(16)
                val g = hex.substring(2, 4).toInt(16)
                val b = hex.substring(4, 6).toInt(16)
                (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            else -> 0xFF000000.toInt()
        }
    }

    private fun parseRgbColor(color: String): Int {
        val values = color.substring(4, color.length - 1)
            .split(",")
            .map { it.trim().toIntOrNull() ?: 0 }
        val r = values.getOrElse(0) { 0 }
        val g = values.getOrElse(1) { 0 }
        val b = values.getOrElse(2) { 0 }
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun parseRgbaColor(color: String): Int {
        val values = color.substring(5, color.length - 1)
            .split(",")
            .map { it.trim() }
        val r = values.getOrElse(0) { "0" }.toIntOrNull() ?: 0
        val g = values.getOrElse(1) { "0" }.toIntOrNull() ?: 0
        val b = values.getOrElse(2) { "0" }.toIntOrNull() ?: 0
        val a = (values.getOrElse(3) { "1" }.toFloatOrNull() ?: 1f) * 255
        return (a.toInt() shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun parseNamedColor(color: String): Int {
        val namedColors = mapOf(
            "black" to 0xFF000000.toInt(),
            "white" to 0xFFFFFFFF.toInt(),
            "red" to 0xFFFF0000.toInt(),
            "green" to 0xFF00FF00.toInt(),
            "blue" to 0xFF0000FF.toInt(),
            "yellow" to 0xFFFFFF00.toInt(),
            "cyan" to 0xFF00FFFF.toInt(),
            "magenta" to 0xFFFF00FF.toInt(),
            "gray" to 0xFF808080.toInt(),
            "grey" to 0xFF808080.toInt(),
            "silver" to 0xFFC0C0C0.toInt(),
            "maroon" to 0xFF800000.toInt(),
            "olive" to 0xFF808000.toInt(),
            "purple" to 0xFF800080.toInt(),
            "teal" to 0xFF008080.toInt(),
            "navy" to 0xFF000080.toInt(),
        )
        return namedColors[color.lowercase()] ?: 0xFF000000.toInt()
    }

    fun parseOpacity(opacity: Float?): Float {
        return opacity ?: 1f
    }
}
