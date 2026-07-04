package org.graphiks.kanvas.text

import org.graphiks.kanvas.types.Rect

/**
 * GPU-ready text blob wrapping a [TextBlob] with rasterized glyph atlas data.
 *
 * Approach B (Skia-like): [TextBlob] stays lightweight (glyph IDs + positions).
 * [GpuTextBlob] is produced internally by [TextBridge] when the GPU renderer
 * needs glyph raster data. The atlas and UVs are never stored in [TextBlob].
 *
 * The glyph atlas cache is not part of the MVP — each [GpuTextBlob] carries
 * its own atlas. A shared [GlyphAtlasCache] can be added later as an internal
 * optimization without changing the public API.
 */
data class GpuTextBlob(
    val textBlob: TextBlob,
    val atlasRgba: ByteArray,       // A8 glyph atlas pixels (width × height)
    val atlasWidth: Int,
    val atlasHeight: Int,
    private val glyphUvData: List<Rect>? = null,
    val glyphRects: List<Rect> = emptyList(),
) {
    /** Per-glyph UV coordinates into the atlas texture, computed by TextBridge. */
    val glyphUvs: List<Rect> by lazy {
        glyphUvData ?: List(textBlob.glyphRuns.sumOf { it.glyphs.size }) {
            Rect.fromLTRB(0f, 0f, 1f, 1f)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GpuTextBlob) return false
        return textBlob == other.textBlob && atlasRgba.contentEquals(other.atlasRgba) &&
            atlasWidth == other.atlasWidth && atlasHeight == other.atlasHeight
    }

    override fun hashCode(): Int {
        var result = textBlob.hashCode()
        result = 31 * result + atlasRgba.contentHashCode()
        result = 31 * result + atlasWidth
        result = 31 * result + atlasHeight
        return result
    }
}
