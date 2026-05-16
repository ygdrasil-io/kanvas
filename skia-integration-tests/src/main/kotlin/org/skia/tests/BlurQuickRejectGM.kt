package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SK_ColorYELLOW
import org.skia.foundation.SkBlurMaskFilter
import org.skia.foundation.SkBlurStyle
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/blurquickreject.cpp::BlurQuickRejectGM`
 * (300 × 300).
 *
 * Stresses the rasteriser's interaction between a `clipRect` and a
 * blurred draw whose bbox extends outside the clip. The bug being
 * tested : if the "quick reject" classifier looks at the *original*
 * draw bbox (without expanding for the blur margin), it would
 * incorrectly skip blurred-but-edge-touching draws.
 *
 * Layout : clip to a `kBoxSize×kBoxSize` rect, then draw four blurred
 * coloured rects whose bboxes overlap the clip's corners and edges.
 * Each blurred rect is paired with a hairline outline of itself so
 * the visible halo can be compared against the original geometry.
 */
public class BlurQuickRejectGM : GM() {

    override fun getName(): String = "blurquickreject"
    override fun getISize(): SkISize = SkISize.Make(300, 300)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val kBoxSize = 100f
        val kBlurRadius = 30f
        val sigma = 0.57735f * kBlurRadius + 0.5f

        val clipRect = SkRect.MakeWH(kBoxSize, kBoxSize)

        // 4 blurred rects, each touching one boundary of the clip.
        val blurRects = arrayOf(
            // Outside left, halo bleeds in.
            SkRect.MakeLTRB(-kBlurRadius - 1f, 0f, -1f, kBoxSize),
            // Outside top.
            SkRect.MakeLTRB(0f, -kBlurRadius - 1f, kBoxSize, -1f),
            // Inside right edge but bleeding out.
            SkRect.MakeLTRB(kBoxSize + 1f, 0f, kBoxSize + kBlurRadius + 1f, kBoxSize),
            // Inside bottom edge.
            SkRect.MakeLTRB(0f, kBoxSize + kBlurRadius + 1f, kBoxSize, 2 * kBoxSize + kBlurRadius + 1f),
        )
        val colors = intArrayOf(SK_ColorRED, SK_ColorGREEN, SK_ColorBLUE, SK_ColorYELLOW)

        val hairlinePaint = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            color = SK_ColorWHITE
            strokeWidth = 0f
        }

        val blurPaint = SkPaint().apply {
            maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, sigma)
        }

        c.drawColor(SK_ColorBLACK)
        c.save()
        c.translate(kBoxSize, kBoxSize)
        c.drawRect(clipRect, hairlinePaint)
        c.clipRect(clipRect)
        for (i in blurRects.indices) {
            blurPaint.color = colors[i]
            c.drawRect(blurRects[i], blurPaint)
            c.drawRect(blurRects[i], hairlinePaint)
        }
        c.restore()
    }
}
