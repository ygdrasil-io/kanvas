package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/fadefilter.cpp::fadefilter` (256 × 256).
 *
 * Single-rect draw whose paint carries a layer-style image filter
 * `SkImageFilters::ColorFilter(SkColorFilters::Matrix(M))`. The matrix
 * is the identity column-vector + a constant `0.5` bias on R/G/B (so
 * a plain rect draws as a fade-tinted version of itself). Validates
 * the Phase 7d.1 `SkImageFilters::ColorFilter` glue : applies the
 * inner color-filter to whatever input layer the image-filter pipeline
 * produces, then composites back into the parent.
 */
public class FadeFilterGM : GM() {

    override fun getName(): String = "fadefilter"
    override fun getISize(): SkISize = SkISize.Make(256, 256)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val matrix = floatArrayOf(
            1f, 0f, 0f, 0f, 0.5f,
            0f, 1f, 0f, 0f, 0.5f,
            0f, 0f, 1f, 0f, 0.5f,
            0f, 0f, 0f, 1f, 0f,
        )
        val colorFilter = SkColorFilters.Matrix(matrix)
        val layerPaint = SkPaint().apply {
            imageFilter = SkImageFilters.ColorFilter(colorFilter, null)
        }
        c.drawRect(SkRect.MakeLTRB(64f, 64f, 192f, 192f), layerPaint)
    }
}
