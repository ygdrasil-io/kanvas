package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkBlurMaskFilter
import org.skia.foundation.SkBlurStyle
import org.skia.math.SkColorSetARGB
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRRect
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/blurroundrect.cpp::blur_large_rrects`
 * (300 × 300).
 *
 * Repro for crbug.com/1138810 : an extreme-aspect-ratio rrect
 * (`5..240` x `−20000..25`, corner radius 40) drawn 4 times with
 * `rotate(90°, 150, 150)` between draws, each cell using a
 * different RGB primary corner colour. The σ=20 mask filter exercises
 * the rasteriser's mask-bbox computation under coordinates that
 * exceed the visible canvas by ~80×.
 */
public class BlurLargeRRectsGM : GM() {

    override fun getName(): String = "blur_large_rrects"
    override fun getISize(): SkISize = SkISize.Make(300, 300)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply {
            maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, 20f)
        }

        val rect = SkRect.MakeLTRB(5f, -20000f, 240f, 25f)
        val rrect = SkRRect.MakeRectXY(rect, 40f, 40f)
        for (i in 0 until 4) {
            val r = if ((i and 1) != 0) 0xFF else 0
            val g = if ((i and 2) != 0) 0xFF else 0
            val b = if (i < 2) 0xFF else 0
            paint.color = SkColorSetARGB(0xFF, r, g, b)
            c.drawRRect(rrect, paint)
            c.rotate(90f, 150f, 150f)
        }
    }
}
