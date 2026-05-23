package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathDirection
import org.skia.foundation.SkRRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.tools.SkRandom

/**
 * Port of upstream Skia's `gm/nested.cpp::NestedGM`.
 *
 * Tests combinations of nested rect / oval / rrect path shapes with CW outer
 * + CCW inner contours, exercising even-odd and winding fill rules.
 *
 * Four variants:
 *  - `nested_aa`       (doAA=true,  flipped=false)
 *  - `nested_bw`       (doAA=false, flipped=false)
 *  - `nested_flipY_aa` (doAA=true,  flipped=true)
 *  - `nested_flipY_bw` (doAA=false, flipped=true)
 *
 * Use the companion-object factories to instantiate each variant.
 */
public class NestedGM(
    private val doAA: Boolean,
    private val flipped: Boolean,
) : GM() {

    init {
        setBGColor(SkColorSetARGB(0xFF, 0xDD, 0xDD, 0xDD))
    }

    override fun getName(): String {
        val sb = StringBuilder("nested")
        if (flipped) sb.append("_flipY")
        if (doAA) sb.append("_aa") else sb.append("_bw")
        return sb.toString()
    }

    override fun getISize(): SkISize = SkISize.Make(kImageWidth, kImageHeight)

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
            isAntiAlias = doAA
        }
        val outerRect = SkRect.MakeWH(40f, 40f)
        val innerRects = arrayOf(
            SkRect.MakeLTRB(10f, 10f, 30f, 30f),
            SkRect.MakeLTRB(0.5f, 18f, 4.5f, 22f),
        )

        // Random multicolor background to make transparency errors visible.
        val rand = SkRandom()
        for (yy in 0 until kImageHeight step 10) {
            for (xx in 0 until kImageWidth step 10) {
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
                    if (flipped) {
                        c.scale(1.0f, -1.0f)
                        c.translate(xOff, -yOff - 40.0f)
                    } else {
                        c.translate(xOff, yOff)
                    }
                    c.drawPath(builder.detach(), shapePaint)
                    c.restore()

                    xOff += 45f
                }
            }
            xOff = 2f
            yOff += 45f
        }
    }

    private companion object {
        const val kImageWidth = 269
        const val kImageHeight = 134
    }
}
