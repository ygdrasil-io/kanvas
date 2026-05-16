package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/nonclosedpaths.cpp:NonClosedPathsGM`.
 *
 * Tests how various stroke settings (style × cap × join × width) render
 * **non-closed** paths that visually look closed but lack `close()`.
 * The three closure-types differ in where the trailing `lineTo` lands:
 *
 *  - **TotallyNonClosed** : last point is the *bottom-left*, leaving an
 *    open contour (path looks not closed at all).
 *  - **FakeCloseCorner**  : last point coincides with the start at a
 *    corner (looks closed, but caps still appear at the join).
 *  - **FakeCloseMiddle**  : last point coincides with the start in the
 *    middle of an edge (visually identical to a closed path).
 *
 * The grid is `3 types × 2 styles × 3 caps × 3 joins × 4 widths = 216`
 * cells of `100 × 100` each, plus 3 fill-style-only cells. Layout is
 * `kJoin × widths = 3 × 4 = 12` cells per row.
 *
 * Reference image: `nonclosedpaths.png`, 1220 × 1920, default white BG.
 *
 * Stresses the stroker on every cap / join combination simultaneously
 * — the heaviest stroke-permutation regression test in the suite.
 */
public class NonClosedPathsGM : GM() {

    private enum class ClosureType { TotallyNonClosed, FakeCloseCorner, FakeCloseMiddle }

    override fun getName(): String = "nonclosedpaths"

    // 12 cells × 18 rows + 3 extra cells, each 100 × 100.
    override fun getISize(): SkISize = SkISize.Make(1220, 1920)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val strokeWidths = intArrayOf(0, 10, 40, 50)
        val numWidths = strokeWidths.size
        val styles = arrayOf(SkPaint.Style.kStroke_Style, SkPaint.Style.kStrokeAndFill_Style)
        val caps = arrayOf(SkPaint.Cap.kButt_Cap, SkPaint.Cap.kRound_Cap, SkPaint.Cap.kSquare_Cap)
        val joins = arrayOf(SkPaint.Join.kMiter_Join, SkPaint.Join.kRound_Join, SkPaint.Join.kBevel_Join)
        val types = ClosureType.values()

        // Layout: 3 joins × 4 widths = 12 cells per row.
        val lineNum = joins.size * numWidths
        var counter = 0

        val paint = SkPaint().apply { isAntiAlias = true }

        for (type in types) {
            for (style in styles) {
                for (cap in caps) {
                    for (join in joins) {
                        for (width in strokeWidths) {
                            c.save()
                            setLocation(c, counter, lineNum)

                            val path = makePath(type)
                            paint.style = style
                            paint.strokeCap = cap
                            paint.strokeJoin = join
                            paint.strokeWidth = width.toFloat()

                            c.drawPath(path, paint)
                            c.restore()
                            counter++
                        }
                    }
                }
            }
        }

        // 3 extra cells under fill-only style — one per closure type.
        paint.style = SkPaint.Style.kFill_Style
        for (type in types) {
            c.save()
            setLocation(c, counter, lineNum)
            c.drawPath(makePath(type), paint)
            c.restore()
            counter++
        }
    }

    /** Mirrors upstream's `MakePath` — a "rect-like" path with the closure variant baked in. */
    private fun makePath(type: ClosureType): SkPath {
        val b = SkPathBuilder()
        if (type == ClosureType.FakeCloseMiddle) {
            b.moveTo(30f, 50f)
            b.lineTo(30f, 30f)
        } else {
            b.moveTo(30f, 30f)
        }
        b.lineTo(70f, 30f)
        b.lineTo(70f, 70f)
        b.lineTo(30f, 70f)
        b.lineTo(30f, 50f)
        if (type == ClosureType.FakeCloseCorner) {
            b.lineTo(30f, 30f)
        }
        return b.detach()
    }

    /** Mirrors upstream's `SetLocation`. */
    private fun setLocation(canvas: SkCanvas, counter: Int, lineNum: Int) {
        val x = 100f * (counter % lineNum) + 10f + 0.25f
        val y = 100f * (counter / lineNum) + 10f + 0.75f
        canvas.translate(x, y)
    }
}
