package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorBLACK
import org.skia.math.SK_ColorRED
import org.skia.math.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorFilters
import org.skia.math.SkColorSetARGB
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * Bespoke validation GM for [SkImageFilters.Offset] +
 * [SkImageFilters.ColorFilter] + [SkImageFilters.Compose] (Phase 7d.1).
 *
 * Not a port — upstream `gm/imagefiltersbase.cpp` mixes ~20 image
 * filters across `Blur`, `DropShadow`, `MatrixTransform`, and several
 * more we don't ship until Phase 7d.2. This GM exercises the 3
 * filters available in 7d.1 across a 3-cell row to validate the
 * `drawImageRect` → filter → blend integration end-to-end.
 *
 * Cells :
 *  1. Source image drawn raw (no filter).
 *  2. Offset (15, 10) applied to the source.
 *  3. Compose(Offset(15, 10), ColorFilter(swap-RB)) applied — verifies
 *     the chained filter math: pixels are first colour-swapped, then
 *     positionally offset.
 */
public class ImageFilterOffsetGM : GM() {

    override fun getName(): String = "image_filter_offset"
    override fun getISize(): SkISize = SkISize.Make(280, 100)

    private val sourceImage: SkImage = run {
        val bm = SkBitmap(64, 64).also { it.eraseColor(SK_ColorWHITE) }
        val canvas = SkCanvas(bm)
        // Red square in the centre — distinctive shape for visual diff.
        canvas.drawRect(
            SkRect.MakeLTRB(8f, 8f, 56f, 56f),
            SkPaint(SK_ColorRED),
        )
        // Black diagonal stripe to make rotation/offset visible.
        canvas.drawRect(
            SkRect.MakeLTRB(20f, 28f, 44f, 36f),
            SkPaint(SK_ColorBLACK),
        )
        bm.asImage()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val sampling = SkSamplingOptions.Default

        // Cell 1 : raw source.
        c.drawImage(sourceImage, 10f, 10f, sampling, null)

        // Cell 2 : Offset(15, 10).
        val offsetPaint = SkPaint().apply {
            imageFilter = SkImageFilters.Offset(15f, 10f)
        }
        c.drawImage(sourceImage, 100f, 10f, sampling, offsetPaint)

        // Cell 3 : Compose(Offset, ColorFilter(swap-RB)).
        val swapRB = SkColorFilters.Matrix(floatArrayOf(
            0f, 0f, 1f, 0f, 0f,    // R' = B
            0f, 1f, 0f, 0f, 0f,
            1f, 0f, 0f, 0f, 0f,    // B' = R
            0f, 0f, 0f, 1f, 0f,
        ))
        val composePaint = SkPaint().apply {
            imageFilter = SkImageFilters.Compose(
                outer = SkImageFilters.Offset(15f, 10f),
                inner = SkImageFilters.ColorFilter(swapRB),
            )
        }
        c.drawImage(sourceImage, 190f, 10f, sampling, composePaint)
    }
}
