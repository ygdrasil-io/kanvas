package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder

/**
 * Port of Skia's `gm/emptypath.cpp::EmptyStrokeGM` (200 × 240).
 *
 * Exercises the stroker's handling of zero-length sub-paths. Four
 * variants stacked vertically (40 px apart) ; in each row, three
 * red 7-px-wide dots mark the three reference points
 * `(40, 40)`, `(80, 40)`, `(120, 40)` (drawn first via
 * [SkCanvas.PointMode.kPoints]) and a black 21-px-wide square-cap
 * stroked path is overlaid :
 *
 *  - row 0 — three `moveTo`-only sub-paths → empty → no black draws
 *    (red dots remain visible).
 *  - row 1 — three `moveTo + close` sub-paths → degenerate but
 *    closed → stroker draws three black square caps (covering the
 *    red dots).
 *  - row 2 — three `moveTo + lineTo(same point)` zero-length lines
 *    → square-capped → three black squares.
 *  - row 3 — mixed (one empty, one closed, one zero-line) → red,
 *    black, black.
 */
public class EmptyStrokeGM : GM() {

    override fun getName(): String = "emptystroke"
    override fun getISize(): SkISize = SkISize.Make(200, 240)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val procs: Array<() -> SkPath> = arrayOf(
            ::makePathMove,
            ::makePathMoveClose,
            ::makePathMoveLine,
            ::makePathMoveMix,
        )

        val strokePaint = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 21f
            strokeCap = SkPaint.Cap.kSquare_Cap
        }

        val dotPaint = SkPaint().apply {
            color = SK_ColorRED
            strokeWidth = 7f
        }

        for (proc in procs) {
            c.drawPoints(SkCanvas.PointMode.kPoints, kPts, dotPaint)
            c.drawPath(proc(), strokePaint)
            c.translate(0f, 40f)
        }
    }

    private companion object {
        val kPts: Array<SkPoint> = arrayOf(
            SkPoint.Make(40f, 40f),
            SkPoint.Make(80f, 40f),
            SkPoint.Make(120f, 40f),
        )

        fun makePathMove(): SkPath {
            val b = SkPathBuilder()
            for (p in kPts) b.moveTo(p.fX, p.fY)
            return b.detach()
        }

        fun makePathMoveClose(): SkPath {
            val b = SkPathBuilder()
            for (p in kPts) { b.moveTo(p.fX, p.fY); b.close() }
            return b.detach()
        }

        fun makePathMoveLine(): SkPath {
            val b = SkPathBuilder()
            for (p in kPts) { b.moveTo(p.fX, p.fY); b.lineTo(p.fX, p.fY) }
            return b.detach()
        }

        fun makePathMoveMix(): SkPath {
            val b = SkPathBuilder()
            b.moveTo(kPts[0].fX, kPts[0].fY)
            b.moveTo(kPts[1].fX, kPts[1].fY); b.close()
            b.moveTo(kPts[2].fX, kPts[2].fY); b.lineTo(kPts[2].fX, kPts[2].fY)
            return b.detach()
        }
    }
}
