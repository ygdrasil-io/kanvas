package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkClipOp
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathDirection
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/circularclips.cpp::CircularClipsGM` (800 × 200).
 *
 * Stress test for circular clipPath stacks under both
 * [SkClipOp.kIntersect] and [SkClipOp.kDifference] semantics, plus
 * inverse-fill toggling. The layout :
 *
 *  - Top-left : a 10× zoomed pair of intersected circles (the lens-
 *    shaped overlap at the canvas centre).
 *  - 4 columns × 2 rows : every combination of inverse-fill state on
 *    `fCircle1` / `fCircle2` (toggled at row boundaries) under both
 *    `kDifference` and `kIntersect` ops. Each cell shows the exact
 *    Boolean shape produced by the clip stack.
 *
 * `SkPath::toggleInverseFillType()` (mutable upstream) is substituted
 * by `makeToggleInverseFillType()` (returns a new path), with the
 * field reassigned each iteration.
 */
public class CircularClipsGM : GM() {

    private val fX1 = 80f
    private val fX2 = 120f
    private val fY = 50f
    private val fR = 40f
    private var fCircle1: SkPath = SkPath.Circle(fX1, fY, fR, SkPathDirection.kCW)
    private var fCircle2: SkPath = SkPath.Circle(fX2, fY, fR, SkPathDirection.kCW)

    override fun getName(): String = "circular-clips"
    override fun getISize(): SkISize = SkISize.Make(800, 200)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val ops = arrayOf(SkClipOp.kDifference, SkClipOp.kIntersect)

        val rect = SkRect.MakeLTRB(fX1 - fR, fY - fR, fX2 + fR, fY + fR)

        val fillPaint = SkPaint().apply { color = 0x80808080.toInt() }

        // Giant background : 10× zoom into the intersected pair.
        c.save()
        c.scale(10f, 10f)
        c.translate(-((fX1 + fX2) / 2 - fR), -(fY - 2 * fR / 3))
        c.clipPath(fCircle1, doAntiAlias = true)
        c.clipPath(fCircle2, doAntiAlias = true)
        c.drawRect(rect, fillPaint)
        c.restore()

        fillPaint.color = 0xFF000000.toInt()

        for (i in 0 until 4) {
            fCircle1 = fCircle1.makeToggleInverseFillType()
            if (i % 2 == 0) {
                fCircle2 = fCircle2.makeToggleInverseFillType()
            }

            c.save()
            for (op in ops.indices) {
                c.save()
                c.clipPath(fCircle1)
                c.clipPath(fCircle2, ops[op])
                c.drawRect(rect, fillPaint)
                c.restore()
                c.translate(0f, 2 * fY)
            }
            c.restore()
            c.translate(fX1 + fX2, 0f)
        }
    }
}
