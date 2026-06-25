package org.graphiks.kanvas.gpu.renderer.text

import org.graphiks.kanvas.font.atlas.GlyphAtlasUploadPlan
import org.graphiks.kanvas.font.atlas.GlyphAtlasUploadPlanner
import org.graphiks.kanvas.font.glyph.A8Bitmap
import org.graphiks.kanvas.font.glyph.A8Rasterizer
import org.graphiks.kanvas.font.glyph.GlyphStrikeKey
import org.graphiks.kanvas.font.scaler.GlyphScaler

/** Real A8 glyph atlas texture built from a font glyph set, ready for GPU upload as R8Unorm. */
data class GlyphAtlasTexture(
    val a8Bytes: ByteArray,
    val width: Int,
    val height: Int,
    val glyphCount: Int,
    val fontFamily: String,
    val evidenceDumpLines: List<String>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GlyphAtlasTexture) return false
        return width == other.width && height == other.height &&
            glyphCount == other.glyphCount && a8Bytes.contentEquals(other.a8Bytes)
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + glyphCount
        result = 31 * result + a8Bytes.contentHashCode()
        return result
    }
}

/** Sealed result distinguishing a built atlas from a dependency-gated refusal. */
sealed interface GlyphAtlasTextureResult {
    /** Successful atlas build with packed A8 bytes and evidence. */
    data class Built(val atlas: GlyphAtlasTexture) : GlyphAtlasTextureResult
    /** Refusal reason when atlas building is dependency-gated or resource-missing. */
    data class Refused(val reason: String) : GlyphAtlasTextureResult
}

/**
 * KGPU-M26-003: builds a real A8 glyph atlas from the Liberation Sans font (M12 font stack):
 * scale glyphs via [GlyphScaler], rasterize coverage via [A8Rasterizer], and pack via
 * [GlyphAtlasUploadPlanner]. The returned A8 bytes are uploaded to a real GPU texture by the
 * offscreen backend, replacing the M25 procedural glyph coverage.
 */
class GlyphAtlasTextureBuilder(
    private val fontResourcePath: String = "/fonts/liberation/LiberationSans-Regular.ttf",
    private val fontFamily: String = "Liberation Sans",
) {
    /** Builds a real A8 glyph atlas from the Liberation Sans font for the given text glyph set. */
    fun build(text: String, fontSize: Float): GlyphAtlasTextureResult {
        val fontBytes = javaClass.getResourceAsStream(fontResourcePath)?.readBytes()
            ?: return GlyphAtlasTextureResult.Refused("font-resource-missing:$fontResourcePath")

        val scaler = GlyphScaler.fromBytes(fontBytes)
        val entries = mutableListOf<Pair<GlyphStrikeKey, A8Bitmap>>()
        val rasterizer = A8Rasterizer()
        val seen = mutableSetOf<Int>()
        for (ch in text) {
            val codepoint = ch.code
            if (ch == ' ' || codepoint in seen) continue
            seen += codepoint
            val glyphId = scaler.glyphIdForCodepoint(codepoint) ?: continue
            val scaled = scaler.scaleGlyph(glyphId = glyphId, size = fontSize, sourceCodepoint = codepoint)
            val bitmap = rasterizer.rasterize(scaled) ?: continue
            entries += GlyphStrikeKey(glyphId = glyphId, size = fontSize, subpixelX = 0, subpixelY = 0) to bitmap
        }

        if (entries.isEmpty()) {
            return GlyphAtlasTextureResult.Refused("no-rasterized-glyphs:text=$text")
        }

        val plan = GlyphAtlasUploadPlanner().plan(entries)
        return when (plan) {
            is GlyphAtlasUploadPlan.Refused -> GlyphAtlasTextureResult.Refused("atlas-plan-refused:${plan.reason}")
            is GlyphAtlasUploadPlan.Accepted -> {
                val evidence = listOf(
                    "glyphAtlasTexture:fontFamily=$fontFamily",
                    "glyphAtlasTexture:fontSize=$fontSize",
                    "glyphAtlasTexture:glyphCount=${entries.size}",
                    "glyphAtlasTexture:atlasDimensions=${plan.atlasWidth}x${plan.atlasHeight}",
                    "glyphAtlasTexture:format=R8Unorm",
                    "glyphAtlasTexture:byteCount=${plan.atlasBytes.size}",
                    "glyphAtlasTexture:nonClaim=no-sdf-no-subpixel",
                )
                GlyphAtlasTextureResult.Built(
                    GlyphAtlasTexture(
                        a8Bytes = plan.atlasBytes,
                        width = plan.atlasWidth,
                        height = plan.atlasHeight,
                        glyphCount = entries.size,
                        fontFamily = fontFamily,
                        evidenceDumpLines = evidence,
                    ),
                )
            }
        }
    }
}
