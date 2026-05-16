package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorBLACK
import org.skia.foundation.SkBlurMaskFilter
import org.skia.foundation.SkBlurStyle
import org.skia.foundation.SkConicalGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRRect
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/blurroundrect.cpp::SimpleBlurRoundRectGM`
 * (1000 × 500).
 *
 * 4 rows × 4 corner radii × `{no-shader, radial-gradient-shader}`
 * = 32 cells. Each cell draws a 25×25 RRect with a uniform corner
 * radius and a `SkBlurStyle.kNormal` blur whose σ derives from the
 * row's "blurRadius" via Skia's `ConvertRadiusToSigma(r) = 0.57735·r
 * + 0.5`. Even-indexed columns use a solid black paint ; odd
 * columns add a conical-gradient shader to test the shader+blur
 * pipeline.
 *
 * **Note** : our pipeline doesn't apply `paint.maskFilter` when
 * `paint.shader` is non-null (Phase 7c limitation), so the shader
 * cells render as un-blurred shaded RRects — visual layout still
 * matches but those cells don't show the halo.
 */
public class SimpleBlurRoundRectGM : GM() {

    override fun getName(): String = "simpleblurroundrect"
    override fun getISize(): SkISize = SkISize.Make(1000, 500)

    private fun makeRadial(): org.skia.foundation.SkShader? {
        val pts = arrayOf(SkPoint(0f, 0f), SkPoint(100f, 100f))
        val colors = intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt())
        val pos = floatArrayOf(0.25f, 0.75f)
        val scale = SkMatrix.MakeScale(0.5f, 0.5f).postTranslate(5f, 5f)
        val mid = SkPoint((pts[0].fX + pts[1].fX) * 0.5f, (pts[0].fY + pts[1].fY) * 0.5f)
        val center1 = SkPoint(
            pts[0].fX + (pts[1].fX - pts[0].fX) * 3f / 5f,
            pts[0].fY + (pts[1].fY - pts[0].fY) * 1f / 4f,
        )
        return SkConicalGradient.Make(
            center1, (pts[1].fX - pts[0].fX) / 7f,
            mid, (pts[1].fX - pts[0].fX) / 2f,
            colors, pos, SkTileMode.kClamp, scale,
        )
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.scale(1.5f, 1.5f)
        c.translate(50f, 50f)

        val blurRadii = floatArrayOf(1f, 5f, 10f, 20f)
        val cornerRadii = floatArrayOf(1f, 5f, 10f, 20f)
        val r = SkRect.MakeWH(25f, 25f)

        for (row in blurRadii.indices) {
            val saveCount = c.save()
            c.translate(0f, (r.height() + 50f) * row)
            for (pair in cornerRadii.indices) {
                val paint = SkPaint().apply {
                    color = SK_ColorBLACK
                    maskFilter = SkBlurMaskFilter.Make(
                        SkBlurStyle.kNormal,
                        0.57735f * blurRadii[row] + 0.5f,
                    )
                }
                val rrect = SkRRect.MakeRectXY(r, cornerRadii[pair], cornerRadii[pair])
                c.drawRRect(rrect, paint)
                c.translate(r.width() + 50f, 0f)

                paint.shader = makeRadial()
                c.drawRRect(rrect, paint)
                c.translate(r.width() + 50f, 0f)
            }
            c.restoreToCount(saveCount)
        }
    }
}
