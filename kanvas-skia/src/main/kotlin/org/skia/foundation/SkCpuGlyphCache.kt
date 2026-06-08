package org.skia.foundation

import org.graphiks.math.SkIRect
import org.graphiks.math.SkRect
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

public data class SkCpuGlyphCache(
    public val scopeId: String,
    public val fontSourceId: String,
    public val fontFamily: String,
    public val fontStyle: String,
    public val size: Float,
    public val scaleX: Float,
    public val skewX: Float,
    public val representation: String,
    public val text: String,
    public val inventory: List<SkCpuGlyphInventoryItem>,
    public val glyphs: List<SkCpuGlyphRecord>,
    public val diagnostics: List<String>,
) {
    public val dumpSha256: String by lazy {
        sha256(canonicalJson(includeDumpSha256 = false).toByteArray(Charsets.UTF_8))
    }

    public fun toJson(): String = canonicalJson(includeDumpSha256 = true)

    private fun canonicalJson(includeDumpSha256: Boolean): String = buildString {
        append("{\n")
        appendJsonField("scopeId", scopeId, comma = true)
        appendJsonField("fontSourceId", fontSourceId, comma = true)
        appendJsonField("fontFamily", fontFamily, comma = true)
        appendJsonField("fontStyle", fontStyle, comma = true)
        appendJsonField("size", size, comma = true)
        appendJsonField("scaleX", scaleX, comma = true)
        appendJsonField("skewX", skewX, comma = true)
        appendJsonField("representation", representation, comma = true)
        appendJsonField("text", text, comma = true)
        appendJsonField("inventoryCount", inventory.size, comma = true)
        appendJsonField("glyphCount", glyphs.size, comma = true)
        append("  \"diagnostics\": [")
        append(diagnostics.joinToString(", ") { jsonString(it) })
        append("],\n")
        append("  \"inventory\": [\n")
        append(inventory.joinToString(",\n") { it.toJson().prependIndent("    ") })
        append("\n  ],\n")
        append("  \"glyphs\": [\n")
        append(glyphs.joinToString(",\n") { it.toJson().prependIndent("    ") })
        append("\n  ]")
        if (includeDumpSha256) {
            append(",\n")
            appendJsonField("dumpSha256", dumpSha256, comma = false)
            append("\n")
        } else {
            append("\n")
        }
        append("}\n")
    }

    public companion object {
        public const val AlphaMaskRepresentation: String = "font.glyph.alpha-mask"
        public const val MissingGlyphDiagnostic: String = "font.missing-glyph.notdef-used"

        public fun build(
            scopeId: String,
            fontSourceId: String,
            font: SkFont,
            text: String,
        ): SkCpuGlyphCache {
            val codePoints = text.codePoints().toArray()
            val glyphIds = font.textToGlyphs(text)
            val xPositions = font.getXPos(glyphIds)
            val byKey = LinkedHashMap<String, MutableGlyphRecord>()
            val inventory = ArrayList<SkCpuGlyphInventoryItem>(codePoints.size)
            val diagnostics = linkedSetOf<String>()

            for (i in codePoints.indices) {
                val codePoint = codePoints[i]
                val glyphId = glyphIds[i]
                val key = glyphKey(scopeId, fontSourceId, font, glyphId)
                val diagnostic = if (glyphId == 0) MissingGlyphDiagnostic else null
                if (diagnostic != null) diagnostics += diagnostic

                val record = byKey.getOrPut(key) {
                    MutableGlyphRecord(
                        key = key,
                        glyphId = glyphId,
                        codePoints = linkedSetOf(),
                        advance = font.getWidth(glyphId),
                        bounds = font.getBounds(glyphId),
                        mask = rasterizeMask(font, glyphId),
                        diagnostic = diagnostic,
                    )
                }
                record.codePoints += codePoint
                if (record.diagnostic == null && diagnostic != null) {
                    record.diagnostic = diagnostic
                }
                inventory += SkCpuGlyphInventoryItem(
                    index = i,
                    codePoint = codePoint,
                    glyphId = glyphId,
                    key = key,
                    advance = font.getWidth(glyphId),
                    x = xPositions.getOrElse(i) { 0f },
                    diagnostic = diagnostic,
                )
            }

            return SkCpuGlyphCache(
                scopeId = scopeId,
                fontSourceId = fontSourceId,
                fontFamily = font.typeface.getFamilyName(),
                fontStyle = font.typeface.fontStyle.toString(),
                size = font.size,
                scaleX = font.scaleX,
                skewX = font.skewX,
                representation = AlphaMaskRepresentation,
                text = text,
                inventory = inventory,
                glyphs = byKey.values.map { it.freeze() },
                diagnostics = diagnostics.toList(),
            )
        }

        private fun glyphKey(
            scopeId: String,
            fontSourceId: String,
            font: SkFont,
            glyphId: Int,
        ): String = listOf(
            scopeId,
            fontSourceId,
            "family=${font.typeface.getFamilyName()}",
            "style=${font.typeface.fontStyle}",
            "size=${floatToken(font.size)}",
            "scaleX=${floatToken(font.scaleX)}",
            "skewX=${floatToken(font.skewX)}",
            "edging=${font.edging.name}",
            "glyph=$glyphId",
        ).joinToString("|")

        private fun rasterizeMask(font: SkFont, glyphId: Int): SkCpuGlyphMask {
            val path = font.getPath(glyphId) ?: return SkCpuGlyphMask.Empty
            if (path.isEmpty()) return SkCpuGlyphMask.Empty
            val bounds = path.computeTightBounds()
            if (bounds.isEmpty) return SkCpuGlyphMask.Empty

            val clipRect = bounds.roundOutForMask()
            if (clipRect.isEmpty) return SkCpuGlyphMask.Empty

            val clip = SkRegion(clipRect)
            val aa = SkAAClip()
            if (!aa.setPath(path, clip, font.edging != SkFont.Edging.kAlias)) {
                return SkCpuGlyphMask.Empty
            }

            val maskBounds = aa.getBounds()
            if (maskBounds.isEmpty) return SkCpuGlyphMask.Empty
            val width = maskBounds.width()
            val height = maskBounds.height()
            val pixels = ByteArray(width * height)
            var nonZero = 0
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val alpha = aa.coverage(maskBounds.left + x, maskBounds.top + y)
                    if (alpha != 0) nonZero += 1
                    pixels[y * width + x] = alpha.toByte()
                }
            }
            return SkCpuGlyphMask(
                left = maskBounds.left,
                top = maskBounds.top,
                width = width,
                height = height,
                pixels = pixels,
                nonZeroPixels = nonZero,
                sha256 = sha256(pixels),
            )
        }
    }
}

public data class SkCpuGlyphInventoryItem(
    public val index: Int,
    public val codePoint: Int,
    public val glyphId: Int,
    public val key: String,
    public val advance: Float,
    public val x: Float,
    public val diagnostic: String?,
) {
    internal fun toJson(): String = buildString {
        append("{\n")
        appendJsonField("index", index, comma = true)
        appendJsonField("codePoint", codePointLabel(codePoint), comma = true)
        appendJsonField("codePointValue", codePoint, comma = true)
        appendJsonField("glyphId", glyphId, comma = true)
        appendJsonField("key", key, comma = true)
        appendJsonField("advance", advance, comma = true)
        appendJsonField("x", x, comma = true)
        appendJsonNullableField("diagnostic", diagnostic, comma = false)
        append("\n}")
    }
}

public data class SkCpuGlyphRecord(
    public val key: String,
    public val glyphId: Int,
    public val codePoints: List<Int>,
    public val advance: Float,
    public val bounds: SkRect,
    public val mask: SkCpuGlyphMask,
    public val diagnostic: String?,
) {
    public val codePoint: Int get() = codePoints.firstOrNull() ?: 0

    internal fun toJson(): String = buildString {
        append("{\n")
        appendJsonField("key", key, comma = true)
        appendJsonField("glyphId", glyphId, comma = true)
        append("  \"codePoints\": [")
        append(codePoints.joinToString(", ") { jsonString(codePointLabel(it)) })
        append("],\n")
        appendJsonField("advance", advance, comma = true)
        append("  \"bounds\": ")
        appendRect(bounds)
        append(",\n")
        append("  \"mask\": ")
        append(mask.toJson().prependIndent("  ").trimStart())
        append(",\n")
        appendJsonNullableField("diagnostic", diagnostic, comma = false)
        append("\n}")
    }
}

public class SkCpuGlyphMask(
    public val left: Int,
    public val top: Int,
    public val width: Int,
    public val height: Int,
    public val pixels: ByteArray,
    public val nonZeroPixels: Int,
    public val sha256: String,
) {
    internal fun toJson(): String = buildString {
        append("{\n")
        appendJsonField("left", left, comma = true)
        appendJsonField("top", top, comma = true)
        appendJsonField("width", width, comma = true)
        appendJsonField("height", height, comma = true)
        appendJsonField("nonZeroPixels", nonZeroPixels, comma = true)
        appendJsonField("sha256", sha256, comma = true)
        append("  \"pixels\": [")
        append(pixels.joinToString(", ") { (it.toInt() and 0xFF).toString() })
        append("]\n")
        append("}")
    }

    override fun equals(other: Any?): Boolean =
        other is SkCpuGlyphMask &&
            left == other.left &&
            top == other.top &&
            width == other.width &&
            height == other.height &&
            pixels.contentEquals(other.pixels) &&
            nonZeroPixels == other.nonZeroPixels &&
            sha256 == other.sha256

    override fun hashCode(): Int {
        var result = left
        result = 31 * result + top
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + pixels.contentHashCode()
        result = 31 * result + nonZeroPixels
        result = 31 * result + sha256.hashCode()
        return result
    }

    override fun toString(): String =
        "SkCpuGlyphMask(left=$left, top=$top, width=$width, height=$height, " +
            "pixels=${pixels.size} bytes, nonZeroPixels=$nonZeroPixels, sha256=$sha256)"

    public companion object {
        public const val EmptyHash: String = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

        public val Empty: SkCpuGlyphMask = SkCpuGlyphMask(
            left = 0,
            top = 0,
            width = 0,
            height = 0,
            pixels = ByteArray(0),
            nonZeroPixels = 0,
            sha256 = EmptyHash,
        )
    }
}

private data class MutableGlyphRecord(
    val key: String,
    val glyphId: Int,
    val codePoints: LinkedHashSet<Int>,
    val advance: Float,
    val bounds: SkRect,
    val mask: SkCpuGlyphMask,
    var diagnostic: String?,
) {
    fun freeze(): SkCpuGlyphRecord = SkCpuGlyphRecord(
        key = key,
        glyphId = glyphId,
        codePoints = codePoints.toList(),
        advance = advance,
        bounds = bounds,
        mask = mask,
        diagnostic = diagnostic,
    )
}

private fun SkRect.roundOutForMask(): SkIRect = SkIRect(
    floor(left.toDouble()).toInt(),
    floor(top.toDouble()).toInt(),
    ceil(right.toDouble()).toInt(),
    ceil(bottom.toDouble()).toInt(),
)

private fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xFF) }

private fun StringBuilder.appendJsonField(name: String, value: String, comma: Boolean) {
    append("  ").append(jsonString(name)).append(": ").append(jsonString(value))
    if (comma) append(",")
    append("\n")
}

private fun StringBuilder.appendJsonNullableField(name: String, value: String?, comma: Boolean) {
    append("  ").append(jsonString(name)).append(": ")
    append(if (value == null) "null" else jsonString(value))
    if (comma) append(",")
}

private fun StringBuilder.appendJsonField(name: String, value: Int, comma: Boolean) {
    append("  ").append(jsonString(name)).append(": ").append(value)
    if (comma) append(",")
    append("\n")
}

private fun StringBuilder.appendJsonField(name: String, value: Float, comma: Boolean) {
    append("  ").append(jsonString(name)).append(": ").append(floatToken(value))
    if (comma) append(",")
    append("\n")
}

private fun StringBuilder.appendRect(rect: SkRect) {
    append("{")
    append(jsonString("left")).append(": ").append(floatToken(rect.left)).append(", ")
    append(jsonString("top")).append(": ").append(floatToken(rect.top)).append(", ")
    append(jsonString("right")).append(": ").append(floatToken(rect.right)).append(", ")
    append(jsonString("bottom")).append(": ").append(floatToken(rect.bottom))
    append("}")
}

private fun jsonString(value: String): String = buildString {
    append('"')
    for (ch in value) {
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> {
                if (ch.code < 0x20 || ch.code > 0x7E) {
                    append("\\u")
                    append(ch.code.toString(16).padStart(4, '0'))
                } else {
                    append(ch)
                }
            }
        }
    }
    append('"')
}

private fun codePointLabel(codePoint: Int): String =
    "U+${codePoint.toString(16).uppercase().padStart(4, '0')}"

private fun floatToken(value: Float): String =
    String.format(Locale.US, "%.6f", value).trimEnd('0').trimEnd('.').ifEmpty { "0" }
