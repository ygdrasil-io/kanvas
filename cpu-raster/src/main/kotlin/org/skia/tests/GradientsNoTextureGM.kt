package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorBLUE
import org.skia.foundation.SK_ColorGREEN
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SkColorSetARGB
import org.skia.foundation.SkConicalGradient
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRadialGradient
import org.skia.foundation.SkShader
import org.skia.foundation.SkSweepGradient
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * Port of upstream Skia's `gm/gradients_no_texture.cpp::GradientsNoTextureGM`.
 *
 * Produces a 4×5×2 grid of gradient cells :
 *  - 4 colour configurations (1, 2, 3 and 4 stops sampled from the
 *    `[red, green, blue, white]` array) ;
 *  - 5 gradient types (linear, radial, sweep, two-point conical
 *    "TwoPointRadial", two-point conical "TwoPointConical") ;
 *  - 2 alpha values (`0xFF`, `0x40`).
 *
 * `dither` is not threaded through our paint pipeline (the F16 raster
 * backend is already 16 bpc) — both upstream variants
 * (`gradients_no_texture` and `gradients_no_texture_nodither`) render
 * identically here. The constructor accepts the flag so the two GM
 * names can be exposed but the output is the same.
 */
public class GradientsNoTextureGM(private val dither: Boolean) : GM() {

    public constructor() : this(true)

    init {
        setBGColor(SkColorSetARGB(0xFF, 0xDD, 0xDD, 0xDD))
    }

    override fun getName(): String =
        if (dither) "gradients_no_texture" else "gradients_no_texture_nodither"

    override fun getISize(): SkISize = SkISize.Make(640, 615)

    private data class GradData(val colors: IntArray, val pos: FloatArray?)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val baseColors = intArrayOf(SK_ColorRED, SK_ColorGREEN, SK_ColorBLUE, SK_ColorWHITE)
        val gradData = listOf(
            GradData(baseColors.copyOfRange(0, 1), null),
            GradData(baseColors.copyOfRange(0, 2), null),
            GradData(baseColors.copyOfRange(0, 3), null),
            GradData(baseColors.copyOfRange(0, 4), null),
        )

        val pts = arrayOf(SkPoint(0f, 0f), SkPoint(50f, 50f))
        val tm = SkTileMode.kClamp
        val rect = SkRect.MakeLTRB(0f, 0f, 50f, 50f)

        val makers: List<(SkPoint, SkPoint, GradData, SkTileMode) -> SkShader?> = listOf(
            // Linear.
            { p0, p1, d, t -> SkLinearGradient.Make(p0, p1, d.colors, d.pos, t) },
            // Radial — center = midpoint, radius = midpoint.x.
            { p0, p1, d, t ->
                val cx = (p0.fX + p1.fX) * 0.5f; val cy = (p0.fY + p1.fY) * 0.5f
                SkRadialGradient.Make(SkPoint(cx, cy), cx, d.colors, d.pos, t)
            },
            // Sweep — full 0..360 sweep around midpoint.
            { p0, p1, d, t ->
                val cx = (p0.fX + p1.fX) * 0.5f; val cy = (p0.fY + p1.fY) * 0.5f
                SkSweepGradient.Make(SkPoint(cx, cy), 0f, 360f, d.colors, d.pos, t)
            },
            // Two-point radial (Make2Radial in C++).
            { p0, p1, d, t ->
                val center0X = (p0.fX + p1.fX) * 0.5f
                val center0Y = (p0.fY + p1.fY) * 0.5f
                val c0 = SkPoint(center0X, center0Y)
                val c1 = SkPoint(
                    p0.fX + 0.6f * (p1.fX - p0.fX),
                    p0.fY + 0.25f * (p1.fY - p0.fY),
                )
                val r1 = (p1.fX - p0.fX) / 7f
                val r0 = (p1.fX - p0.fX) / 2f
                SkConicalGradient.Make(c1, r1, c0, r0, d.colors, d.pos, t)
            },
            // Two-point conical (Make2Conical in C++).
            { p0, p1, d, t ->
                val r0 = (p1.fX - p0.fX) / 10f
                val r1 = (p1.fX - p0.fX) / 3f
                val c0 = SkPoint(p0.fX + r0, p0.fY + r0)
                val c1 = SkPoint(p1.fX - r1, p1.fY - r1)
                SkConicalGradient.Make(c1, r1, c0, r0, d.colors, d.pos, t)
            },
        )

        val alphas = intArrayOf(0xFF, 0x40)
        val paint = SkPaint().apply { isAntiAlias = true }

        c.translate(20f, 20f)
        for (a in alphas.indices) {
            for (i in gradData.indices) {
                c.save()
                for (j in makers.indices) {
                    paint.shader = makers[j](pts[0], pts[1], gradData[i], tm)
                    paint.alpha = alphas[a]
                    c.drawRect(rect, paint)
                    c.translate(0f, rect.height() + 20f)
                }
                c.restore()
                c.translate(rect.width() + 20f, 0f)
            }
        }
    }
}
