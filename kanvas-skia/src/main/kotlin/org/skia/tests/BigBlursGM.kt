package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorBLACK
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SkBlurMaskFilter
import org.skia.foundation.SkBlurStyle
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathDirection
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/bigblurs.cpp::BigBlursGM` (320 × 512).
 *
 * Exercises the blur-rect nine-patching code paths with a 65536×65536
 * source rectangle (and a "rectori" — outer rect with inner CCW rect
 * for an inverse-fill ring) at the **5 corners + centre** under all
 * **4 [SkBlurStyle] variants**. The 5 close-up clip rects of size
 * 64×64 each capture one corner / centre slice of the giant primitive,
 * so the full output is a `5×8` grid of 64-px close-ups.
 *
 * Layout :
 *  - Rows 0-3 : `bigRect`  with kNormal / kSolid / kOuter / kInner.
 *  - Rows 4-7 : `rectori`  with kNormal / kSolid / kOuter / kInner.
 *  - Each row's 5 columns capture UL, UR, LR, LL, centre.
 *  - A red 1-px stroked frame outlines every close-up.
 *
 * `sigma` is `SkBlurMask::ConvertRadiusToSigma(4)` = `0.57735·4 + 0.5`
 * ≈ 2.8094.
 */
public class BigBlursGM : GM() {

    init {
        setBGColor(0xFFDDDDDDu.toInt())
    }

    override fun getName(): String = "bigblurs"
    override fun getISize(): SkISize = SkISize.Make(kWidth, kHeight)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val kBig = 65536f
        val sigma = convertRadiusToSigma(4f)

        val bigRect = SkRect.MakeWH(kBig, kBig)
        val insetRect = bigRect.makeInset(20f, 20f)

        val rectori = SkPathBuilder()
            .addRect(bigRect)
            .addRect(insetRect, SkPathDirection.kCCW)
            .detach()

        val kLeftTopPad = 3f * sigma
        val kRightBotPad = kCloseUpSize - 3f * sigma

        // UL hand corners of the rendered close-ups (in source space).
        val origins = arrayOf(
            -kLeftTopPad to -kLeftTopPad,                           // UL
            (kBig - kRightBotPad) to -kLeftTopPad,                  // UR
            (kBig - kRightBotPad) to (kBig - kRightBotPad),         // LR
            -kLeftTopPad to (kBig - kRightBotPad),                  // LL
            (kBig / 2 - kCloseUpSize / 2) to (kBig / 2 - kCloseUpSize / 2), // centre
        )

        val outlinePaint = SkPaint().apply {
            color = SK_ColorRED
            style = SkPaint.Style.kStroke_Style
        }
        val blurPaint = SkPaint().apply {
            isAntiAlias = true
            color = SK_ColorBLACK
        }

        var desiredX = 0f
        var desiredY = 0f

        for (i in 0 until 2) {
            for (j in 0..3) {  // kLastEnum_SkBlurStyle = kInner = 3
                val style = SkBlurStyle.entries[j]
                blurPaint.maskFilter = SkBlurMaskFilter.Make(style, sigma)

                for (k in origins.indices) {
                    c.save()

                    val clipRect = SkRect.MakeXYWH(desiredX, desiredY, kCloseUpSize, kCloseUpSize)
                    c.clipRect(clipRect)

                    c.translate(desiredX - origins[k].first, desiredY - origins[k].second)

                    if (i == 0) {
                        c.drawRect(bigRect, blurPaint)
                    } else {
                        c.drawPath(rectori, blurPaint)
                    }
                    c.restore()
                    c.drawRect(clipRect, outlinePaint)

                    desiredX += kCloseUpSize
                }
                desiredX = 0f
                desiredY += kCloseUpSize
            }
        }
    }

    private fun convertRadiusToSigma(radius: Float): Float =
        if (radius > 0f) 0.57735f * radius + 0.5f else 0f

    private companion object {
        const val kCloseUpSize: Float = 64f
        const val kWidth: Int = 5 * 64
        const val kHeight: Int = 2 * 4 * 64
    }
}
