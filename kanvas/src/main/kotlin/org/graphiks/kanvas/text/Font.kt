package org.graphiks.kanvas.text

import org.graphiks.kanvas.types.Point

interface FontMetricsProvider {
    fun getMetrics(size: Float): FontMetrics?
}

/** Requested glyph edge treatment, matching Skia's `SkFont::Edging` vocabulary. */
enum class FontEdging { ALIAS, ANTI_ALIAS, SUBPIXEL_ANTI_ALIAS }

/** Requested glyph hinting strength, matching Skia's `SkFontHinting` vocabulary. */
enum class FontHinting { NONE, SLIGHT, NORMAL, FULL }

data class Font(
    val typeface: Typeface,
    val size: Float = 12f,
    val antiAlias: Boolean = true,
    val subpixel: Boolean = true,
    val isEmbolden: Boolean = false,  // applied per-codepoint; ZWJ clusters get multiple increments
    /** OpenType design coordinates, keyed by their four-character axis tag (for example `wght`). */
    val variationCoordinates: Map<String, Float> = emptyMap(),
    val edging: FontEdging = if (!antiAlias) FontEdging.ALIAS else if (subpixel) {
        FontEdging.SUBPIXEL_ANTI_ALIAS
    } else {
        FontEdging.ANTI_ALIAS
    },
    val hinting: FontHinting = FontHinting.NORMAL,
    val embeddedBitmaps: Boolean = true,
) {
    init {
        require(size.isFinite() && size >= 0f) { "Font size must be finite and non-negative." }
        variationCoordinates.forEach { (tag, value) ->
            require(tag.length == 4 && tag.all { it.code in 0x20..0x7e }) {
                "Variation axis tag must be four printable ASCII characters: $tag"
            }
            require(value.isFinite()) { "Variation coordinate for $tag must be finite." }
        }
    }
    fun getMetrics(): FontMetrics? {
        if (typeface is FontTypeface) {
            val scaler = typeface.scaler ?: return null
            val scale = size / scaler.unitsPerEmInt.toFloat()
            return FontMetrics(
                ascent = scaler.hheaAscent * scale,
                descent = scaler.hheaDescent * scale,
                leading = scaler.hheaLineGap * scale,
            )
        }
        return (typeface as? FontMetricsProvider)?.getMetrics(size)
    }

    fun toTextBlob(str: String, originX: Float, originY: Float): TextBlob {
        val glyphIds = mutableListOf<UShort>()
        val positions = mutableListOf<Point>()
        var cursorX = originX
        for (cp in str.codePoints()) {
            val gid = typeface.glyphIdForCodepoint(cp)
            glyphIds.add(gid.toUShort())
            positions.add(Point(cursorX, originY))
            cursorX += typeface.getAdvance(gid, size, variationCoordinates)
        }
        return TextBlob(
            glyphRuns = listOf(KanvasGlyphRun(glyphIds, positions, fontSize = size)),
            typeface = typeface,
            fontSize = size,
            variationCoordinates = variationCoordinates,
        )
    }

    fun measureText(str: String): Float {
        var width = 0f
        for (cp in str.codePoints()) {
            val gid = typeface.glyphIdForCodepoint(cp)
            width += typeface.getAdvance(gid, size, variationCoordinates)
        }
        if (isEmbolden) width += str.codePoints().count().toFloat() * size * 0.02f
        return width
    }

    fun getGlyphWidths(str: String): List<Float> {
        val widths = mutableListOf<Float>()
        for (cp in str.codePoints()) {
            val gid = typeface.glyphIdForCodepoint(cp)
            widths.add(typeface.getAdvance(gid, size, variationCoordinates))
        }
        return widths
    }
}
