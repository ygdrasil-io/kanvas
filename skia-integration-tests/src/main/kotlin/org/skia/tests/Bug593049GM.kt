package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/arcto.cpp` `DEF_SIMPLE_GM(bug593049, …)`.
 *
 * Regression test: a single half-arc starting at a non-axis-aligned
 * point, stroked with `kRound_Cap`. The combination of:
 *  - `arcTo(oval, startAngleDeg, sweepAngleDeg, forceMoveTo = false)`,
 *  - a stroke wider than the arc's extent,
 *  - round caps,
 * exposed a Skia stroker bug at one point.
 *
 * Reference image: `bug593049.png`, 300 × 300, default white BG.
 */
public class Bug593049GM : GM() {

    override fun getName(): String = "bug593049"
    override fun getISize(): SkISize = SkISize.Make(300, 300)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.translate(111f, 0f)

        val yOffset = 122.88f
        val radius = 61.44f
        val oval = SkRect.MakeXYWH(-radius, yOffset - radius, 2f * radius, 2f * radius)
        val path = SkPathBuilder()
            .moveTo(-43.44464063610148f, 79.43535936389853f)
            .arcTo(oval, 1.25f * 180f, 0.5f * 180f, forceMoveTo = false)
            .detach()

        val paint = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            strokeCap = SkPaint.Cap.kRound_Cap
            strokeWidth = 15.36f
        }
        c.drawPath(path, paint)
    }
}
