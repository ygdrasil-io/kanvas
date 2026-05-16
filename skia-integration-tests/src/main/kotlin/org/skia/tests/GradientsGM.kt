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
 * Port of upstream Skia's `gm/gradients.cpp::GradientsGM`
 * (`DEF_GM(return new GradientsGM(true);)`).
 *
 * 6×5 grid of gradient permutations :
 *  - 6 colour configurations (`gGradData[]`) — 2-stop /
 *    3-stop / 5-stop combinations.
 *  - 5 gradient types (linear, radial, sweep, 2-radial, 2-conical).
 *
 * **Note** : this port renders only the dither=true variant (one
 * of the two upstream sub-GMs). `setDither` isn't a parameter our
 * pipeline tracks — both variants render identically here. Output
 * matches the dither=true reference (`gradients.png`).
 */
public class GradientsGM : GM() {

    init {
        setBGColor(SkColorSetARGB(0xFF, 0xDD, 0xDD, 0xDD))
    }

    override fun getName(): String = "gradients"
    override fun getISize(): SkISize = SkISize.Make(840, 815)

    private data class GradData(val colors: IntArray, val pos: FloatArray?)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val pts = arrayOf(SkPoint(0f, 0f), SkPoint(100f, 100f))
        val tm = SkTileMode.kClamp
        val r = SkRect.MakeLTRB(0f, 0f, 100f, 100f)
        val paint = SkPaint().apply { isAntiAlias = true }

        // 5 base colours + clamp variant.
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

        // Note: TwoPointConicalGradient.Make can return null for
        // degenerate cases ; gradient lambdas use `?:` to skip the
        // shader (paint draws as solid colour with no shader,
        // producing transparent if no other shader is set). Upstream
        // also returns null for some configs ; iso-similar.
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
            for (j in makers.indices) {
                val scale = if (i == 5) {
                    SkMatrix.MakeScale(0.5f, 0.5f).postTranslate(25f, 25f)
                } else SkMatrix.Identity
                paint.shader = makers[j](pts[0], pts[1], gradDatas[i], tm, scale)
                c.drawRect(r, paint)
                c.translate(0f, 120f)
            }
            c.restore()
            c.translate(120f, 0f)
        }
    }
}
