package org.skia.foundation

import org.skia.math.SkRect
import org.skia.math.SkScalar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tan

/**
 * Mutable, fluent path builder. Mirrors Skia's `SkPathBuilder` 4.x API.
 *
 * - `moveTo` / `lineTo` / `quadTo` / `conicTo` / `cubicTo` / `close` :
 *   raw verb emission. The current point is tracked internally.
 * - `arcTo(rect, startDeg, sweepDeg, forceMoveTo)` and
 *   `addArc(rect, startDeg, sweepDeg)` : elliptical arc construction.
 *   Each arc is split into ≤90° segments and approximated by cubic
 *   Béziers using the Hugues approximation
 *   (`k = (4/3) * tan(θ/4)`).
 * - `addRect` / `addOval` / `addCircle` / `addPolygon` / `addPath` :
 *   contour-level helpers.
 * - `detach()` produces an immutable [SkPath] and resets the builder.
 *   `snapshot()` produces a copy without resetting (the builder remains
 *   usable).
 */
public class SkPathBuilder public constructor() {

    private val verbs: ArrayList<SkPath.Verb> = ArrayList()
    private val coords: ArrayList<SkScalar> = ArrayList()
    private val conicWeights: ArrayList<SkScalar> = ArrayList()
    private var fillType: SkPathFillType = SkPathFillType.kWinding

    /** Current pen position (source-space). */
    private var lastX: SkScalar = 0f
    private var lastY: SkScalar = 0f
    /** Start of the current contour — `close` rewinds the pen here. */
    private var contourX: SkScalar = 0f
    private var contourY: SkScalar = 0f
    /** True between `moveTo` and the next `close` or end-of-builder. */
    private var hasContour: Boolean = false

    public fun isEmpty(): Boolean = verbs.isEmpty()

    public fun setFillType(t: SkPathFillType): SkPathBuilder = apply { fillType = t }

    public fun moveTo(x: SkScalar, y: SkScalar): SkPathBuilder = apply {
        verbs.add(SkPath.Verb.kMove); coords.add(x); coords.add(y)
        lastX = x; lastY = y
        contourX = x; contourY = y
        hasContour = true
    }

    public fun lineTo(x: SkScalar, y: SkScalar): SkPathBuilder = apply {
        ensureContour()
        verbs.add(SkPath.Verb.kLine); coords.add(x); coords.add(y)
        lastX = x; lastY = y
    }

    public fun quadTo(
        x1: SkScalar, y1: SkScalar, x2: SkScalar, y2: SkScalar,
    ): SkPathBuilder = apply {
        ensureContour()
        verbs.add(SkPath.Verb.kQuad)
        coords.add(x1); coords.add(y1)
        coords.add(x2); coords.add(y2)
        lastX = x2; lastY = y2
    }

    public fun conicTo(
        x1: SkScalar, y1: SkScalar, x2: SkScalar, y2: SkScalar, w: SkScalar,
    ): SkPathBuilder = apply {
        ensureContour()
        when {
            !w.isFinite() || w <= 0f -> {
                // Degenerate weight → fall back to line segments through the control.
                lineTo(x1, y1)
                lineTo(x2, y2)
            }
            w == 1f -> {
                // Conic with weight 1 = quadratic — preserve verb identity for
                // downstream readers but the rasterizer treats both alike.
                quadTo(x1, y1, x2, y2)
            }
            else -> {
                verbs.add(SkPath.Verb.kConic)
                coords.add(x1); coords.add(y1)
                coords.add(x2); coords.add(y2)
                conicWeights.add(w)
                lastX = x2; lastY = y2
            }
        }
    }

    public fun cubicTo(
        x1: SkScalar, y1: SkScalar,
        x2: SkScalar, y2: SkScalar,
        x3: SkScalar, y3: SkScalar,
    ): SkPathBuilder = apply {
        ensureContour()
        verbs.add(SkPath.Verb.kCubic)
        coords.add(x1); coords.add(y1)
        coords.add(x2); coords.add(y2)
        coords.add(x3); coords.add(y3)
        lastX = x3; lastY = y3
    }

    public fun close(): SkPathBuilder = apply {
        if (hasContour) {
            verbs.add(SkPath.Verb.kClose)
            lastX = contourX; lastY = contourY
            hasContour = false
        }
    }

    /**
     * Append an elliptical arc spanning `sweepAngleDeg` starting at
     * `startAngleDeg`, both measured from the centre of `oval`. Mirrors
     * Skia's `SkPathBuilder::arcTo(SkRect, SkScalar, SkScalar, bool)`.
     *
     * - `forceMoveTo = true` (or builder empty) — the contour starts at
     *   the arc's first point with `moveTo`.
     * - `forceMoveTo = false` and a current contour exists — the first
     *   arc point is reached via an implicit `lineTo`, joining the
     *   existing contour.
     *
     * Each arc is split into segments of ≤ 90° and approximated by
     * cubic Béziers. The approximation error stays under ~0.0003 of the
     * radius for 90° segments — well below the 0.25-pixel flatness used
     * by the rasterizer at typical scales.
     */
    public fun arcTo(
        oval: SkRect,
        startAngleDeg: SkScalar,
        sweepAngleDeg: SkScalar,
        forceMoveTo: Boolean,
    ): SkPathBuilder = apply {
        if (sweepAngleDeg == 0f) {
            // Skia's behaviour: degenerate sweep emits a moveTo or lineTo to the
            // start of the arc but no curve. Useful for chained arcs.
            val (sx, sy) = ovalPointAt(oval, startAngleDeg)
            if (forceMoveTo || !hasContour) moveTo(sx, sy) else lineTo(sx, sy)
            return@apply
        }
        emitArc(oval, startAngleDeg, sweepAngleDeg, forceMoveTo = forceMoveTo)
    }

    /**
     * Equivalent to `arcTo(oval, startAngleDeg, sweepAngleDeg, forceMoveTo = true)`
     * — the arc always starts a new contour. Mirrors Skia's
     * `SkPathBuilder::addArc`.
     */
    public fun addArc(
        oval: SkRect,
        startAngleDeg: SkScalar,
        sweepAngleDeg: SkScalar,
    ): SkPathBuilder = apply {
        if (sweepAngleDeg == 0f) return@apply
        emitArc(oval, startAngleDeg, sweepAngleDeg, forceMoveTo = true)
    }

    public fun addRect(
        rect: SkRect,
        dir: SkPathDirection = SkPathDirection.kCW,
    ): SkPathBuilder = apply {
        moveTo(rect.left, rect.top)
        if (dir == SkPathDirection.kCW) {
            lineTo(rect.right, rect.top)
            lineTo(rect.right, rect.bottom)
            lineTo(rect.left, rect.bottom)
        } else {
            lineTo(rect.left, rect.bottom)
            lineTo(rect.right, rect.bottom)
            lineTo(rect.right, rect.top)
        }
        close()
    }

    /**
     * Append an axis-aligned ellipse contour as 4 cubic Béziers per the
     * standard `(4/3) * (sqrt(2) - 1) ≈ 0.5523` approximation. Visual
     * error stays under ~0.027 % of the radius, indistinguishable from
     * the analytic ellipse at typical pixel scales.
     */
    public fun addOval(
        oval: SkRect,
        dir: SkPathDirection = SkPathDirection.kCW,
    ): SkPathBuilder = apply {
        val cx = (oval.left + oval.right) * 0.5f
        val cy = (oval.top + oval.bottom) * 0.5f
        val rx = (oval.right - oval.left) * 0.5f
        val ry = (oval.bottom - oval.top) * 0.5f
        val k = OVAL_KAPPA
        val kx = k * rx
        val ky = k * ry
        moveTo(cx + rx, cy)
        if (dir == SkPathDirection.kCW) {
            cubicTo(cx + rx, cy + ky, cx + kx, cy + ry, cx, cy + ry)
            cubicTo(cx - kx, cy + ry, cx - rx, cy + ky, cx - rx, cy)
            cubicTo(cx - rx, cy - ky, cx - kx, cy - ry, cx, cy - ry)
            cubicTo(cx + kx, cy - ry, cx + rx, cy - ky, cx + rx, cy)
        } else {
            cubicTo(cx + rx, cy - ky, cx + kx, cy - ry, cx, cy - ry)
            cubicTo(cx - kx, cy - ry, cx - rx, cy - ky, cx - rx, cy)
            cubicTo(cx - rx, cy + ky, cx - kx, cy + ry, cx, cy + ry)
            cubicTo(cx + kx, cy + ry, cx + rx, cy + ky, cx + rx, cy)
        }
        close()
    }

    public fun addCircle(
        cx: SkScalar, cy: SkScalar, r: SkScalar,
        dir: SkPathDirection = SkPathDirection.kCW,
    ): SkPathBuilder = addOval(SkRect.MakeLTRB(cx - r, cy - r, cx + r, cy + r), dir)

    /**
     * Append a polygon contour (one `moveTo` + `N-1` `lineTo`s, optionally
     * followed by `close`). Mirrors Skia's `SkPathBuilder::addPolygon`.
     */
    public fun addPolygon(
        points: Array<Pair<SkScalar, SkScalar>>,
        isClosed: Boolean,
    ): SkPathBuilder = apply {
        if (points.isEmpty()) return@apply
        moveTo(points[0].first, points[0].second)
        for (i in 1 until points.size) lineTo(points[i].first, points[i].second)
        if (isClosed) close()
    }

    /** Append every verb of `path` to this builder. */
    public fun addPath(path: SkPath): SkPathBuilder = apply {
        var coordIdx = 0
        var weightIdx = 0
        val src = path.coords
        for (verb in path.verbs) {
            when (verb) {
                SkPath.Verb.kMove -> moveTo(src[coordIdx++], src[coordIdx++])
                SkPath.Verb.kLine -> lineTo(src[coordIdx++], src[coordIdx++])
                SkPath.Verb.kQuad -> {
                    val x1 = src[coordIdx++]; val y1 = src[coordIdx++]
                    val x2 = src[coordIdx++]; val y2 = src[coordIdx++]
                    quadTo(x1, y1, x2, y2)
                }
                SkPath.Verb.kConic -> {
                    val x1 = src[coordIdx++]; val y1 = src[coordIdx++]
                    val x2 = src[coordIdx++]; val y2 = src[coordIdx++]
                    val w = path.conicWeights[weightIdx++]
                    conicTo(x1, y1, x2, y2, w)
                }
                SkPath.Verb.kCubic -> {
                    val x1 = src[coordIdx++]; val y1 = src[coordIdx++]
                    val x2 = src[coordIdx++]; val y2 = src[coordIdx++]
                    val x3 = src[coordIdx++]; val y3 = src[coordIdx++]
                    cubicTo(x1, y1, x2, y2, x3, y3)
                }
                SkPath.Verb.kClose -> close()
            }
        }
    }

    /**
     * Detach the accumulated verb stream into an immutable [SkPath] and
     * reset the builder to empty (mirrors `SkPathBuilder::detach()` in
     * Skia 4.x). Subsequent calls start a fresh path.
     */
    public fun detach(): SkPath {
        val path = snapshot()
        verbs.clear(); coords.clear(); conicWeights.clear()
        fillType = SkPathFillType.kWinding
        lastX = 0f; lastY = 0f
        contourX = 0f; contourY = 0f
        hasContour = false
        return path
    }

    /** Return a copy of the current accumulated path without resetting. */
    public fun snapshot(): SkPath = SkPath(
        verbs = verbs.toTypedArray(),
        coords = coords.toFloatArray(),
        conicWeights = conicWeights.toFloatArray(),
        fillType = fillType,
    )

    // ----------------------------------------------------------------
    // Internals.
    // ----------------------------------------------------------------

    /**
     * Skia's convention is that `lineTo` / `quadTo` / `cubicTo` etc. on
     * an empty contour implicitly emit a `moveTo(0, 0)`. We preserve it.
     */
    private fun ensureContour() {
        if (!hasContour) moveTo(0f, 0f)
    }

    private fun ovalPointAt(oval: SkRect, angleDeg: SkScalar): Pair<SkScalar, SkScalar> {
        val cx = (oval.left + oval.right) * 0.5f
        val cy = (oval.top + oval.bottom) * 0.5f
        val rx = (oval.right - oval.left) * 0.5f
        val ry = (oval.bottom - oval.top) * 0.5f
        val theta = angleDeg.toDouble() * PI / 180.0
        return (cx + rx * cos(theta).toFloat()) to (cy + ry * sin(theta).toFloat())
    }

    private fun emitArc(
        oval: SkRect, startAngleDeg: SkScalar, sweepAngleDeg: SkScalar,
        forceMoveTo: Boolean,
    ) {
        val cx = (oval.left + oval.right) * 0.5f
        val cy = (oval.top + oval.bottom) * 0.5f
        val rx = (oval.right - oval.left) * 0.5f
        val ry = (oval.bottom - oval.top) * 0.5f
        val startRad = startAngleDeg.toDouble() * PI / 180.0
        val sweepRad = sweepAngleDeg.toDouble() * PI / 180.0
        val nSegs = max(1, ceil(abs(sweepRad) / (PI / 2.0)).toInt())
        val segAngle = sweepRad / nSegs
        val k = (4.0 / 3.0) * tan(segAngle / 4.0)

        val firstX = (cx + rx * cos(startRad)).toFloat()
        val firstY = (cy + ry * sin(startRad)).toFloat()
        if (forceMoveTo || !hasContour) {
            moveTo(firstX, firstY)
        } else if (lastX != firstX || lastY != firstY) {
            lineTo(firstX, firstY)
        }

        var theta = startRad
        for (i in 0 until nSegs) {
            val t1 = theta
            val t2 = theta + segAngle
            val cosT1 = cos(t1); val sinT1 = sin(t1)
            val cosT2 = cos(t2); val sinT2 = sin(t2)
            val p1x = cx + rx * (cosT1 - k * sinT1).toFloat()
            val p1y = cy + ry * (sinT1 + k * cosT1).toFloat()
            val p2x = cx + rx * (cosT2 + k * sinT2).toFloat()
            val p2y = cy + ry * (sinT2 - k * cosT2).toFloat()
            val p3x = cx + rx * cosT2.toFloat()
            val p3y = cy + ry * sinT2.toFloat()
            cubicTo(p1x, p1y, p2x, p2y, p3x, p3y)
            theta = t2
        }
    }

    private companion object {
        /** `(4/3) * (sqrt(2) - 1)` — Hugues' constant for 90° cubic-Bézier circle approximation. */
        const val OVAL_KAPPA: Float = 0.5522847498307933f
    }
}
