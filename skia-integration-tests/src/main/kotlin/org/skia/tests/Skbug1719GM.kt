package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkBlurMaskFilter
import org.skia.foundation.SkBlurStyle
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathFillType
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/skbug1719.cpp::skbug_1719`.
 *
 * Reproduces a clip + blur + colorFilter precision bug
 * (skbug.com/1719). A round-rect-shaped clipPath is intersected, then
 * a slightly-larger nested-rect path is drawn through it with a tiny-
 * sigma blur and a colorFilter that re-tints the result.
 *
 * The clip path is the same shape as the inner ring of the draw path,
 * so the visible content is just the ~1 px wide AA edge of the
 * draw — a mid-tone halo whose pixel intensity depends on correct
 * compositing of `colorFilter(Blend(0xBFFFFFFF, kSrcIn))` over the
 * blurred inner-rect alpha.
 */
public class Skbug1719GM : GM() {

    init {
        setBGColor(0xFF303030u.toInt())
    }

    override fun getName(): String = "skbug1719"
    override fun getISize(): SkISize = SkISize.Make(300, 100)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        // Translate so the (823, 653)-anchored geometry lands at our origin.
        c.translate(-820f, -650f)

        // Clip path : rounded-rect-like contour built from cubicTo arcs.
        val clipPath = SkPathBuilder()
            .moveTo(832f, 654f)
            .lineTo(1034f, 654f)
            .cubicTo(1038.4183f, 654f, 1042f, 657.58173f, 1042f, 662f)
            .lineTo(1042f, 724f)
            .cubicTo(1042f, 728.41827f, 1038.4183f, 732f, 1034f, 732f)
            .lineTo(832f, 732f)
            .cubicTo(827.58173f, 732f, 824f, 728.41827f, 824f, 724f)
            .lineTo(824f, 662f)
            .cubicTo(824f, 657.58173f, 827.58173f, 654f, 832f, 654f)
            .close()
            .detach()

        // Draw path : even-odd outer rect minus the same inner round-rect
        // (= the AA frame between them).
        val drawPath = SkPathBuilder()
            .setFillType(SkPathFillType.kEvenOdd)
            .moveTo(823f, 653f)
            .lineTo(1043f, 653f)
            .lineTo(1043f, 733f)
            .lineTo(823f, 733f)
            .lineTo(823f, 653f)
            .close()
            .moveTo(832f, 654f)
            .lineTo(1034f, 654f)
            .cubicTo(1038.4183f, 654f, 1042f, 657.58173f, 1042f, 662f)
            .lineTo(1042f, 724f)
            .cubicTo(1042f, 728.41827f, 1038.4183f, 732f, 1034f, 732f)
            .lineTo(832f, 732f)
            .cubicTo(827.58173f, 732f, 824f, 728.41827f, 824f, 724f)
            .lineTo(824f, 662f)
            .cubicTo(824f, 657.58173f, 827.58173f, 654f, 832f, 654f)
            .close()
            .detach()

        val paint = SkPaint().apply {
            isAntiAlias = true
            color = 0xFF000000.toInt()
            maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, 0.78867501f)
            colorFilter = SkColorFilters.Blend(0xBFFFFFFF.toInt(), SkBlendMode.kSrcIn)
        }

        c.clipPath(clipPath, doAntiAlias = true)
        c.drawPath(drawPath, paint)
    }
}
