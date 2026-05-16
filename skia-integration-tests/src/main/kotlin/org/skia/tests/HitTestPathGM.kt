package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorRED
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathFillType
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.tools.SkRandom

/**
 * Port of Skia's `gm/hittestpath.cpp` (`hittestpath` GM, 700 × 460).
 *
 * Constructs a 4-segment path of `lineTo + quadTo + cubicTo`
 * with deterministic random control points, and tests
 * [SkPath.contains] for every half-pixel inside an inflated bbox.
 *
 * The left half draws the path with `kEvenOdd` fill and dots every
 * sample point that [SkPath.contains] reports as inside ; the right
 * half repeats with `kWinding`. The set of red points must precisely
 * trace the parity / winding region the rasterizer paints.
 *
 * Reference image: `hittestpath.png`, 700 × 460.
 */
public class HitTestPathGM : GM() {

    override fun getName(): String = "hittestpath"
    override fun getISize(): SkISize = SkISize.Make(700, 460)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val b = SkPathBuilder()
        val rand = SkRandom()
        val scale = 300

        for (i in 0 until 4) {
            val randoms = FloatArray(12)
            for (idx in randoms.indices) {
                randoms[idx] = rand.nextUScalar1()
            }
            b.lineTo(randoms[0] * scale, randoms[1] * scale)
            b.quadTo(
                randoms[2] * scale,
                randoms[3] * scale,
                randoms[4] * scale,
                randoms[5] * scale,
            )
            b.cubicTo(
                randoms[6] * scale,
                randoms[7] * scale,
                randoms[8] * scale,
                randoms[9] * scale,
                randoms[10] * scale,
                randoms[11] * scale,
            )
        }

        b.setFillType(SkPathFillType.kEvenOdd)
        b.offset(20f, 20f)

        var path = b.detach()
        testHittest(c, path)

        c.translate(scale.toFloat(), 0f)
        path = path.makeFillType(SkPathFillType.kWinding)
        testHittest(c, path)
    }

    private fun testHittest(canvas: SkCanvas, path: SkPath) {
        val paint = SkPaint().apply {
            color = SK_ColorRED
        }
        val r = path.computeBounds()
        canvas.drawPath(path, paint)

        val margin = 4f
        paint.color = 0x800000FF.toInt()
        // Drawn via drawPoints / kPoints with hairline cap so each
        // `path.contains(x, y)` hit lands as a 1×1 dot.
        paint.style = SkPaint.Style.kStroke_Style
        paint.strokeWidth = 0f
        val hits = ArrayList<SkPoint>()
        var y = r.top + 0.5f - margin
        while (y < r.bottom + margin) {
            var x = r.left + 0.5f - margin
            while (x < r.right + margin) {
                if (path.contains(x, y)) {
                    hits.add(SkPoint.Make(x, y))
                }
                x += 1f
            }
            y += 1f
        }
        if (hits.isNotEmpty()) {
            canvas.drawPoints(SkCanvas.PointMode.kPoints, hits.toTypedArray(), paint)
        }
    }
}
