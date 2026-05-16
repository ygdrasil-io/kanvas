package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkConicalGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/gradients_2pt_conical.cpp::ConicalGradientsGM`
 * with `kInside_GradCaseType` (840 × 815).
 *
 * 4-column × 7-row grid of `100 × 100` rects, each painted with a
 * "two-point conical gradient" whose two circles are arranged so both
 * centres land inside the rect (the "inside" case set). Rows are
 * different gradient maker variations (different center / radius
 * permutations including `Flip`, `Center`, `ZeroRad*`); columns are
 * different colour-stop layouts:
 *  - col 0 — 2-stop {Red, Green} at `{0, 1}`
 *  - col 1 — 2-stop {Red, Green} at `{.25, .75}`
 *  - col 2 — 5-stop {R, G, B, W, K} at `{0, .125, .5, .875, 1}`
 *  - col 3 — 4-stop {R, G, G, B} at `{0, 0, 1, 1}` (hardstop) under a
 *    `Scale(0.5, 0.5) · Translate(25, 25)` local matrix
 *
 * BG = `0xFFDDDDDD`. All shaders share `tileMode = kClamp` (the GM
 * variant `_repeat` / `_mirror` sets a different mode — we port only
 * the default `kClamp` flavour here; `_nodither` matches our
 * renderer's behaviour since we don't apply dither either).
 */
public class ConicalGradients2ptInsideGM : GM() {

    override fun getName(): String = "gradients_2pt_conical_inside_nodither"
    override fun getISize(): SkISize = SkISize.Make(840, 815)

    init {
        setBGColor(0xFFDDDDDD.toInt())
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val pts = arrayOf(SkPoint(0f, 0f), SkPoint(100f, 100f))
        val rect = SkRect.MakeXYWH(0f, 0f, 100f, 100f)

        c.translate(20f, 20f)

        val gradMakers = listOf(
            ::make2ConicalInside,
            ::make2ConicalInsideFlip,
            ::make2ConicalInsideCenter,
            ::make2ConicalZeroRad,
            ::make2ConicalZeroRadFlip,
            ::make2ConicalZeroRadCenter,
            ::make2ConicalInsideCenterReversed,
        )

        for (i in 0 until gGradData.size) {
            c.save()
            for (j in 0 until gradMakers.size) {
                val localMatrix = if (i == 3) {
                    // Hardstop column — apply scale + translate.
                    SkMatrix.MakeScale(0.5f, 0.5f).postTranslate(25f, 25f)
                } else {
                    SkMatrix.Identity
                }
                val shader = gradMakers[j](pts, gGradData[i], SkTileMode.kClamp, localMatrix)
                if (shader != null) {
                    val paint = SkPaint().apply {
                        isAntiAlias = true
                        this.shader = shader
                    }
                    c.drawRect(rect, paint)
                }
                c.translate(0f, 120f)
            }
            c.restore()
            c.translate(120f, 0f)
        }
    }

    // ─── Maker functions — mirror gm/gradients_2pt_conical.cpp ───────

    private fun make2ConicalInside(pts: Array<SkPoint>, data: GradData, tm: SkTileMode, lm: SkMatrix): SkConicalGradient? {
        val center0 = SkPoint(midpoint(pts[0].fX, pts[1].fX), midpoint(pts[0].fY, pts[1].fY))
        val center1 = SkPoint(interp(pts[0].fX, pts[1].fX, 3f / 5f), interp(pts[0].fY, pts[1].fY, 1f / 4f))
        return SkConicalGradient.Make(
            start = center1, startRadius = (pts[1].fX - pts[0].fX) / 7f,
            end = center0, endRadius = (pts[1].fX - pts[0].fX) / 2f,
            colors = data.colors, positions = data.positions,
            tileMode = tm, localMatrix = lm,
        )
    }

    private fun make2ConicalInsideFlip(pts: Array<SkPoint>, data: GradData, tm: SkTileMode, lm: SkMatrix): SkConicalGradient? {
        val center0 = SkPoint(midpoint(pts[0].fX, pts[1].fX), midpoint(pts[0].fY, pts[1].fY))
        val center1 = SkPoint(interp(pts[0].fX, pts[1].fX, 3f / 5f), interp(pts[0].fY, pts[1].fY, 1f / 4f))
        return SkConicalGradient.Make(
            start = center0, startRadius = (pts[1].fX - pts[0].fX) / 2f,
            end = center1, endRadius = (pts[1].fX - pts[0].fX) / 7f,
            colors = data.colors, positions = data.positions,
            tileMode = tm, localMatrix = lm,
        )
    }

    private fun make2ConicalInsideCenter(pts: Array<SkPoint>, data: GradData, tm: SkTileMode, lm: SkMatrix): SkConicalGradient? {
        val center0 = SkPoint(midpoint(pts[0].fX, pts[1].fX), midpoint(pts[0].fY, pts[1].fY))
        return SkConicalGradient.Make(
            start = center0, startRadius = (pts[1].fX - pts[0].fX) / 7f,
            end = center0, endRadius = (pts[1].fX - pts[0].fX) / 2f,
            colors = data.colors, positions = data.positions,
            tileMode = tm, localMatrix = lm,
        )
    }

    private fun make2ConicalInsideCenterReversed(pts: Array<SkPoint>, data: GradData, tm: SkTileMode, lm: SkMatrix): SkConicalGradient? {
        val center0 = SkPoint(midpoint(pts[0].fX, pts[1].fX), midpoint(pts[0].fY, pts[1].fY))
        return SkConicalGradient.Make(
            start = center0, startRadius = (pts[1].fX - pts[0].fX) / 2f,
            end = center0, endRadius = (pts[1].fX - pts[0].fX) / 7f,
            colors = data.colors, positions = data.positions,
            tileMode = tm, localMatrix = lm,
        )
    }

    private fun make2ConicalZeroRad(pts: Array<SkPoint>, data: GradData, tm: SkTileMode, lm: SkMatrix): SkConicalGradient? {
        val center0 = SkPoint(midpoint(pts[0].fX, pts[1].fX), midpoint(pts[0].fY, pts[1].fY))
        val center1 = SkPoint(interp(pts[0].fX, pts[1].fX, 3f / 5f), interp(pts[0].fY, pts[1].fY, 1f / 4f))
        return SkConicalGradient.Make(
            start = center1, startRadius = 0f,
            end = center0, endRadius = (pts[1].fX - pts[0].fX) / 2f,
            colors = data.colors, positions = data.positions,
            tileMode = tm, localMatrix = lm,
        )
    }

    private fun make2ConicalZeroRadFlip(pts: Array<SkPoint>, data: GradData, tm: SkTileMode, lm: SkMatrix): SkConicalGradient? {
        val center0 = SkPoint(midpoint(pts[0].fX, pts[1].fX), midpoint(pts[0].fY, pts[1].fY))
        val center1 = SkPoint(interp(pts[0].fX, pts[1].fX, 3f / 5f), interp(pts[0].fY, pts[1].fY, 1f / 4f))
        return SkConicalGradient.Make(
            start = center1, startRadius = (pts[1].fX - pts[0].fX) / 2f,
            end = center0, endRadius = 0f,
            colors = data.colors, positions = data.positions,
            tileMode = tm, localMatrix = lm,
        )
    }

    private fun make2ConicalZeroRadCenter(pts: Array<SkPoint>, data: GradData, tm: SkTileMode, lm: SkMatrix): SkConicalGradient? {
        val center0 = SkPoint(midpoint(pts[0].fX, pts[1].fX), midpoint(pts[0].fY, pts[1].fY))
        return SkConicalGradient.Make(
            start = center0, startRadius = 0f,
            end = center0, endRadius = (pts[1].fX - pts[0].fX) / 2f,
            colors = data.colors, positions = data.positions,
            tileMode = tm, localMatrix = lm,
        )
    }

    private data class GradData(
        val colors: IntArray,
        val positions: FloatArray,
    )

    private companion object {
        // Mirror upstream's gColors / gPos0..2 / gColorClamp / gPosClamp.
        val gColorsBasic: IntArray = intArrayOf(
            0xFFFF0000.toInt(),  // Red
            0xFF00FF00.toInt(),  // Green
            0xFF0000FF.toInt(),  // Blue
            0xFFFFFFFF.toInt(),  // White
            0xFF000000.toInt(),  // Black
        )

        val gColorClamp: IntArray = intArrayOf(
            0xFFFF0000.toInt(),  // Red
            0xFF00FF00.toInt(),  // Green
            0xFF00FF00.toInt(),  // Green (hardstop)
            0xFF0000FF.toInt(),  // Blue
        )

        val gGradData: List<GradData> = listOf(
            GradData(gColorsBasic.copyOfRange(0, 2), floatArrayOf(0f, 1f)),
            GradData(gColorsBasic.copyOfRange(0, 2), floatArrayOf(0.25f, 0.75f)),
            GradData(gColorsBasic.copyOfRange(0, 5), floatArrayOf(0f, 0.125f, 0.5f, 0.875f, 1f)),
            GradData(gColorClamp, floatArrayOf(0f, 0f, 1f, 1f)),
        )

        fun midpoint(a: Float, b: Float): Float = (a + b) * 0.5f
        fun interp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
    }
}
