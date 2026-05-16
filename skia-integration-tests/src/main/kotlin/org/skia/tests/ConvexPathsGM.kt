package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathDirection
import org.skia.foundation.SkRRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.tools.SkRandom

/**
 * Port of Skia's `gm/convexpaths.cpp` (`ConvexPathsGM`).
 *
 * 35+ convex paths laid out in a 5-column grid, each filled with a
 * pseudo-random opaque colour from `SkRandom`. Exercises every verb
 * (line / quad / conic / cubic / arc) plus the path factories (`Rect`,
 * `Circle`, `Oval`, `RRect`, `Line`, `Polygon`) and degenerate cases
 * (point lines, point quads, repeat-vertex cubics, moveTo-only paths).
 *
 * Phase 3e reproduces the upstream layout faithfully, including the
 * 4096-points-per-side polygon path and the matrix-transformed
 * skbug.40040207 cubic. The latter is preserved by **applying the
 * matrix manually** at construction time (the matrix is pure scale +
 * translate `(x, y) → (0.1x − 1, 0.115207y − 2.64977)`, so no
 * `SkPath.transform(SkMatrix)` is needed yet).
 *
 * Paint: `setAntiAlias(true)` only; default `kFill_Style`. Random
 * colours come from a default-seeded [SkRandom] (bit-compatible with
 * upstream), so the colour assigned to each grid cell matches Skia.
 */
public class ConvexPathsGM : GM() {
    init { setBGColor(SK_ColorBLACK) }

    override fun getName(): String = "convexpaths"
    override fun getISize(): SkISize = SkISize.Make(1200, 1100)

    private val paths: List<SkPath> by lazy { makePaths() }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply { isAntiAlias = true }
        val rand = SkRandom()
        c.translate(20f, 20f)
        // Skia scales the whole grid down so 5 × 200-px columns fit.
        c.scale(2.0f / 3, 2.0f / 3)
        for (i in paths.indices) {
            c.save()
            c.translate(200f * (i % 5) + 1f / 10, 200f * (i / 5) + 9f / 10)
            paint.color = rand.nextU() or 0xFF000000.toInt()
            c.drawPath(paths[i], paint)
            c.restore()
        }
    }

    private fun makePaths(): List<SkPath> {
        val b = SkPathBuilder()
        val out = ArrayList<SkPath>(40)

        // 0: closed quad triangle (line + quad)
        out.add(
            b.moveTo(0f, 0f)
                .quadTo(50f, 100f, 0f, 100f)
                .lineTo(0f, 0f)
                .detach()
        )

        // 1: closed lens (two quads)
        out.add(
            b.moveTo(0f, 50f)
                .quadTo(50f, 0f, 100f, 50f)
                .quadTo(50f, 100f, 0f, 50f)
                .detach()
        )

        // 2-3: rect CW / CCW
        out.add(SkPath.Rect(SkRect.MakeLTRB(0f, 0f, 100f, 100f), SkPathDirection.kCW))
        out.add(SkPath.Rect(SkRect.MakeLTRB(0f, 0f, 100f, 100f), SkPathDirection.kCCW))

        // 4: circle
        out.add(SkPath.Circle(50f, 50f, 50f, SkPathDirection.kCW))

        // 5-7: ovals
        out.add(SkPath.Oval(SkRect.MakeXYWH(0f, 0f, 50f, 100f), SkPathDirection.kCW))
        out.add(SkPath.Oval(SkRect.MakeXYWH(0f, 0f, 100f, 5f), SkPathDirection.kCCW))
        out.add(SkPath.Oval(SkRect.MakeXYWH(0f, 0f, 1f, 100f), SkPathDirection.kCCW))

        // 8: rounded rect
        out.add(
            SkPath.RRect(
                SkRRect.MakeRectXY(SkRect.MakeLTRB(0f, 0f, 100f, 100f), 40f, 20f),
                SkPathDirection.kCW,
            )
        )

        // 9: large number of points — 4096-point square outline
        run {
            val length = 100f
            val ptsPerSide = 1 shl 12 // 4096
            b.moveTo(0f, 0f)
            for (i in 1 until ptsPerSide) b.lineTo(length * i / ptsPerSide, 0f)
            for (i in 0 until ptsPerSide) b.lineTo(length, length * i / ptsPerSide)
            for (i in ptsPerSide downTo 1) b.lineTo(length * i / ptsPerSide, length)
            for (i in ptsPerSide downTo 1) b.lineTo(0f, length * i / ptsPerSide)
            out.add(b.detach())
        }

        // 10: shallow diagonals polygon
        out.add(
            SkPath.Polygon(
                arrayOf(0f to 0f, 100f to 1f, 98f to 100f, 3f to 96f),
                isClosed = false,
            )
        )

        // 11: arcTo
        out.add(
            b.arcTo(SkRect.MakeXYWH(0f, 0f, 50f, 100f), 25f, 130f, forceMoveTo = false)
                .detach()
        )

        // 12-13: simple cubics from origin
        out.add(b.cubicTo(1f, 1f, 10f, 90f, 0f, 100f).detach())
        out.add(b.cubicTo(100f, 50f, 20f, 100f, 0f, 0f).detach())

        // 14: cubic with repeated first/last control points
        out.add(
            b.moveTo(10f, 10f)
                .cubicTo(10f, 10f, 10f, 0f, 20f, 0f)
                .lineTo(40f, 0f)
                .cubicTo(40f, 0f, 50f, 0f, 50f, 10f)
                .detach()
        )

        // 15: cubic with repeated middle control points
        out.add(
            b.moveTo(10f, 10f)
                .cubicTo(10f, 0f, 10f, 0f, 20f, 0f)
                .lineTo(40f, 0f)
                .cubicTo(50f, 0f, 50f, 0f, 50f, 10f)
                .detach()
        )

        // 16: cubic where last three points are almost a line
        out.add(
            b.moveTo(0f, 228f / 8)
                .cubicTo(628f / 8, 82f / 8, 1255f / 8, 141f / 8, 1883f / 8, 202f / 8)
                .detach()
        )

        // 17-19: flat cubics (tangent variations)
        out.add(b.moveTo(10f, 0f).cubicTo(0f, 1f, 30f, 1f, 20f, 0f).detach())
        out.add(b.moveTo(0f, 0f).cubicTo(10f, 1f, 30f, 1f, 20f, 0f).detach())
        out.add(b.moveTo(10f, 0f).cubicTo(0f, 1f, 20f, 1f, 30f, 0f).detach())

        // 20: triangle with degenerate quad edge (control = endpoint)
        out.add(
            b.moveTo(8.59375f, 45f)
                .quadTo(16.9921875f, 45f, 31.25f, 45f)
                .lineTo(100f, 100f)
                .lineTo(8.59375f, 45f)
                .detach()
        )

        // 21: triangle with quad-repeated point
        out.add(
            b.moveTo(0f, 25f)
                .lineTo(50f, 0f)
                .quadTo(50f, 50f, 50f, 50f)
                .detach()
        )

        // 22: triangle with cubic 2x repeated point
        out.add(
            b.moveTo(0f, 25f)
                .lineTo(50f, 0f)
                .cubicTo(50f, 0f, 50f, 50f, 50f, 50f)
                .detach()
        )

        // 23: triangle with quad nearly-repeated point
        out.add(
            b.moveTo(0f, 25f)
                .lineTo(50f, 0f)
                .quadTo(50f, 49.95f, 50f, 50f)
                .detach()
        )

        // 24: triangle with cubic 3x nearly-repeated point
        out.add(
            b.moveTo(0f, 25f)
                .lineTo(50f, 0f)
                .cubicTo(50f, 49.95f, 50f, 49.97f, 50f, 50f)
                .detach()
        )

        // 25: triangle with point-degenerate cubic at one corner
        out.add(
            b.moveTo(0f, 25f)
                .lineTo(50f, 0f)
                .lineTo(50f, 50f)
                .cubicTo(50f, 50f, 50f, 50f, 50f, 50f)
                .detach()
        )

        // 26: point line
        out.add(SkPath.Line(50f to 50f, 50f to 50f))

        // 27: point quad
        out.add(b.moveTo(50f, 50f).quadTo(50f, 50f, 50f, 50f).detach())

        // 28: point cubic
        out.add(b.moveTo(50f, 50f).cubicTo(50f, 50f, 50f, 50f, 50f, 50f).detach())

        // 29: moveTo-only paths
        out.add(
            b.moveTo(0f, 0f)
                .moveTo(0f, 0f)
                .moveTo(1f, 1f)
                .moveTo(1f, 1f)
                .moveTo(10f, 10f)
                .detach()
        )

        // 30: another moveTo-only
        out.add(b.moveTo(0f, 0f).moveTo(0f, 0f).detach())

        // 31-35: line / quad / cubic degenerates from origin (implicit moveTo at (0,0))
        out.add(b.lineTo(100f, 100f).detach())
        out.add(b.quadTo(100f, 100f, 0f, 0f).detach())
        out.add(b.quadTo(100f, 100f, 50f, 50f).detach())
        out.add(b.quadTo(50f, 50f, 100f, 100f).detach())
        out.add(b.cubicTo(0f, 0f, 0f, 0f, 100f, 100f).detach())

        // 36: skbug.40040207 — original C++ uses path.transform(matrix) with
        // m.setAll(0.1, 0, -1, 0, 0.115207, -2.64977, 0, 0, 1). Since we don't
        // have SkPath.transform(SkMatrix) yet AND that matrix is pure scale +
        // translate, apply it inline at construction time so the verb stream
        // and rendered geometry stay faithful to upstream.
        out.add(skbugTransformedTriangle())

        // 37: small circle, deliberately last so it lands at device coords far
        // from the origin (small area relative to x, y values).
        out.add(SkPath.Circle(0f, 0f, 1.2f))

        return out
    }

    private fun skbugTransformedTriangle(): SkPath {
        fun mx(x: Float): Float = 0.1f * x - 1f
        fun my(y: Float): Float = 0.115207f * y - 2.64977f
        return SkPathBuilder()
            .moveTo(mx(16.875f), my(192.594f))
            .cubicTo(
                mx(45.625f), my(192.594f),
                mx(74.375f), my(192.594f),
                mx(103.125f), my(192.594f),
            )
            .cubicTo(
                mx(88.75f), my(167.708f),
                mx(74.375f), my(142.823f),
                mx(60f), my(117.938f),
            )
            .cubicTo(
                mx(45.625f), my(142.823f),
                mx(31.25f), my(167.708f),
                mx(16.875f), my(192.594f),
            )
            .close()
            .detach()
    }
}
