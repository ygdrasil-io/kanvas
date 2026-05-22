package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkDashPathEffect
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of Skia's `gm/dashing.cpp::Dashing2GM` (640 × 480).
 *
 * 3 dash patterns × 4 shapes (line, rect, oval, 5-point star). Each
 * pattern is stroked at 6 px AA, with a phase = first-interval / 2.
 * Patterns :
 *  - `{10, 10}`
 *  - `{20, 5, 5, 5}`
 *  - `{2, 2}`
 *
 * Cell bounds are 120 × 120 starting at `(20, 20)`, spaced by
 * `width * 4 / 3` (i.e. ~160 px). Exercises [SkDashPathEffect] on
 * non-line paths (curve flattening through stroker on oval / star).
 */
public class Dashing2GM : GM() {

    override fun getName(): String = "dashing2"
    override fun getISize(): SkISize = SkISize.Make(640, 480)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Flattened `gIntervals` table : first value = pattern count,
        // followed by `count` interval values.
        val patterns: Array<FloatArray> = arrayOf(
            floatArrayOf(10f, 10f),
            floatArrayOf(20f, 5f, 5f, 5f),
            floatArrayOf(2f, 2f),
        )

        val procs: Array<(SkRect) -> SkPath> = arrayOf(
            ::makePathLine, ::makePathRect, ::makePathOval, ::makePathStar,
        )

        val paint = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 6f
        }

        val bounds = SkRect.MakeWH(120f, 120f).apply { offset(20f, 20f) }
        val dx = bounds.width() * 4f / 3f
        val dy = bounds.height() * 4f / 3f

        for (y in patterns.indices) {
            val vals = patterns[y]
            val phase = vals[0] / 2f
            paint.pathEffect = SkDashPathEffect.Make(vals, phase)

            for (x in procs.indices) {
                val r = SkRect.MakeLTRB(
                    bounds.left + x * dx, bounds.top + y * dy,
                    bounds.right + x * dx, bounds.bottom + y * dy,
                )
                c.drawPath(procs[x](r), paint)
            }
        }
    }

    private companion object {
        fun makePathLine(b: SkRect): SkPath =
            SkPathBuilder().moveTo(b.left, b.top).lineTo(b.right, b.bottom).detach()

        fun makePathRect(b: SkRect): SkPath = SkPath.Rect(b)
        fun makePathOval(b: SkRect): SkPath = SkPath.Oval(b)

        fun makePathStar(b: SkRect): SkPath {
            val star = makeUnitStar(5)
            val m: SkMatrix = SkMatrix.MakeRectToRect(
                star.computeBounds(), b, SkMatrix.ScaleToFit.kCenter_ScaleToFit,
            ) ?: SkMatrix()
            return star.makeTransform(m)
        }

        fun makeUnitStar(n: Int): SkPath {
            var rad = -PI.toFloat() / 2f
            val drad = (n shr 1) * PI.toFloat() * 2f / n
            val pb = SkPathBuilder()
            pb.moveTo(0f, -1f)
            for (i in 1 until n) {
                rad += drad
                pb.lineTo(cos(rad), sin(rad))
            }
            pb.close()
            return pb.detach()
        }
    }
}
