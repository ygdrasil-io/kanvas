package org.graphiks.kanvas.text

import org.graphiks.kanvas.types.Point

data class Font(
    val typeface: Typeface,
    val size: Float = 12f,
    val antiAlias: Boolean = true,
    val subpixel: Boolean = true,
    val isEmbolden: Boolean = false,
) {
    fun getMetrics(): FontMetrics? {
        if (typeface !is FontTypeface) return null
        val scaler = typeface.scaler ?: return null
        val scale = size / scaler.unitsPerEmInt.toFloat()
        return FontMetrics(
            ascent = scaler.hheaAscent * scale,
            descent = scaler.hheaDescent * scale,
            leading = scaler.hheaLineGap * scale,
        )
    }

    fun toTextBlob(str: String, originX: Float, originY: Float): TextBlob {
        val glyphIds = mutableListOf<UShort>()
        val positions = mutableListOf<Point>()
        var cursorX = originX
        for (cp in str.codePoints()) {
            val gid = typeface.glyphIdForCodepoint(cp)
            glyphIds.add(gid.toUShort())
            positions.add(Point(cursorX, originY))
            cursorX += typeface.getAdvance(gid, size)
        }
        return TextBlob(
            glyphRuns = listOf(KanvasGlyphRun(glyphIds, positions)),
            typeface = typeface,
            fontSize = size,
        )
    }

    fun measureText(str: String): Float {
        var width = 0f
        for (cp in str.codePoints()) {
            val gid = typeface.glyphIdForCodepoint(cp)
            width += typeface.getAdvance(gid, size)
        }
        if (isEmbolden) width += str.codePoints().count().toFloat() * size * 0.02f
        return width
    }

    fun getGlyphWidths(str: String): List<Float> {
        val widths = mutableListOf<Float>()
        for (cp in str.codePoints()) {
            val gid = typeface.glyphIdForCodepoint(cp)
            widths.add(typeface.getAdvance(gid, size))
        }
        return widths
    }
}
