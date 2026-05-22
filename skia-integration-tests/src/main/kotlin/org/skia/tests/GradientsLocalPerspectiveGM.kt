package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkConicalGradient
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRadialGradient
import org.skia.foundation.SkShader
import org.skia.foundation.SkSweepGradient
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect

/**
 * Port of upstream Skia's `gm/gradients.cpp::GradientsLocalPerspectiveGM`
 * (`DEF_GM(return new GradientsLocalPerspectiveGM(true);)` — dither variant).
 *
 * Same 6×5 grid of gradient permutations as [GradientsGM] but with an
 * increasing y-perspective + x-skew applied as the *local* matrix of
 * each gradient shader (perspY = (i+1)/500, skewX = (i+1)/10 where i
 * is the colour-config column index).
 *
 * **Note** : the `dither` parameter is dropped — kanvas-skia raster
 * doesn't toggle dither.
 */
public class GradientsLocalPerspectiveGM : GM() {

    init {
        setBGColor(SkColorSetARGB(0xFF, 0xDD, 0xDD, 0xDD))
    }

    override fun getName(): String = "gradients_local_perspective"
    override fun getISize(): SkISize = SkISize.Make(840, 815)

    private data class GradData(val colors: IntArray, val pos: FloatArray?)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val pts = arrayOf(SkPoint(0f, 0f), SkPoint(100f, 100f))
        val tm = SkTileMode.kClamp
        val r = SkRect.MakeLTRB(0f, 0f, 100f, 100f)
        val paint = SkPaint().apply { isAntiAlias = true }

        val base5 = intArrayOf(SK_ColorRED, SK_ColorGREEN, SK_ColorBLUE, SK_ColorWHITE, SK_ColorBLACK)
        val gradDatas = listOf(
            GradData(intArrayOf(base5[0], base5[1]), null),
            GradData(intArrayOf(base5[0], base5[1]), floatArrayOf(0f, 1f)),
            GradData(intArrayOf(base5[0], base5[1]), floatArrayOf(0.25f, 0.75f)),
            GradData(base5.copyOfRange(0, 5), null),
            GradData(base5.copyOfRange(0, 5), floatArrayOf(0f, 0.125f, 0.5f, 0.875f, 1f)),
            GradData(
                intArrayOf(SK_ColorRED, SK_ColorGREEN, SK_ColorGREEN, SK_ColorBLUE),
                floatArrayOf(0f, 0f, 1f, 1f),
            ),
        )

        val makers: List<(SkPoint, SkPoint, GradData, SkTileMode, SkMatrix) -> SkShader?> = listOf(
            { p0, p1, d, t, lm -> SkLinearGradient.Make(p0, p1, d.colors, d.pos, t, lm) },
            { p0, p1, d, t, lm ->
                val cx = (p0.fX + p1.fX) * 0.5f; val cy = (p0.fY + p1.fY) * 0.5f
                val center = SkPoint(cx, cy)
                SkRadialGradient.Make(center, cx, d.colors, d.pos, t, lm)
            },
            { p0, p1, d, t, lm ->
                val cx = (p0.fX + p1.fX) * 0.5f; val cy = (p0.fY + p1.fY) * 0.5f
                SkSweepGradient.Make(SkPoint(cx, cy), 0f, 360f, d.colors, d.pos, t, lm)
            },
            { p0, p1, d, t, lm ->
                val cx = (p0.fX + p1.fX) * 0.5f; val cy = (p0.fY + p1.fY) * 0.5f
                val c0 = SkPoint(cx, cy)
                val c1 = SkPoint(p0.fX + 0.6f * (p1.fX - p0.fX), p0.fY + 0.25f * (p1.fY - p0.fY))
                SkConicalGradient.Make(
                    c1, (p1.fX - p0.fX) / 7f, c0, (p1.fX - p0.fX) / 2f,
                    d.colors, d.pos, t, lm,
                )
            },
            { p0, p1, d, t, lm ->
                val r0 = (p1.fX - p0.fX) / 10f
                val r1 = (p1.fX - p0.fX) / 3f
                val c0 = SkPoint(p0.fX + r0, p0.fY + r0)
                val c1 = SkPoint(p1.fX - r1, p1.fY - r1)
                SkConicalGradient.Make(c1, r1, c0, r0, d.colors, d.pos, t, lm)
            },
        )

        c.translate(20f, 20f)
        for (i in gradDatas.indices) {
            c.save()
            // perspective.setPerspY((i+1)/500); perspective.setSkewX((i+1)/10)
            val perspective = SkMatrix(
                sx = 1f, kx = (i + 1).toFloat() / 10f, tx = 0f,
                ky = 0f, sy = 1f, ty = 0f,
                persp0 = 0f, persp1 = (i + 1).toFloat() / 500f, persp2 = 1f,
            )
            for (j in makers.indices) {
                paint.shader = makers[j](pts[0], pts[1], gradDatas[i], tm, perspective)
                c.drawRect(r, paint)
                c.translate(0f, 120f)
            }
            c.restore()
            c.translate(120f, 0f)
        }
    }
}
