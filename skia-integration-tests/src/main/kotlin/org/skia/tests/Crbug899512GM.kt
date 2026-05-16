package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorBLACK
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkBlurMaskFilter
import org.skia.foundation.SkBlurStyle
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkPaint
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/crbug_899512.cpp::crbug_899512` (520 × 520).
 *
 * Reproduces a flipped-CTM blur clipping bug : the matrix
 * `[-1 0 220; 0 1 0; 0 0 1]` flips X around `x = 110`, then a 6.27-σ
 * blur is applied to a rect whose pre-flip device-space bbox would
 * extend past the right edge. The fix forced the blur margin
 * computation to use `|det(ctm)|` so flipped CTMs don't lose pixels.
 */
public class Crbug899512GM : GM() {

    override fun getName(): String = "crbug_899512"
    override fun getISize(): SkISize = SkISize.Make(520, 520)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        // Flip X around 110 and shift origin to 220.
        val matrix = SkMatrix.MakeAll(-1f, 0f, 220f, 0f, 1f, 0f, 0f, 0f, 1f)
        c.concat(matrix)
        val paint = SkPaint().apply {
            maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, 6.2735f)
            colorFilter = SkColorFilters.Blend(SK_ColorBLACK, SkBlendMode.kSrcIn)
        }
        c.drawRect(SkRect.MakeXYWH(0f, 10f, 200f, 200f), paint)
    }
}
