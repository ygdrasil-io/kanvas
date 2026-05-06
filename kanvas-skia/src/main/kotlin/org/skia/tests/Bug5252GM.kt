package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/bug5252.cpp::bug5252` (500 × 500).
 *
 * Oval clip (`clipPath(SkPath::Oval(225 × 200))`) intersecting a
 * 15×10 grid of stroked rect + cubic combos. The bug : the oval's
 * `225×200` fit barely missed an internal `220×200` cached threshold
 * causing strokes near the edge of the oval to be miscomputed. The
 * fix forced the exact path through the rasteriser regardless of
 * width/height.
 */
public class Bug5252GM : GM() {

    override fun getName(): String = "bug5252"
    override fun getISize(): SkISize = SkISize.Make(500, 500)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.translate(10f, 20f)
        c.clipPath(SkPath.Oval(SkRect.MakeWH(225f, 200f)), doAntiAlias = true)

        val pa = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            isAntiAlias = true
            strokeWidth = 1f
        }

        for (i in 0 until 15) {
            for (j in 0 until 10) {
                c.save()
                c.translate(i * 15f, j * 20f)
                c.drawRect(SkRect.MakeXYWH(5f, 5f, 10f, 15f), pa)
                c.drawPath(
                    SkPathBuilder()
                        .moveTo(6f, 6f)
                        .cubicTo(14f, 10f, 13f, 12f, 10f, 12f)
                        .cubicTo(7f, 15f, 8f, 17f, 14f, 18f)
                        .detach(),
                    pa,
                )
                c.restore()
            }
        }
    }
}
