package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorBLACK
import org.skia.math.SkColorSetARGB
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathDirection
import org.skia.foundation.SkRRect
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.tools.SkRandom

/**
 * Port of upstream Skia's `gm/nested.cpp::NestedGM`
 * (4 variants — we ship only the `nested_aa` one).
 *
 * Tests combinations of nested rect / oval / rrect path shapes
 * with CW outer + CCW inner contours, exercising even-odd and
 * winding fill rules.
 *
 * **Adaptations** :
 *  - Only the `nested_aa` (doAA=true, flipped=false) variant is
 *    rendered. The other 3 variants (bw / aa+flipY / bw+flipY)
 *    are deferred — their layouts are derivable from this one.
 */
public class NestedGM : GM() {

    init {
        setBGColor(SkColorSetARGB(0xFF, 0xDD, 0xDD, 0xDD))
    }

    override fun getName(): String = "nested_aa"
    override fun getISize(): SkISize = SkISize.Make(269, 134)

    private enum class Shape { kRect, kRRect, kOval }

    private fun addShape(b: SkPathBuilder, rect: SkRect, shape: Shape, dir: SkPathDirection) {
        when (shape) {
            Shape.kRect -> b.addRect(rect, dir)
            Shape.kRRect -> {
                val rr = SkRRect()
                rr.setRectXY(rect, 5f, 5f)
                b.addRRect(rr, dir)
            }
            Shape.kOval -> b.addOval(rect, dir)
        }
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val shapePaint = SkPaint().apply {
            color = SK_ColorBLACK
            isAntiAlias = true
        }
        val outerRect = SkRect.MakeWH(40f, 40f)
        val innerRects = arrayOf(
            SkRect.MakeLTRB(10f, 10f, 30f, 30f),
            SkRect.MakeLTRB(0.5f, 18f, 4.5f, 22f),
        )

        // Random multicolor background.
        val rand = SkRandom()
        for (yy in 0 until getISize().height step 10) {
            for (xx in 0 until getISize().width step 10) {
                val r = SkRect.MakeXYWH(xx.toFloat(), yy.toFloat(), 10f, 10f)
                val p = SkPaint().apply { color = rand.nextU() or 0xFF000000.toInt() }
                c.drawRect(r, p)
            }
        }

        var xOff = 2f
        var yOff = 2f
        for (outerShape in Shape.values()) {
            for (innerShape in Shape.values()) {
                for (innerRect in innerRects) {
                    val builder = SkPathBuilder()
                    addShape(builder, outerRect, outerShape, SkPathDirection.kCW)
                    addShape(builder, innerRect, innerShape, SkPathDirection.kCCW)

                    c.save()
                    c.translate(xOff, yOff)
                    c.drawPath(builder.detach(), shapePaint)
                    c.restore()

                    xOff += 45f
                }
            }
            xOff = 2f
            yOff += 45f
        }
    }
}
