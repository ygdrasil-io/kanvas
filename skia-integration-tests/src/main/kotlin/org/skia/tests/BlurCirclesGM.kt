package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.withSave
import org.graphiks.math.SK_ColorBLACK
import org.skia.foundation.SkBlurMaskFilter
import org.skia.foundation.SkBlurStyle
import org.skia.foundation.SkMaskFilter
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/blurcircles.cpp::BlurCirclesGM` (950 × 950).
 *
 * Phase 7c validation GM — exercises [SkBlurMaskFilter] on a 4 × 4
 * grid of `drawCircle` calls under varying blur radii (rows : 1, 5,
 * 10, 20 px) × varying circle radii (cols : 5, 10, 25, 50 px), each
 * rotated by `j × 22°` about its centre to stress the CTM-aware
 * device-space mask bounds.
 *
 * Validates :
 *  - The mask filter pipeline (`drawPath → rasterise to mask → blur
 *    → composite`) end-to-end.
 *  - Margin computation (`ceil(3 × σ)`) under non-trivial blur radii.
 *  - Rotated CTM : the mask bbox is computed from the rotated path's
 *    AABB, not the source-space bbox.
 *  - Solid black paint (the canonical "drop shadow" colour).
 */
public class BlurCirclesGM : GM() {

    private val blurFilters: Array<SkMaskFilter> = run {
        val radii = floatArrayOf(1f, 5f, 10f, 20f)
        Array(radii.size) { i ->
            SkBlurMaskFilter.Make(SkBlurStyle.kNormal, convertRadiusToSigma(radii[i]))!!
        }
    }

    override fun getName(): String = "blurcircles"
    override fun getISize(): SkISize = SkISize.Make(950, 950)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.scale(1.5f, 1.5f)
        c.translate(50f, 50f)

        val circleRadii = floatArrayOf(5f, 10f, 25f, 50f)

        for (i in blurFilters.indices) {
            // Iso with upstream `SkAutoCanvasRestore autoRestore(canvas, true)` — outer guard
            // around each row. The inner per-circle `save() / restore()` matches upstream's
            // bare pair (intentional, mirrors `gm/blurcircles.cpp`).
            c.withSave {
                translate(0f, 150f * i)
                for (j in circleRadii.indices) {
                    val paint = SkPaint(SK_ColorBLACK).apply {
                        isAntiAlias = true
                        maskFilter = blurFilters[i]
                    }
                    val cxC = 50f; val cyC = 50f
                    save()
                    rotate(j * 22f, cxC, cyC)
                    drawCircle(cxC, cyC, circleRadii[j], paint)
                    restore()
                    translate(150f, 0f)
                }
            }
        }
    }

    public companion object {
        /**
         * Mirrors Skia's `SkBlurMask::ConvertRadiusToSigma(radius)`.
         *
         * `sigma = radius * (1 / √3) + 0.5` — empirical mapping
         * between the perceived blur "radius" (the half-width of the
         * box-blur equivalent) and the Gaussian standard deviation
         * the kernel actually uses.
         */
        public fun convertRadiusToSigma(radius: Float): Float =
            if (radius > 0f) 0.57735f * radius + 0.5f else 0f
    }
}
