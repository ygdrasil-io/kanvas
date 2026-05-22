package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkColorFilter
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkShader
import org.skia.foundation.SkSweepGradient
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils
import kotlin.math.abs
import kotlin.math.max

/**
 * Port of upstream Skia's `gm/hsl.cpp::HSLColorFilterGM`
 * (`DEF_GM(return new HSLColorFilterGM;)`, name `hslcolorfilter`).
 *
 * 840×1100 GM that paints 3 shaders (mandrill image / RGB sweep /
 * pastel sweep) through 7-step "filter ramps" along each of
 * hue / saturation / lightness axes, totalling 9 rows of 7 cells.
 *
 * **Known limitation — HSLAMatrix unimplemented** : upstream uses
 * `SkColorFilters::HSLAMatrix(20-row-major)` which converts the
 * input RGBA to HSL, applies the matrix in HSL space, and converts
 * back. kanvas-skia's [SkColorFilters] doesn't expose an HSL form
 * yet — we fall back to the standard RGB matrix filter via
 * [SkColorFilters.Matrix]. The resulting tint ramps are linear in
 * RGB instead of HSL ; the structural layout (3 × 3 row blocks of
 * 7 cells) matches, but per-cell colours diverge from upstream.
 * Score is expected well below 100 %.
 */
public class HSLColorFilterGM : GM() {

    override fun getName(): String = "hslcolorfilter"
    override fun getISize(): SkISize = SkISize.Make(840, 1100)

    private val fShaders: MutableList<SkShader?> = mutableListOf()

    override fun onOnceBeforeDraw() {
        val mandrill = ToolUtils.GetResourceAsImage("images/mandrill_256.png")
        if (mandrill != null) {
            val src = SkRect.MakeWH(mandrill.width.toFloat(), mandrill.height.toFloat())
            val dst = SkRect.MakeWH(K_WHEEL_SIZE, K_WHEEL_SIZE)
            val lm = SkMatrix.MakeRectToRect(src, dst, SkMatrix.ScaleToFit.kFill_ScaleToFit)
                ?: SkMatrix.Identity
            fShaders.add(mandrill.makeShader(SkTileMode.kClamp, SkTileMode.kClamp, SkSamplingOptions.Default, lm))
        } else {
            fShaders.add(null)
        }

        val gGrads = arrayOf(
            intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFFFF0000.toInt()),
            intArrayOf(0xDFC08040.toInt(), 0xDF8040C0.toInt(), 0xDF40C080.toInt(), 0xDFC08040.toInt()),
        )
        for (cols in gGrads) {
            fShaders.add(
                SkSweepGradient.Make(
                    SkPoint(K_WHEEL_SIZE / 2f, K_WHEEL_SIZE / 2f),
                    -90f, 270f,
                    cols, null,
                    SkTileMode.kRepeat,
                ),
            )
        }
    }

    private fun makeFilter(h: Float, s: Float, l: Float): SkColorFilter {
        // Mirrors upstream's HSLAMatrix construction but applied via the
        // RGB matrix filter (see KDoc).
        val hBias = h
        val hScale = 1.0f
        val sBias = max(s, 0f)
        val sScale = 1f - abs(s)
        val lBias = max(l, 0f)
        val lScale = 1f - abs(l)
        val cm = floatArrayOf(
            hScale,      0f,      0f, 0f, hBias,
                 0f, sScale,      0f, 0f, sBias,
                 0f,     0f, lScale, 0f, lBias,
                 0f,     0f,      0f, 1f,    0f,
        )
        return SkColorFilters.Matrix(cm)
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.drawColor(0xFFCCCCCC.toInt())

        // (h_range, s_range, l_range) per test row.
        val gTests = arrayOf(
            arrayOf(floatArrayOf(-0.5f, 0.5f), floatArrayOf(0f, 0f), floatArrayOf(0f, 0f)),
            arrayOf(floatArrayOf(0f, 0f), floatArrayOf(-1f, 1f), floatArrayOf(0f, 0f)),
            arrayOf(floatArrayOf(0f, 0f), floatArrayOf(0f, 0f), floatArrayOf(-1f, 1f)),
        )

        val rect = SkRect.MakeWH(K_WHEEL_SIZE, K_WHEEL_SIZE)
        val paint = SkPaint()

        for (shader in fShaders) {
            paint.shader = shader
            for (tst in gTests) {
                c.translate(0f, K_WHEEL_SIZE * 0.1f)
                val dh = (tst[0][1] - tst[0][0]) / (K_STEPS - 1).toFloat()
                val ds = (tst[1][1] - tst[1][0]) / (K_STEPS - 1).toFloat()
                val dl = (tst[2][1] - tst[2][0]) / (K_STEPS - 1).toFloat()
                var h = tst[0][0]
                var s = tst[1][0]
                var l = tst[2][0]
                c.save()
                try {
                    for (i in 0 until K_STEPS) {
                        paint.colorFilter = makeFilter(h, s, l)
                        c.translate(K_WHEEL_SIZE * 0.1f, 0f)
                        c.drawRect(rect, paint)
                        c.translate(K_WHEEL_SIZE * 1.1f, 0f)
                        h += dh
                        s += ds
                        l += dl
                    }
                } finally {
                    c.restore()
                }
                c.translate(0f, K_WHEEL_SIZE * 1.1f)
            }
            c.translate(0f, K_WHEEL_SIZE * 0.1f)
        }
    }

    private companion object {
        const val K_WHEEL_SIZE: Float = 100f
        const val K_STEPS: Int = 7
    }
}
