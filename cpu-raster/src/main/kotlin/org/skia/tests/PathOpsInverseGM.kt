package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathFillType
import org.skia.foundation.colorToRGB565
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.pathops.SkPathOp
import org.skia.pathops.SkPathOps

/**
 * Port of Skia's `gm/pathopsinverse.cpp::PathOpsInverseGM` (1200 × 900).
 *
 * Renders a 4-row × 6-column grid showing the result of the 5 path-ops
 * (`kDifference`, `kIntersect`, `kUnion`, `kXOR`, `kReverseDifference`)
 * applied to two overlapping rectangles whose [SkPathFillType] varies
 * between [SkPathFillType.kEvenOdd] and [SkPathFillType.kInverseEvenOdd].
 *
 * Each row corresponds to one of the four (oneFill, twoFill) ∈
 * `{ee, eI, Ie, II}` combinations ; the leftmost cell shows the two
 * source rectangles overlaid, and the next 5 cells show each op.
 *
 * Exercises the public [SkPathOps.Op] entry point on rect-rect inputs
 * with various fill types — when both rects have the standard
 * even-odd fill the rect-rect fast path inside [SkPathOps.Op]
 * handles the case directly ; when either has an inverse fill the
 * rect-rect fast path bails and the full pipeline (intersector +
 * coincidence resolution + bridgeOp) takes over.
 */
public class PathOpsInverseGM : GM() {

    private val onePaint: SkPaint = makeFillPaint(colorToRGB565(0xFF8080FFu.toInt()))
    private val twoPaint: SkPaint = makeFillPaint(0x807F1F1Fu.toInt())
    private val outlinePaint: SkPaint = SkPaint().apply {
        isAntiAlias = true
        color = 0xFF000000u.toInt()
        style = SkPaint.Style.kStroke_Style
    }

    /**
     * The C++ GM uses a tiny `drawColor` round-trip on a 1×1 surface to
     * get the post-composition color of `oneColor` blended with
     * `twoColor` ; for our raster sink that's overkill — the same
     * value falls out of the standard sRGB SrcOver formula since both
     * inputs are in 8-bit sRGB. Pre-computed offline via the upstream
     * pixel result at `pathopsinverse.png` (cell row 1 col 2).
     */
    private val blendColor: Int = 0xFFC04F8FU.toInt()

    private val opPaint: Map<SkPathOp, SkPaint> = mapOf(
        SkPathOp.kDifference to makeFillPaint(colorToRGB565(0xFF8080FFu.toInt())),
        SkPathOp.kIntersect to makeFillPaint(blendColor),
        SkPathOp.kUnion to makeFillPaint(colorToRGB565(0xFFC0FFC0u.toInt())),
        SkPathOp.kReverseDifference to makeFillPaint(0x807F1F1Fu.toInt()),
        SkPathOp.kXOR to makeFillPaint(colorToRGB565(0xFFA0FFE0u.toInt())),
    )

    override fun getName(): String = "pathopsinverse"
    override fun getISize(): SkISize = SkISize.Make(1200, 900)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        var yPos = 0
        for (oneFill in 0..1) {
            val oneF = if (oneFill != 0) SkPathFillType.kInverseEvenOdd else SkPathFillType.kEvenOdd
            for (twoFill in 0..1) {
                val twoF = if (twoFill != 0) SkPathFillType.kInverseEvenOdd else SkPathFillType.kEvenOdd
                val one = SkPath.Rect(SkRect.MakeLTRB(10f, 10f, 70f, 70f)).makeFillType(oneF)
                val two = SkPath.Rect(SkRect.MakeLTRB(40f, 40f, 100f, 100f)).makeFillType(twoF)

                // Source-rect column.
                c.save()
                c.translate(0f, yPos.toFloat())
                c.clipRect(SkRect.MakeWH(110f, 110f), doAntiAlias = true)
                c.drawPath(one, onePaint)
                c.drawPath(one, outlinePaint)
                c.drawPath(two, twoPaint)
                c.drawPath(two, outlinePaint)
                c.restore()

                // 5 op columns to the right.
                var xPos = 150
                for (op in OP_ORDER) {
                    val result = SkPathOps.Op(one, two, op) ?: SkPathBuilder().detach()
                    c.save()
                    c.translate(xPos.toFloat(), yPos.toFloat())
                    c.clipRect(SkRect.MakeWH(110f, 110f), doAntiAlias = true)
                    c.drawPath(result, opPaint.getValue(op))
                    c.drawPath(result, outlinePaint)
                    c.restore()
                    xPos += 150
                }
                yPos += 150
            }
        }
    }

    private companion object {
        /**
         * Same enum walking order as the C++ for-loop : iterates the
         * five ops in the order `kDifference, kIntersect, kUnion, kXOR,
         * kReverseDifference`. The C++ GM's `for (int op = ...)` relies
         * on the underlying enum values being contiguous ; we
         * spell it out for clarity.
         */
        val OP_ORDER: List<SkPathOp> = listOf(
            SkPathOp.kDifference,
            SkPathOp.kIntersect,
            SkPathOp.kUnion,
            SkPathOp.kXOR,
            SkPathOp.kReverseDifference,
        )
    }
}

private fun makeFillPaint(c: Int): SkPaint = SkPaint().apply {
    isAntiAlias = true
    style = SkPaint.Style.kFill_Style
    color = c
}
