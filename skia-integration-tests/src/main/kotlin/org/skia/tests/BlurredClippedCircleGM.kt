package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkBlurMaskFilter
import org.skia.foundation.SkBlurStyle
import org.skia.foundation.SkClipOp
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/blurredclippedcircle.cpp::BlurredClippedCircleGM`
 * (1164 × 802).
 *
 * Reproduces the precision artifacts seen in crbug.com/560651 — a
 * scale(2) zoom into a stack of nested clips that culminate in a
 * `clipRRect(oval, kDifference)` cutting an oval hole, then
 * `drawRRect(outerOval, paint{maskFilter=BlurNormal, colorFilter=Blend(RED, kSrcIn)})`
 * draws a 1.366-σ-blurred oval. The visible result is a thin
 * red-tinted halo (the outer oval visible through the cut-out hole's
 * complement, blurred and recoloured).
 *
 * Validates :
 *  - `clipRRect(oval, kDifference, AA=true)` for cutting an oval hole.
 *  - Mask filter blur composed with the difference clip.
 *  - ColorFilter.Blend(SrcIn) on the masked drawRRect output.
 */
public class BlurredClippedCircleGM : GM() {

    init {
        setBGColor(0xFFCCCCCCu.toInt())
    }

    override fun getName(): String = "blurredclippedcircle"
    override fun getISize(): SkISize = SkISize.Make(kWidth, kHeight)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val whitePaint = SkPaint().apply {
            color = SK_ColorWHITE
            blendMode = SkBlendMode.kSrc
            isAntiAlias = true
        }

        c.scale(2f, 2f)

        c.save()
        run {
            val clipRect1 = SkRect.MakeLTRB(0f, 0f, kWidth.toFloat(), kHeight.toFloat())
            c.clipRect(clipRect1)

            c.save()
            run {
                c.clipRect(clipRect1)
                c.drawRect(clipRect1, whitePaint)

                c.save()
                run {
                    val clipRect2 = SkRect.MakeLTRB(8f, 8f, 288f, 288f)
                    val clipRRect = SkRRect.MakeOval(clipRect2)
                    c.clipRRect(clipRRect, SkClipOp.kDifference, doAntiAlias = true)

                    val r = SkRect.MakeLTRB(4f, 4f, 292f, 292f)
                    val rr = SkRRect.MakeOval(r)

                    val paint = SkPaint().apply {
                        maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, 1.366025f)
                        colorFilter = SkColorFilters.Blend(SK_ColorRED, SkBlendMode.kSrcIn)
                        isAntiAlias = true
                    }
                    c.drawRRect(rr, paint)
                }
                c.restore()
            }
            c.restore()
        }
        c.restore()
    }

    private companion object {
        const val kWidth: Int = 1164
        const val kHeight: Int = 802
    }
}
