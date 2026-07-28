package org.graphiks.kanvas.glyph

import java.security.MessageDigest
import java.util.Locale

/**
 * Blur style categories recognized by the glyph mask pipeline.
 */
enum class GlyphMaskBlurStyle { NORMAL, SOLID, OUTER, INNER }

/**
 * Deterministic blur parameters included in [GlyphMaskKey].
 *
 * @property style category of blur to apply.
 * @property sigma blur radius parameter in device-independent pixels.
 * @property rasterScaleX horizontal raster scale at blur-time.
 * @property rasterScaleY vertical raster scale at blur-time.
 */
data class GlyphMaskBlurKey(
    val style: GlyphMaskBlurStyle,
    val sigma: Float,
    val rasterScaleX: Float,
    val rasterScaleY: Float,
) {
    init {
        require(sigma >= 0f) { "Blur sigma must be non-negative, but was $sigma." }
        require(sigma.isFinite()) { "Blur sigma must be finite, but was $sigma." }
        require(rasterScaleX.isFinite() && rasterScaleX > 0f) {
            "Raster scale X must be finite and positive, but was $rasterScaleX."
        }
        require(rasterScaleY.isFinite() && rasterScaleY > 0f) {
            "Raster scale Y must be finite and positive, but was $rasterScaleY."
        }
    }
}

/**
 * Canonical exact glyph mask lookup key.
 *
 * Every field participates in equality and hashing. Two masks produced from
 * different face indices, subpixel offsets, palette identities, variation
 * coordinates, source outline content, rasterizer version, or blur parameters
 * are never interchangeable.
 *
 * @property strikeKey the strike-level cache key with typeface, size, scale,
 * subpixel, variation, palette, and route facts.
 * @property faceIndex zero-based font face index within a font collection.
 * @property sourceOutlineSha256 lowercase hexadecimal SHA-256 digest over the
 * source outline representation used to produce the mask.
 * @property rasterizerVersion stable rasterizer version identifier that
 * changes when the coverage algorithm is updated.
 * @property blur optional blur parameters applied to the mask before coloring.
 */
data class GlyphMaskKey(
    val strikeKey: GlyphStrikeKey,
    val faceIndex: Int,
    val sourceOutlineSha256: String,
    val rasterizerVersion: String = "a8-nonzero-4x4-v1",
    val blur: GlyphMaskBlurKey? = null,
) {
    private val strikeKeySha256: String =
        strikeKey.preimageSha256(strikeKey.glyphId ?: 0)

    init {
        require(faceIndex >= 0) { "Face index must be non-negative, but was $faceIndex." }
        require(sourceOutlineSha256.length == 64) {
            "Source outline SHA-256 must be 64 lowercase hex characters, but was ${sourceOutlineSha256.length}."
        }
        require(sourceOutlineSha256.all { it in '0'..'9' || it in 'a'..'f' }) {
            "Source outline SHA-256 must be lowercase hexadecimal."
        }
    }

    /**
     * Stable JSON-ordered canonical preimage string that deterministically
     * separates every combination of face, subpixel, variation, palette,
     * outline content, rasterizer version, and blur.
     */
    fun canonicalPreimage(): String = buildString {
        append("{")
        append("\"strikeKey\": ").append(strikeKeySha256)
        append(", \"faceIndex\": ").append(faceIndex)
        append(", \"sourceOutlineSha256\": \"").append(sourceOutlineSha256).append("\"")
        append(", \"rasterizerVersion\": \"").append(rasterizerVersion).append("\"")
        if (blur != null) {
            append(", \"blur\": {")
            append("\"style\": \"").append(blur.style.name).append("\"")
            append(", \"sigma\": ").append(blur.sigma.toBits())
            append(", \"rasterScaleX\": ").append(blur.rasterScaleX.toBits())
            append(", \"rasterScaleY\": ").append(blur.rasterScaleY.toBits())
            append("}")
        } else {
            append(", \"blur\": null")
        }
        append("}")
    }

    /**
     * Lowercase hexadecimal SHA-256 digest of [canonicalPreimage].
     */
    fun sha256(): String =
        keySha256(canonicalPreimage().toByteArray(Charsets.UTF_8))

    companion object {
        private fun keySha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte ->
                String.format(Locale.ROOT, "%02x", byte.toInt() and 0xFF)
            }
    }
}
