package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorBLACK
import org.skia.foundation.SkBlurMaskFilter
import org.skia.foundation.SkBlurStyle
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRRect
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/blurignorexform.cpp::BlurIgnoreXformGM` (375 × 475).
 *
 * Tests `SkMaskFilter::MakeBlur` with the `respectCTM` flag. Two
 * columns (`none` — `respectCTM=true`; `IgnoreTransform` —
 * `respectCTM=false`) and three rows (identity, scale 0.5, scale 2.0).
 * Each cell draws the chosen primitive (circle / rect / rrect) twice
 * with a 10-pixel offset, once with the blur mask filter and once
 * without.
 *
 * **Fidelity caveat** : `:kanvas-skia` does not yet expose the
 * `respectCTM=false` knob on `SkBlurMaskFilter.Make` — both columns
 * render with `respectCTM=true`, so the second column will visibly
 * diverge from the upstream reference (the blur scales with the CTM
 * instead of staying constant). The first column should match within
 * the usual blur-rasteriser tolerance.
 */
public class BlurIgnoreXformGM(
    private val drawType: DrawType,
) : GM() {

    public enum class DrawType { kCircle, kRect, kRRect }

    private val blurFilters: Array<org.skia.foundation.SkMaskFilter?> = arrayOfNulls(2)

    override fun getName(): String = when (drawType) {
        DrawType.kCircle -> "blur_ignore_xform_circle"
        DrawType.kRect -> "blur_ignore_xform_rect"
        DrawType.kRRect -> "blur_ignore_xform_rrect"
    }

    override fun getISize(): SkISize = SkISize.Make(375, 475)

    override fun onOnceBeforeDraw() {
        for (i in 0 until kNumBlurs) {
            // NB: upstream calls `SkMaskFilter::MakeBlur(style, sigma, respectCTM)`.
            // We currently lack the `respectCTM` overload — both filters are
            // identical. Documented in the class kdoc.
            blurFilters[i] = SkBlurMaskFilter.Make(
                SkBlurStyle.kNormal,
                convertRadiusToSigma(20f),
            )
        }
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val basePaint = SkPaint().apply {
            color = SK_ColorBLACK
            isAntiAlias = true
        }

        c.translate(10f, 25f)
        c.save()
        c.translate(80f, 0f)
        for (i in 0 until kNumBlurs) {
            val outerSave = c.save()
            c.translate(i * 150f, 0f)
            for (scale in kMatrixScales) {
                c.save()
                c.scale(scale.fScale, scale.fScale)
                val kRadius = 20f
                val coord = 50f * 1f / scale.fScale
                val rect = SkRect.MakeXYWH(coord - kRadius, coord - kRadius, 2 * kRadius, 2 * kRadius)
                val rrect = SkRRect.MakeRectXY(rect, kRadius / 2f, kRadius / 2f)

                basePaint.maskFilter = blurFilters[i]
                for (j in 0 until 2) {
                    c.save()
                    c.translate(10f * (1 - j), 10f * (1 - j))
                    when (drawType) {
                        DrawType.kCircle -> c.drawCircle(coord, coord, kRadius, basePaint)
                        DrawType.kRect -> c.drawRect(rect, basePaint)
                        DrawType.kRRect -> c.drawRRect(rrect, basePaint)
                    }
                    basePaint.maskFilter = null
                    c.restore()
                }
                c.restore()
                c.translate(0f, 150f)
            }
            c.restoreToCount(outerSave)
        }
        c.restore()
        drawOverlay(c)
    }

    private fun drawOverlay(canvas: SkCanvas) {
        canvas.translate(10f, 0f)
        val font = ToolUtils.DefaultPortableFont()
        canvas.save()
        for (i in 0 until kNumBlurs) {
            canvas.drawString(kBlurFlagNames[i], 100f, 0f, font, SkPaint())
            canvas.translate(130f, 0f)
        }
        canvas.restore()
        for (scale in kMatrixScales) {
            canvas.drawString(scale.fName, 0f, 50f, font, SkPaint())
            canvas.translate(0f, 150f)
        }
    }

    private data class MatrixScale(val fScale: Float, val fName: String)

    public companion object {
        private const val kNumBlurs = 2
        private val kBlurFlagNames = arrayOf("none", "IgnoreTransform")
        private val kMatrixScales = arrayOf(
            MatrixScale(1.0f, "Identity"),
            MatrixScale(0.5f, "Scale = 0.5"),
            MatrixScale(2.0f, "Scale = 2.0"),
        )

        /** Mirrors `SkBlurMask::ConvertRadiusToSigma`. */
        public fun convertRadiusToSigma(radius: Float): Float =
            if (radius > 0f) 0.57735f * radius + 0.5f else 0f
    }
}
