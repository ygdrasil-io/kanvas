package org.skia.foundation

import org.skia.math.SkRect

/**
 * Mirrors Skia's
 * [`SkTextBlob`](https://github.com/google/skia/blob/main/include/core/SkTextBlob.h).
 *
 * An immutable sequence of glyph **runs**, each carrying its own
 * [SkFont] + glyph-id array + positions. Built via
 * [SkTextBlobBuilder] and rendered via
 * [org.skia.core.SkCanvas.drawTextBlob].
 *
 * **Phase I1 scope** : `HorizontalSpread` (uniform x-advance per
 * glyph, constant baseline y) and `FullPositions` (per-glyph `(x, y)`)
 * runs only. `RotateScale` / `RSXform` runs are deferred — no GM in
 * scope uses them.
 *
 * @property runs immutable list of glyph runs ; each run has its own
 *   font and is drawn left-to-right inside the blob's local space.
 * @property cullRect a conservative bounding box of the blob's glyph
 *   ink ; matches Skia's `bounds()` accessor.
 */
public class SkTextBlob internal constructor(
    internal val runs: List<Run>,
    private val cullRect: SkRect,
) {
    /** Mirrors Skia's `SkTextBlob::bounds()`. */
    public fun bounds(): SkRect = cullRect

    /**
     * Internal run representation. The two variants cover the
     * `allocRun` (uniform x-spacing, constant y) and `allocRunPosH`
     * (per-glyph x, constant y) / `allocRunPos` (per-glyph x and y)
     * builder paths.
     */
    internal sealed class Run {
        abstract val font: SkFont
        abstract val glyphIds: IntArray

        /**
         * `allocRun` : single origin, glyphs stride out via the font's
         * advance widths. Mirrors Skia's `RunRecord` + `kDefault_RunFlag`.
         */
        data class HorizontalSpread(
            override val font: SkFont,
            override val glyphIds: IntArray,
            val x: Float,
            val y: Float,
        ) : Run()

        /**
         * `allocRunPosH` : per-glyph `x` array, constant baseline `y`.
         * Mirrors Skia's `kHorizontal_Positioning`.
         */
        data class HorizontalPositions(
            override val font: SkFont,
            override val glyphIds: IntArray,
            val xs: FloatArray,
            val constY: Float,
        ) : Run()

        /**
         * `allocRunPos` : full `(x, y)` per glyph, interleaved.
         * Mirrors Skia's `kFull_Positioning`.
         */
        data class FullPositions(
            override val font: SkFont,
            override val glyphIds: IntArray,
            /** Interleaved `[x0, y0, x1, y1, …]`, length = `glyphIds.size * 2`. */
            val positions: FloatArray,
        ) : Run()
    }
}
