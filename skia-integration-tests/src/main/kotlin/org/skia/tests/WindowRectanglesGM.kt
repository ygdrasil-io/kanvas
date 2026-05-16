package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkClipOp
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.graphiks.math.SkVector
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/windowrectangles.cpp` (`WindowRectanglesGM`,
 * GM name `windowrectangles`).
 *
 * Builds a stack of `kDifference` clips on top of a 25-px checkerboard
 * background, then draws a single 500 × 500 filled rect (cyan-ish) on
 * top. The window-rectangles clip stack is composed of :
 *  - 2 axis-aligned rects (one BW, one AA) ;
 *  - 1 round rect (rect + 60×45 corner) ;
 *  - 1 nine-patch rrect ;
 *  - 1 rrect with 4 mismatched corner radii.
 *
 * The original test used [SkClipStack] iteration ; kanvas-skia inlines
 * the equivalent canvas calls (every element happens to be a
 * `kDifference` op against axis-aligned geometry). Reference image:
 * `windowrectangles.png`, 600 × 600.
 */
public class WindowRectanglesGM : GM() {

    override fun getName(): String = "windowrectangles"
    override fun getISize(): SkISize = SkISize.Make(600, 600)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        ToolUtils.draw_checkerboard(c, 0xFFFFFFFF.toInt(), 0xFFC6C3C6.toInt(), 25)

        // Apply the difference clip stack from upstream (in order).
        c.clipRect(
            SkRect.MakeXYWH(370.75f, 80.25f, 149f, 100f),
            SkClipOp.kDifference,
            doAntiAlias = false,
        )
        c.clipRect(
            SkRect.MakeXYWH(80.25f, 420.75f, 150f, 100f),
            SkClipOp.kDifference,
            doAntiAlias = true,
        )
        c.clipRRect(
            SkRRect.MakeRectXY(SkRect.MakeXYWH(200f, 200f, 200f, 200f), 60f, 45f),
            SkClipOp.kDifference,
            doAntiAlias = true,
        )

        val nine = SkRRect()
        nine.setNinePatch(
            SkRect.MakeXYWH(550f - 30.25f - 100f, 370.75f, 100f, 150f),
            12f, 35f, 23f, 20f,
        )
        c.clipRRect(nine, SkClipOp.kDifference, doAntiAlias = true)

        val complx = SkRRect()
        val complxRadii = arrayOf<SkVector>(
            SkVector(6f, 4f),
            SkVector(8f, 12f),
            SkVector(16f, 24f),
            SkVector(48f, 32f),
        )
        complx.setRectRadii(SkRect.MakeXYWH(80.25f, 80.75f, 100f, 149f), complxRadii)
        c.clipRRect(complx, SkClipOp.kDifference, doAntiAlias = false)

        // Cover with the kCoverRect = (50, 50, 550, 550).
        val paint = SkPaint().apply { color = 0xFF00AA80.toInt() }
        c.drawRect(SkRect.MakeLTRB(50f, 50f, 550f, 550f), paint)
    }
}
