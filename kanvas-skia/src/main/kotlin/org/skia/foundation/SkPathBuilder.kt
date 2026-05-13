package org.skia.foundation

import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkScalar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
// SkRRect lives in the same `org.skia.foundation` package — no import needed.

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

    /**
     * Which of the two ellipse arcs (smaller or larger half) connects
     * the start and end points of an SVG-style [arcTo]. Mirrors
     * `SkPathBuilder::ArcSize` (`include/core/SkPathBuilder.h:576-579`).
     */
    public enum class ArcSize { kSmall_ArcSize, kLarge_ArcSize }

    private val verbs: ArrayList<SkPath.StorageVerb> = ArrayList()
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

    /**
     * Build with [fillType] set up front. Mirrors
     * `SkPathBuilder::SkPathBuilder(SkPathFillType)`
     * (`include/core/SkPathBuilder.h:51`).
     */
    public constructor(fillType: SkPathFillType) : this() {
        this.fillType = fillType
    }

    /**
     * Build a copy of [path]: replays every verb / weight from the source
     * and inherits its [fillType]. Mirrors
     * `SkPathBuilder::SkPathBuilder(const SkPath& path)`
     * (`include/core/SkPathBuilder.h:59`).
     */
    public constructor(path: SkPath) : this() {
        this.fillType = path.fillType
        addPath(path)
    }

    public fun isEmpty(): Boolean = verbs.isEmpty()

    /**
     * Number of points stored in the builder so far. Mirrors
     * `SkPathBuilder::countPoints` (`include/core/SkPathBuilder.h:951`).
     */
    public fun countPoints(): Int = coords.size / 2

    /**
     * Last point on the builder's pen (the latest non-`kClose` endpoint),
     * or `null` if no points have been emitted. Mirrors
     * `SkPathBuilder::getLastPt` (`include/core/SkPathBuilder.h:928`).
     */
    public fun getLastPt(): SkPoint? {
        if (coords.isEmpty()) return null
        val n = coords.size
        return SkPoint(coords[n - 2], coords[n - 1])
    }

    /**
     * Reset the builder to empty state — clears verbs / coords / weights
     * and resets [fillType] to `kWinding`. Returns `this` for chaining.
     * Mirrors `SkPathBuilder::reset` (`include/core/SkPathBuilder.h:159`).
     *
     * Identical to the side-effect of [detach], but exposed as a
     * standalone op for callers that want to reuse the builder without
     * materialising a path.
     */
    public fun reset(): SkPathBuilder = apply {
        verbs.clear(); coords.clear(); conicWeights.clear()
        fillType = SkPathFillType.kWinding
        lastX = 0f; lastY = 0f
        contourX = 0f; contourY = 0f
        hasContour = false
    }

    public fun setFillType(t: SkPathFillType): SkPathBuilder = apply { fillType = t }

    /**
     * Read the currently configured fill rule. Mirrors
     * `SkPathBuilder::fillType` (`include/core/SkPathBuilder.h:80`).
     */
    public fun fillType(): SkPathFillType = fillType

    /**
     * True if the configured [fillType] is `kInverseWinding` or
     * `kInverseEvenOdd`. Mirrors `SkPathBuilder::isInverseFillType`
     * (`include/core/SkPathBuilder.h:958`).
     */
    public fun isInverseFillType(): Boolean = fillType.isInverse()

    /**
     * Flip the inverse bit of [fillType] in place; returns `this` for
     * chaining. Mirrors `SkPathBuilder::toggleInverseFillType`
     * (`include/core/SkPathBuilder.h:909-912`).
     */
    public fun toggleInverseFillType(): SkPathBuilder = apply {
        fillType = fillType.toggleInverse()
    }

    public fun moveTo(x: SkScalar, y: SkScalar): SkPathBuilder = apply {
        // Mirrors SkPathBuilder.cpp:136-156 — if the previous verb is also a
        // move, replace its point in place. Each contour can carry at most
        // one move verb (the last one specified).
        if (verbs.isNotEmpty() && verbs.last() == SkPath.StorageVerb.kMove) {
            val n = coords.size
            coords[n - 2] = x
            coords[n - 1] = y
        } else {
            verbs.add(SkPath.StorageVerb.kMove); coords.add(x); coords.add(y)
        }
        lastX = x; lastY = y
        contourX = x; contourY = y
        hasContour = true
    }

    public fun lineTo(x: SkScalar, y: SkScalar): SkPathBuilder = apply {
        ensureContour()
        verbs.add(SkPath.StorageVerb.kLine); coords.add(x); coords.add(y)
        lastX = x; lastY = y
    }

    public fun quadTo(
        x1: SkScalar, y1: SkScalar, x2: SkScalar, y2: SkScalar,
    ): SkPathBuilder = apply {
        ensureContour()
        verbs.add(SkPath.StorageVerb.kQuad)
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
                verbs.add(SkPath.StorageVerb.kConic)
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
        verbs.add(SkPath.StorageVerb.kCubic)
        coords.add(x1); coords.add(y1)
        coords.add(x2); coords.add(y2)
        coords.add(x3); coords.add(y3)
        lastX = x3; lastY = y3
    }

    public fun close(): SkPathBuilder = apply {
        if (hasContour) {
            verbs.add(SkPath.StorageVerb.kClose)
            lastX = contourX; lastY = contourY
            hasContour = false
        }
    }

    /**
     * Append `lineTo` for each point in [pts] (no leading `moveTo` —
     * uses the current pen). Mirrors `SkPathBuilder::polylineTo`
     * (`include/core/SkPathBuilder.h:381`).
     */
    public fun polylineTo(pts: Array<Pair<SkScalar, SkScalar>>): SkPathBuilder = apply {
        if (pts.isEmpty()) return@apply
        ensureContour()
        for (p in pts) {
            verbs.add(SkPath.StorageVerb.kLine)
            coords.add(p.first); coords.add(p.second)
            lastX = p.first; lastY = p.second
        }
    }

    /**
     * Equivalent to `moveTo(a).lineTo(b)`. Mirrors
     * `SkPathBuilder::addLine` (`include/core/SkPathBuilder.h:689-691`).
     */
    public fun addLine(
        ax: SkScalar, ay: SkScalar,
        bx: SkScalar, by: SkScalar,
    ): SkPathBuilder = moveTo(ax, ay).lineTo(bx, by)

    // ----------------------------------------------------------------
    // Relative variants — origin at the current pen position.
    // Mirror Skia's `r*` family. SVG path data emits these heavily, so
    // the convenience matters even though every relative call is just
    // `(lastX + dx, lastY + dy)` re-routed to the absolute primitive.
    // ----------------------------------------------------------------

    public fun rMoveTo(dx: SkScalar, dy: SkScalar): SkPathBuilder =
        moveTo(lastX + dx, lastY + dy)

    public fun rLineTo(dx: SkScalar, dy: SkScalar): SkPathBuilder =
        lineTo(lastX + dx, lastY + dy)

    public fun rQuadTo(
        dx1: SkScalar, dy1: SkScalar, dx2: SkScalar, dy2: SkScalar,
    ): SkPathBuilder =
        quadTo(lastX + dx1, lastY + dy1, lastX + dx2, lastY + dy2)

    public fun rConicTo(
        dx1: SkScalar, dy1: SkScalar, dx2: SkScalar, dy2: SkScalar, w: SkScalar,
    ): SkPathBuilder =
        conicTo(lastX + dx1, lastY + dy1, lastX + dx2, lastY + dy2, w)

    public fun rCubicTo(
        dx1: SkScalar, dy1: SkScalar,
        dx2: SkScalar, dy2: SkScalar,
        dx3: SkScalar, dy3: SkScalar,
    ): SkPathBuilder = cubicTo(
        lastX + dx1, lastY + dy1,
        lastX + dx2, lastY + dy2,
        lastX + dx3, lastY + dy3,
    )

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

    /**
     * **Tangent arc** (PostScript-style `arct`). Mirrors Skia's
     * `SkPathBuilder::arcTo(SkPoint p1, SkPoint p2, SkScalar radius)`
     * (`src/core/SkPathBuilder.cpp:477-511`).
     *
     * Given the current pen position `P0`, append a circular arc of
     * the given `radius` that is tangent to the line segment `P0→p1`
     * at one end and tangent to `p1→p2` at the other. The arc replaces
     * the sharp corner at `p1`. Emits at most one `lineTo` (to the
     * first tangent point) followed by exactly one `conicTo` — matching
     * the upstream verb-stream guarantee.
     *
     * Degenerate cases (all match Skia):
     *  - Empty builder → `moveTo(p1)` and return (port-legacy fast-path,
     *    pinned by `tangent arcTo on empty path`).
     *  - `radius == 0` → `lineTo(p1)`.
     *  - `P0 == p1` or `p1 == p2` (`befored`/`afterd` denormalised), or
     *    `sinh ≈ 0` (collinear) → `lineTo(p1)`.
     *
     * Math (in double precision):
     * ```
     * cosh = before · after          (between unit vectors P0→p1 and p1→p2)
     * sinh = before × after
     * d    = |radius * (1 - cosh) / sinh|     (distance from p1 to tangent points)
     * T0   = p1 - d · before                  (first tangent point)
     * T1   = p1 + d · after                   (second tangent point)
     * weight = sqrt(0.5 + 0.5·cosh) = cos(arc-sweep / 2)
     * ```
     */
    public fun arcTo(
        x1: SkScalar, y1: SkScalar,
        x2: SkScalar, y2: SkScalar,
        radius: SkScalar,
    ): SkPathBuilder = apply {
        // Empty-builder fast path preserved (port-legacy behaviour).
        if (verbs.isEmpty()) { moveTo(x1, y1); return@apply }
        ensureContour()
        if (radius == 0f) { lineTo(x1, y1); return@apply }

        val p0x = lastX.toDouble(); val p0y = lastY.toDouble()
        val bx = x1.toDouble() - p0x; val by = y1.toDouble() - p0y
        val ax = x2.toDouble() - x1.toDouble(); val ay = y2.toDouble() - y1.toDouble()
        val blen = sqrt(bx * bx + by * by)
        val alen = sqrt(ax * ax + ay * ay)
        if (!blen.isFinite() || !alen.isFinite() || blen == 0.0 || alen == 0.0) {
            lineTo(x1, y1); return@apply
        }
        val ubx = bx / blen; val uby = by / blen
        val uax = ax / alen; val uay = ay / alen
        val cosh = ubx * uax + uby * uay
        val sinh = ubx * uay - uby * uax
        // Skia uses `SkScalarNearlyZero` ≈ 1/4096 = 0.000244 — collinear bail.
        if (!cosh.isFinite() || !sinh.isFinite() || abs(sinh) < 1.0 / 4096.0) {
            lineTo(x1, y1); return@apply
        }

        val dist = abs(radius.toDouble() * (1.0 - cosh) / sinh)
        val t0x = (x1.toDouble() - dist * ubx).toFloat()
        val t0y = (y1.toDouble() - dist * uby).toFloat()
        val t1x = (x1.toDouble() + dist * uax).toFloat()
        val t1y = (y1.toDouble() + dist * uay).toFloat()

        if (t0x != lastX || t0y != lastY) lineTo(t0x, t0y)
        // Conic weight = cos(arc-sweep / 2). Half-angle identity:
        //   cos²(α/2) = (1 + cos α) / 2  →  weight = sqrt(0.5 + 0.5·cosh).
        val weight = sqrt(0.5 + cosh * 0.5).toFloat()
        conicTo(x1, y1, t1x, t1y, weight)
    }

    /**
     * **SVG arc, absolute endpoint.** Mirrors
     * `SkPathBuilder::arcTo(SkPoint r, SkScalar xAxisRotate, ArcSize, SkPathDirection sweep, SkPoint xy)`
     * (`include/core/SkPathBuilder.h:670`,
     * `src/core/SkPathBuilder.cpp:519-645`). Implements the SVG
     * endpoint-to-conic conversion specified in
     * <https://www.w3.org/TR/SVG/implnote.html#ArcConversionEndpointToCenter>.
     *
     * Appends one or more conic Bézier curves connecting the current
     * pen position to `(x, y)`, tracing an elliptical arc with radii
     * `(rx, ry)` rotated by `xAxisRotateDeg` about the origin. The
     * `largeArc` / `sweep` flags pick one of the four possible arcs.
     *
     * Degenerate cases (match Skia behaviour):
     *  - `rx == 0` or `ry == 0` → emit `lineTo(x, y)`.
     *  - start == end → emit `lineTo(x, y)`.
     *  - tiny `thetaArc` (< π / 1e6) → emit `lineTo(x, y)`.
     *
     * If the supplied radii are too small to span the chord, they are
     * scaled up uniformly per the SVG spec.
     */
    public fun arcTo(
        rx: SkScalar, ry: SkScalar,
        xAxisRotateDeg: SkScalar,
        arcLarge: ArcSize,
        arcSweep: SkPathDirection,
        x: SkScalar, y: SkScalar,
    ): SkPathBuilder = apply {
        ensureContour()
        if (rx == 0f || ry == 0f) { lineTo(x, y); return@apply }
        val startX = lastX; val startY = lastY
        if (startX == x && startY == y) { lineTo(x, y); return@apply }

        var rxAbs = abs(rx); var ryAbs = abs(ry)
        val midX = (startX - x) * 0.5f
        val midY = (startY - y) * 0.5f

        // Rotate(-angle) on the half-chord midpoint vector.
        val rotMinus = SkMatrix.MakeRotate(-xAxisRotateDeg)
        val (tmX, tmY) = rotMinus.mapXY(midX, midY)

        val sqRx = rxAbs * rxAbs; val sqRy = ryAbs * ryAbs
        val sqX = tmX * tmX; val sqY = tmY * tmY

        // Scale radii up if the chord doesn't fit (SVG `OutOfRangeRadii`).
        var radiiScale = sqX / sqRx + sqY / sqRy
        if (radiiScale > 1f) {
            radiiScale = sqrt(radiiScale.toDouble()).toFloat()
            rxAbs *= radiiScale; ryAbs *= radiiScale
        }

        // Map start and end to the unit circle: scale(1/rx, 1/ry) ∘ rotate(-angle).
        var pointTransform = SkMatrix.MakeScale(1f / rxAbs, 1f / ryAbs)
            .preRotate(-xAxisRotateDeg)
        val (u0x, u0y) = pointTransform.mapXY(startX, startY)
        val (u1x, u1y) = pointTransform.mapXY(x, y)
        val deltaX = u1x - u0x; val deltaY = u1y - u0y
        val d = deltaX * deltaX + deltaY * deltaY

        // Center offset: perpendicular to the chord, length tied to the
        // chord-radius ratio. Sign flipped per (sweep, largeArc) parity.
        val scaleSq = max(1f / d - 0.25f, 0f)
        var scaleFactor = sqrt(scaleSq.toDouble()).toFloat()
        if ((arcSweep == SkPathDirection.kCCW) != (arcLarge == ArcSize.kLarge_ArcSize)) {
            scaleFactor = -scaleFactor
        }
        val sdx = deltaX * scaleFactor; val sdy = deltaY * scaleFactor
        val centerX = (u0x + u1x) * 0.5f - sdy
        val centerY = (u0y + u1y) * 0.5f + sdx

        val u0xc = (u0x - centerX).toDouble(); val u0yc = (u0y - centerY).toDouble()
        val u1xc = (u1x - centerX).toDouble(); val u1yc = (u1y - centerY).toDouble()
        val theta1 = atan2(u0yc, u0xc)
        val theta2 = atan2(u1yc, u1xc)
        var thetaArc = theta2 - theta1
        if (thetaArc < 0 && arcSweep == SkPathDirection.kCW) {
            thetaArc += 2.0 * PI
        } else if (thetaArc > 0 && arcSweep != SkPathDirection.kCW) {
            thetaArc -= 2.0 * PI
        }

        // Tiny angles produce numerically unstable conics — emit a line
        // (Skia: skbug.com/40040578). Threshold mirrors upstream.
        if (abs(thetaArc) < PI / 1_000_000.0) {
            lineTo(x, y); return@apply
        }

        // Final transform: rotate(angle) ∘ scale(rx, ry).
        pointTransform = SkMatrix.MakeRotate(xAxisRotateDeg).preScale(rxAbs, ryAbs)

        // Up to 1/3 of a full turn per segment (≤120°), Skia convention.
        val segments = ceil(abs(thetaArc) / (2.0 * PI / 3.0)).toInt()
        val thetaWidth = thetaArc / segments
        val tParam = tan(0.5 * thetaWidth)
        if (!tParam.isFinite()) return@apply
        val w = sqrt(0.5 + cos(thetaWidth) * 0.5).toFloat()

        var startTheta = theta1
        for (i in 0 until segments) {
            val endTheta = startTheta + thetaWidth
            val sinEnd = sin(endTheta)
            val cosEnd = cos(endTheta)
            val u1xR = (centerX + cosEnd).toFloat()
            val u1yR = (centerY + sinEnd).toFloat()
            // Conic control: tangent extension from the segment end.
            val u0xR = (u1xR + tParam * sinEnd).toFloat()
            val u0yR = (u1yR + (-tParam * cosEnd)).toFloat()
            val (mx0, my0) = pointTransform.mapXY(u0xR, u0yR)
            val (mx1, my1) = pointTransform.mapXY(u1xR, u1yR)
            // Snap the final segment's end point exactly to (x, y) to
            // erase rounding error accumulated through the multi-stage
            // matrix chain (Skia mirrors this with `fPts.back() = endPt`).
            val ex = if (i == segments - 1) x else mx1
            val ey = if (i == segments - 1) y else my1
            conicTo(mx0, my0, ex, ey, w)
            startTheta = endTheta
        }
    }

    /**
     * **SVG arc, relative endpoint.** Mirrors
     * `SkPathBuilder::rArcTo(SkPoint r, SkScalar xAxisRotate, ArcSize, SkPathDirection sweep, SkVector dxdy)`
     * (`include/core/SkPathBuilder.h:605`). Equivalent to
     * `arcTo(rx, ry, xAxisRotateDeg, arcLarge, arcSweep, lastX + dx, lastY + dy)`.
     */
    public fun rArcTo(
        rx: SkScalar, ry: SkScalar,
        xAxisRotateDeg: SkScalar,
        arcLarge: ArcSize,
        arcSweep: SkPathDirection,
        dx: SkScalar, dy: SkScalar,
    ): SkPathBuilder = arcTo(rx, ry, xAxisRotateDeg, arcLarge, arcSweep, lastX + dx, lastY + dy)

    public fun addRect(
        rect: SkRect,
        dir: SkPathDirection = SkPathDirection.kCW,
    ): SkPathBuilder = addRect(rect, dir, startIndex = 0)

    /**
     * Append a closed rectangular contour (`kMove + 3×kLine + kClose`)
     * starting at the corner selected by [startIndex] (`0..3`, indexed CW
     * from top-left) and wound in [dir]. Mirrors
     * `SkPathBuilder::addRect(SkRect, SkPathDirection, unsigned startIndex)`
     * (`include/core/SkPathBuilder.h:716`,
     * `src/core/SkPathBuilder.cpp` `addRect` impl).
     *
     * The verb stream length and shape is invariant across `startIndex`;
     * only the order of corner emission rotates.
     */
    public fun addRect(
        rect: SkRect,
        dir: SkPathDirection,
        startIndex: Int,
    ): SkPathBuilder = apply {
        // 4 corners in CW order from top-left.
        val cornerX = floatArrayOf(rect.left, rect.right, rect.right, rect.left)
        val cornerY = floatArrayOf(rect.top,  rect.top,   rect.bottom, rect.bottom)
        val advance = if (dir == SkPathDirection.kCW) 1 else 3
        var i = ((startIndex % 4) + 4) % 4
        moveTo(cornerX[i], cornerY[i])
        repeat(3) {
            i = (i + advance) % 4
            lineTo(cornerX[i], cornerY[i])
        }
        close()
    }

    /**
     * Append an axis-aligned ellipse contour as `kMove + 4 × kConic + kClose`,
     * each conic representing a 90° quarter-ellipse with weight `√2/2`.
     * Mirrors `SkPathRawShapes::Oval` (`src/core/SkPathRawShapes.cpp:48-86`)
     * — the conic representation is exact for the analytical ellipse
     * (vs. the cubic kappa approximation, which carries ~0.027 % chord
     * error). Conic control points coincide with the bounding-rect
     * corners; conic ends are the cardinal points of the oval.
     *
     * Uses the `startIndex = 1` convention from Skia 4.x (default
     * `addOval(oval, dir)`), which begins the contour at
     * `(oval.right, oval.centerY())`.
     */
    public fun addOval(
        oval: SkRect,
        dir: SkPathDirection = SkPathDirection.kCW,
    ): SkPathBuilder = addOval(oval, dir, startIndex = 1)

    /**
     * Append a closed oval contour (`kMove + 4×kConic + kClose`,
     * each conic a 90° quarter-ellipse with weight `√2/2`) starting at
     * the cardinal selected by [startIndex] (`0..3` mapping to
     * top / right / bottom / left of the bounding rect respectively, CW)
     * and wound in [dir]. Mirrors
     * `SkPathBuilder::addOval(SkRect, SkPathDirection, unsigned startIndex)`
     * (`include/core/SkPathBuilder.h:747`, `set_as_oval` in
     * `src/core/SkPathRawShapes.cpp:66-86`).
     *
     * The default `startIndex = 1` reproduces Skia 4.x's legacy default:
     * the contour begins at `(oval.right, centerY)`.
     */
    public fun addOval(
        oval: SkRect,
        dir: SkPathDirection,
        startIndex: Int,
    ): SkPathBuilder = apply {
        val cx = (oval.left + oval.right) * 0.5f
        val cy = (oval.top + oval.bottom) * 0.5f
        val l = oval.left; val t = oval.top
        val r = oval.right; val b = oval.bottom
        val w = OVAL_CONIC_WEIGHT

        // 4 cardinals in CW order: 0 top, 1 right, 2 bottom, 3 left.
        val ovalX = floatArrayOf(cx, r,  cx, l)
        val ovalY = floatArrayOf(t,  cy, b,  cy)
        // 4 bbox corners in CW order from top-left.
        val cornerX = floatArrayOf(l, r, r, l)
        val cornerY = floatArrayOf(t, t, b, b)

        // Skia mirrors set_as_oval: ovalIter starts at startIndex; rectIter
        // starts at startIndex + (CW ? 0 : 1). For CW, the conic control for
        // the segment from cardinal i → cardinal (i+1)%4 is corner (i+1)%4
        // (the bbox corner enclosed in that quadrant). For CCW, the segment
        // is cardinal i → cardinal (i-1)%4 with control at corner i.
        val advance = if (dir == SkPathDirection.kCW) 1 else 3
        var ovalIdx = ((startIndex % 4) + 4) % 4
        var rectIdx = ((startIndex + (if (dir == SkPathDirection.kCW) 0 else 1)) % 4 + 4) % 4

        moveTo(ovalX[ovalIdx], ovalY[ovalIdx])
        repeat(4) {
            rectIdx = (rectIdx + advance) % 4
            ovalIdx = (ovalIdx + advance) % 4
            conicTo(cornerX[rectIdx], cornerY[rectIdx], ovalX[ovalIdx], ovalY[ovalIdx], w)
        }
        close()
    }

    public fun addCircle(
        cx: SkScalar, cy: SkScalar, r: SkScalar,
        dir: SkPathDirection = SkPathDirection.kCW,
    ): SkPathBuilder = addOval(SkRect.MakeLTRB(cx - r, cy - r, cx + r, cy + r), dir)

    /**
     * Append a rounded rectangle contour. Mirrors Skia's
     * `SkPathBuilder::addRRect(rrect, dir)`. Defers to [addRect] / [addOval]
     * when the [SkRRect.Type] degenerates and otherwise emits 4 lines + 4
     * cubic-Bézier corners using the same kappa approximation as [addOval].
     *
     * **Note**: Skia's optional `startIndex` parameter is not yet exposed —
     * the contour always starts at the top-left corner's end-of-arc on the
     * top edge (`(left + tlRx, top)`), then proceeds in the requested
     * direction. None of the Phase 4 GMs we plan to port care about
     * `startIndex`; will be added if needed.
     */
    public fun addRRect(
        rrect: SkRRect,
        dir: SkPathDirection = SkPathDirection.kCW,
    ): SkPathBuilder = addRRect(rrect, dir, startIndex = 0)

    /**
     * Append a closed rounded-rect contour starting at the cardinal
     * selected by [startIndex] (`0..7` indexed CW from "top edge, just
     * past the TL corner") and wound in [dir]. Mirrors
     * `SkPathBuilder::addRRect(SkRRect, SkPathDirection, unsigned startIndex)`
     * (`include/core/SkPathBuilder.h:759`,
     * `src/core/SkPathRawShapes.cpp:107-160`).
     *
     * Verb stream variant depends on the `(startIndex, dir)` parity:
     * - **LineStart** (`(startIndex & 1) != (dir == kCW)`):
     *   `kMove + (kLine + kConic) × 4 + kClose` (10 verbs).
     * - **ConicStart** (`(startIndex & 1) == (dir == kCW)`):
     *   `kMove + (kConic + kLine) × 3 + kConic + kClose` (9 verbs — the
     *   trailing line back to the move point is supplied implicitly by
     *   `close()`).
     *
     * **Default `startIndex = 0`** preserves the port's pre-3.5 contour
     * (top edge, after TL corner). Skia 4.x's `addRRect(rrect, dir)`
     * default is `6` for CW / `7` for CCW (legacy compatibility) — opt
     * in by passing the index explicitly.
     */
    public fun addRRect(
        rrect: SkRRect,
        dir: SkPathDirection,
        startIndex: Int,
    ): SkPathBuilder = apply {
        when (rrect.getType()) {
            SkRRect.Type.kEmpty_Type -> return@apply
            // For collapsed types (kRect / kOval), defer to the matching
            // helper with its *own* default startIndex. Skia 4.x's
            // SkPathPriv::SimplifyRRect remaps the rrect's index to a new
            // index on the underlying primitive — that's a Phase 3.5+
            // refinement; today we keep the pre-3.5 collapse behaviour
            // (rect: startIndex 0, oval: startIndex 1) so legacy callers
            // see no shift on their default `addRRect(rrect)` path.
            SkRRect.Type.kRect_Type -> { addRect(rrect.rect(), dir); return@apply }
            SkRRect.Type.kOval_Type -> { addOval(rrect.rect(), dir); return@apply }
            SkRRect.Type.kSimple_Type,
            SkRRect.Type.kNinePatch_Type,
            SkRRect.Type.kComplex_Type -> emitRRectCorners(rrect, dir, startIndex)
        }
    }

    private fun emitRRectCorners(rrect: SkRRect, dir: SkPathDirection, startIndex: Int) {
        val rect = rrect.rect()
        val tl = rrect.radii(SkRRect.Corner.kUpperLeft_Corner)
        val tr = rrect.radii(SkRRect.Corner.kUpperRight_Corner)
        val br = rrect.radii(SkRRect.Corner.kLowerRight_Corner)
        val bl = rrect.radii(SkRRect.Corner.kLowerLeft_Corner)
        val w = OVAL_CONIC_WEIGHT
        val l = rect.left; val t = rect.top
        val r = rect.right; val b = rect.bottom

        // 8 cardinal points on the bbox edges, indexed CW from "top edge,
        // just past TL corner". Each odd-indexed point is "before" the next
        // CW corner; each even-indexed point is "after" the previous corner.
        //   0 (L+tl.x, T)        1 (R-tr.x, T)       — top edge
        //   2 (R, T+tr.y)        3 (R, B-br.y)       — right edge
        //   4 (R-br.x, B)        5 (L+bl.x, B)       — bottom edge
        //   6 (L, B-bl.y)        7 (L, T+tl.y)       — left edge
        val cardX = floatArrayOf(l + tl.fX, r - tr.fX, r,        r,        r - br.fX, l + bl.fX, l,        l)
        val cardY = floatArrayOf(t,         t,         t + tr.fY, b - br.fY, b,        b,         b - bl.fY, t + tl.fY)
        // 4 bbox corners as conic control points, indexed by the *enclosed*
        // CW corner (0 = TR, 1 = BR, 2 = BL, 3 = TL). The corner sitting
        // between cardinals (2k - 1) and (2k) — modulo 8 — is corner k - 1
        // mod 4 in CW.
        val cornerX = floatArrayOf(r, r, l, l)
        val cornerY = floatArrayOf(t, b, b, t)

        val advance = if (dir == SkPathDirection.kCW) 1 else -1
        val idx0 = ((startIndex % 8) + 8) % 8
        // In CW, segment idx → (idx + 1) mod 8 is a conic iff idx is odd.
        // In CCW, segment idx → (idx - 1) mod 8 is a conic iff idx is even.
        val firstSegmentConic = (idx0 and 1) == if (dir == SkPathDirection.kCW) 1 else 0
        // LineStart variant emits 8 segments (4 lines + 4 conics, all explicit).
        // ConicStart variant emits 7 (the trailing line back to idx0 is
        // implicit via close()).
        val segments = if (firstSegmentConic) 7 else 8

        moveTo(cardX[idx0], cardY[idx0])
        var idx = idx0
        repeat(segments) {
            val nextIdx = ((idx + advance) % 8 + 8) % 8
            val isConic = (idx and 1) == if (dir == SkPathDirection.kCW) 1 else 0
            if (isConic) {
                // Corner index for the current segment.
                //   CW : segment idx (odd) → cornerIdx = idx >> 1.
                //   CCW: segment idx (even) → cornerIdx = nextIdx >> 1.
                val cornerIdx = if (dir == SkPathDirection.kCW) idx ushr 1 else nextIdx ushr 1
                conicTo(cornerX[cornerIdx], cornerY[cornerIdx], cardX[nextIdx], cardY[nextIdx], w)
            } else {
                lineTo(cardX[nextIdx], cardY[nextIdx])
            }
            idx = nextIdx
        }
        close()
    }

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

    /**
     * Append every verb of [path] to this builder, optionally transforming
     * the source coords by [matrix] and choosing the join behaviour with
     * [mode]. Mirrors `SkPathBuilder::addPath(SkPath, SkMatrix, AddPathMode)`
     * (`include/core/SkPathBuilder.h:861`,
     * `src/core/SkPathBuilder.cpp:776-800`).
     *
     * - **Affine-only**: the matrix is applied component-wise to each
     *   stored point. Conic weights are projectively invariant under
     *   affine maps, so they pass through unchanged. Perspective
     *   matrices are not supported here (same limitation as
     *   [SkPath.makeTransform] / [transform]).
     * - **kExtend** with non-empty destination: the source's first
     *   `kMove` is replaced by a `lineTo` to the (mapped) move target.
     *   Subsequent `kMove` verbs in the source come through as `moveTo`
     *   (Skia only extends the *first* contour).
     * - Empty source is a no-op.
     */
    public fun addPath(
        path: SkPath,
        matrix: SkMatrix = SkMatrix.Identity,
        mode: SkPath.AddPathMode = SkPath.AddPathMode.kAppend,
    ): SkPathBuilder = apply {
        if (path.isEmpty()) return@apply
        val identity = matrix.isIdentity
        val sx = matrix.sx; val kx = matrix.kx; val tx = matrix.tx
        val ky = matrix.ky; val sy = matrix.sy; val ty = matrix.ty
        var coordIdx = 0
        var weightIdx = 0
        val src = path.coords
        var firstMoveSeen = false
        val extending = mode == SkPath.AddPathMode.kExtend && !this@SkPathBuilder.isEmpty()
        for (verb in path.verbs) {
            when (verb) {
                SkPath.StorageVerb.kMove -> {
                    val x0 = src[coordIdx++]; val y0 = src[coordIdx++]
                    val mx = if (identity) x0 else sx * x0 + kx * y0 + tx
                    val my = if (identity) y0 else ky * x0 + sy * y0 + ty
                    if (extending && !firstMoveSeen) {
                        // Replace the source's first kMove with a line to the
                        // mapped move target — extends the dest's last contour.
                        // ensureContour (called inside lineTo) re-anchors the
                        // pen if the dest's last verb is kClose.
                        lineTo(mx, my)
                    } else {
                        moveTo(mx, my)
                    }
                    firstMoveSeen = true
                }
                SkPath.StorageVerb.kLine -> {
                    val x1 = src[coordIdx++]; val y1 = src[coordIdx++]
                    if (identity) lineTo(x1, y1)
                    else lineTo(sx * x1 + kx * y1 + tx, ky * x1 + sy * y1 + ty)
                }
                SkPath.StorageVerb.kQuad -> {
                    val x1 = src[coordIdx++]; val y1 = src[coordIdx++]
                    val x2 = src[coordIdx++]; val y2 = src[coordIdx++]
                    if (identity) quadTo(x1, y1, x2, y2)
                    else quadTo(
                        sx * x1 + kx * y1 + tx, ky * x1 + sy * y1 + ty,
                        sx * x2 + kx * y2 + tx, ky * x2 + sy * y2 + ty,
                    )
                }
                SkPath.StorageVerb.kConic -> {
                    val x1 = src[coordIdx++]; val y1 = src[coordIdx++]
                    val x2 = src[coordIdx++]; val y2 = src[coordIdx++]
                    val w = path.conicWeights[weightIdx++]
                    if (identity) conicTo(x1, y1, x2, y2, w)
                    else conicTo(
                        sx * x1 + kx * y1 + tx, ky * x1 + sy * y1 + ty,
                        sx * x2 + kx * y2 + tx, ky * x2 + sy * y2 + ty,
                        w,
                    )
                }
                SkPath.StorageVerb.kCubic -> {
                    val x1 = src[coordIdx++]; val y1 = src[coordIdx++]
                    val x2 = src[coordIdx++]; val y2 = src[coordIdx++]
                    val x3 = src[coordIdx++]; val y3 = src[coordIdx++]
                    if (identity) cubicTo(x1, y1, x2, y2, x3, y3)
                    else cubicTo(
                        sx * x1 + kx * y1 + tx, ky * x1 + sy * y1 + ty,
                        sx * x2 + kx * y2 + tx, ky * x2 + sy * y2 + ty,
                        sx * x3 + kx * y3 + tx, ky * x3 + sy * y3 + ty,
                    )
                }
                SkPath.StorageVerb.kClose -> close()
            }
        }
    }

    /**
     * Translate-only convenience overload. Mirrors
     * `SkPathBuilder::addPath(SkPath, SkScalar dx, SkScalar dy, AddPathMode)`
     * (`include/core/SkPathBuilder.h:831`). Equivalent to
     * `addPath(src, SkMatrix.MakeTrans(dx, dy), mode)`.
     */
    public fun addPath(
        src: SkPath,
        dx: SkScalar,
        dy: SkScalar,
        mode: SkPath.AddPathMode = SkPath.AddPathMode.kAppend,
    ): SkPathBuilder = addPath(src, SkMatrix.MakeTrans(dx, dy), mode)

    /**
     * Append every verb of `path` to this builder, translating each
     * coordinate pair by `(dx, dy)`. Behaviourally equivalent to
     * `addPath(path)` followed by a translate of the appended verbs,
     * but does the translation inline to avoid a second pass.
     *
     * Used by the T5 glyph-cache path: cached glyph outlines are stored
     * with origin `(0, 0)` and re-emitted at `(glyphX, glyphY)` for
     * each occurrence in a string.
     */
    public fun addPathOffset(path: SkPath, dx: SkScalar, dy: SkScalar): SkPathBuilder = apply {
        if (dx == 0f && dy == 0f) {
            addPath(path)
            return@apply
        }
        var coordIdx = 0
        var weightIdx = 0
        val src = path.coords
        for (verb in path.verbs) {
            when (verb) {
                SkPath.StorageVerb.kMove -> moveTo(src[coordIdx++] + dx, src[coordIdx++] + dy)
                SkPath.StorageVerb.kLine -> lineTo(src[coordIdx++] + dx, src[coordIdx++] + dy)
                SkPath.StorageVerb.kQuad -> {
                    val x1 = src[coordIdx++] + dx; val y1 = src[coordIdx++] + dy
                    val x2 = src[coordIdx++] + dx; val y2 = src[coordIdx++] + dy
                    quadTo(x1, y1, x2, y2)
                }
                SkPath.StorageVerb.kConic -> {
                    val x1 = src[coordIdx++] + dx; val y1 = src[coordIdx++] + dy
                    val x2 = src[coordIdx++] + dx; val y2 = src[coordIdx++] + dy
                    val w = path.conicWeights[weightIdx++]
                    conicTo(x1, y1, x2, y2, w)
                }
                SkPath.StorageVerb.kCubic -> {
                    val x1 = src[coordIdx++] + dx; val y1 = src[coordIdx++] + dy
                    val x2 = src[coordIdx++] + dx; val y2 = src[coordIdx++] + dy
                    val x3 = src[coordIdx++] + dx; val y3 = src[coordIdx++] + dy
                    cubicTo(x1, y1, x2, y2, x3, y3)
                }
                SkPath.StorageVerb.kClose -> close()
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
        reset()
        return path
    }

    /** Return a copy of the current accumulated path without resetting. */
    public fun snapshot(): SkPath = SkPath(
        verbs = verbs.toTypedArray(),
        coords = coords.toFloatArray(),
        conicWeights = conicWeights.toFloatArray(),
        fillType = fillType,
    )

    /**
     * Performance hint: pre-allocate space for [extraPtCount] additional
     * coords (= 2 * point count), [extraVerbCount] verbs and
     * [extraConicCount] conic weights. Mirrors
     * `SkPathBuilder::incReserve` (`include/core/SkPathBuilder.h:874`).
     *
     * Backed by `java.util.ArrayList::ensureCapacity`. Sub-1 hints are
     * silently dropped — Skia's contract.
     */
    public fun incReserve(
        extraPtCount: Int,
        extraVerbCount: Int = extraPtCount,
        extraConicCount: Int = 0,
    ): SkPathBuilder = apply {
        if (extraPtCount > 0) coords.ensureCapacity(coords.size + 2 * extraPtCount)
        if (extraVerbCount > 0) verbs.ensureCapacity(verbs.size + extraVerbCount)
        if (extraConicCount > 0) conicWeights.ensureCapacity(conicWeights.size + extraConicCount)
    }

    /**
     * Translate every stored point by `(dx, dy)`. Mutates the builder.
     * Mirrors `SkPathBuilder::offset`
     * (`include/core/SkPathBuilder.h:891`, `src/core/SkPathBuilder.cpp:769-774`).
     *
     * Updates pen position [lastX]/[lastY] and contour-start
     * [contourX]/[contourY] in lockstep so subsequent verbs and
     * `close()` keep the right reference.
     */
    public fun offset(dx: SkScalar, dy: SkScalar): SkPathBuilder = apply {
        if (dx == 0f && dy == 0f) return@apply
        var i = 0
        while (i < coords.size) {
            coords[i] = coords[i] + dx
            coords[i + 1] = coords[i + 1] + dy
            i += 2
        }
        lastX += dx; lastY += dy
        contourX += dx; contourY += dy
    }

    /**
     * Apply [m] to every stored point. Mutates the builder. Mirrors
     * `SkPathBuilder::transform` (`include/core/SkPathBuilder.h:899`)
     * for the affine case — verb stream and conic weights stay
     * untouched (Bézier control points transform naturally under
     * affine maps). Identity matrix is a no-op.
     *
     * Perspective matrices are *not* supported here (Skia tessellates
     * curves to handle them); use the immutable [SkPath.makeTransform]
     * which has the same affine-only limitation, or wait for a
     * dedicated perspective path-flattening slice.
     */
    public fun transform(m: SkMatrix): SkPathBuilder = apply {
        if (m.isIdentity) return@apply
        val sx = m.sx; val kx = m.kx; val tx = m.tx
        val ky = m.ky; val sy = m.sy; val ty = m.ty
        var i = 0
        while (i < coords.size) {
            val x = coords[i]; val y = coords[i + 1]
            coords[i] = sx * x + kx * y + tx
            coords[i + 1] = ky * x + sy * y + ty
            i += 2
        }
        val nlx = sx * lastX + kx * lastY + tx
        val nly = ky * lastX + sy * lastY + ty
        val ncx = sx * contourX + kx * contourY + tx
        val ncy = ky * contourX + sy * contourY + ty
        lastX = nlx; lastY = nly
        contourX = ncx; contourY = ncy
    }

    /**
     * Replace the point at `coords[2*index]/coords[2*index + 1]` with
     * `(p.fX, p.fY)`. Out-of-range indices are silently ignored. Mirrors
     * `SkPathBuilder::setPoint` (`include/core/SkPathBuilder.h:936`).
     *
     * Use sparingly — mutating a stored point can desynchronise the pen
     * (`lastX`/`lastY`) from the verb-stream tail. Callers that need a
     * consistent pen state should also call [setLastPt].
     */
    public fun setPoint(index: Int, p: SkPoint): SkPathBuilder = apply {
        val flat = index * 2
        if (flat < 0 || flat + 1 >= coords.size) return@apply
        coords[flat] = p.fX
        coords[flat + 1] = p.fY
    }

    /**
     * Set the last point on the builder. If the builder is empty, emit
     * `moveTo(x, y)`. Otherwise overwrite the trailing coord pair and
     * keep the pen position aligned. Mirrors `SkPathBuilder::setLastPt`
     * (`include/core/SkPathBuilder.h:944`).
     */
    public fun setLastPt(x: SkScalar, y: SkScalar): SkPathBuilder = apply {
        if (coords.isEmpty()) {
            moveTo(x, y); return@apply
        }
        val n = coords.size
        coords[n - 2] = x
        coords[n - 1] = y
        lastX = x; lastY = y
    }

    // ----------------------------------------------------------------
    // Internals.
    // ----------------------------------------------------------------

    /**
     * Skia's convention is that `lineTo` / `quadTo` / `cubicTo` etc. on
     * an empty contour implicitly emit a move:
     *  - empty builder → `moveTo(0, 0)`
     *  - last verb is `kClose` → `moveTo(<start of just-closed contour>)`
     *
     * Mirrors `SkPathBuilder.h:1011-1018` (`ensureMove`).
     */
    private fun ensureContour() {
        if (hasContour) return
        if (verbs.isNotEmpty() && verbs.last() == SkPath.StorageVerb.kClose) {
            // [contourX, contourY] still holds the start of the contour we just
            // closed — close() doesn't reset it, only flips hasContour.
            moveTo(contourX, contourY)
        } else {
            moveTo(0f, 0f)
        }
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
        // Conic decomposition of an elliptic arc — Skia parity.
        //
        // Each ≤ 90° sub-arc of an ellipse is exactly representable as a single
        // rational conic Bézier (`SkConic::BuildUnitArc`). For sub-angle θ on
        // the unit circle:
        //   - start = (cos t1, sin t1), end = (cos t2, sin t2)
        //   - control = (cos m / cos(θ/2), sin m / cos(θ/2))   m = (t1+t2)/2
        //   - weight  = cos(θ/2)
        // Scale x by rx, y by ry to lift to the source ellipse and translate
        // by (cx, cy). With nSegs = ceil(|sweep| / 90°), each segment's
        // half-angle is ≤ 45°, so cos(θ/2) ≥ √2/2 — always positive.
        val cx = (oval.left + oval.right) * 0.5f
        val cy = (oval.top + oval.bottom) * 0.5f
        val rx = (oval.right - oval.left) * 0.5f
        val ry = (oval.bottom - oval.top) * 0.5f
        val startRad = startAngleDeg.toDouble() * PI / 180.0
        val sweepRad = sweepAngleDeg.toDouble() * PI / 180.0
        val nSegs = max(1, ceil(abs(sweepRad) / (PI / 2.0)).toInt())
        val segAngle = sweepRad / nSegs
        val halfAngle = segAngle * 0.5
        val cosHalf = cos(halfAngle)
        val invCosHalf = 1.0 / cosHalf
        val weight = cosHalf.toFloat()

        val firstX = (cx + rx * cos(startRad)).toFloat()
        val firstY = (cy + ry * sin(startRad)).toFloat()
        if (forceMoveTo || !hasContour) {
            moveTo(firstX, firstY)
        } else if (abs(lastX - firstX) > ARC_JOIN_EPS || abs(lastY - firstY) > ARC_JOIN_EPS) {
            // Tolerance comparison instead of bit-exact: tangent arcTo and any
            // chained arcTo accumulate ~1 ULP of float error in the centre and
            // start-point reconstruction. A sub-pixel epsilon preserves the
            // visual no-op behaviour without false-positive lineTo verbs.
            lineTo(firstX, firstY)
        }

        var theta = startRad
        for (i in 0 until nSegs) {
            val t2 = theta + segAngle
            val mid = theta + halfAngle
            val ctrlX = (cx + rx * (cos(mid) * invCosHalf)).toFloat()
            val ctrlY = (cy + ry * (sin(mid) * invCosHalf)).toFloat()
            val endX = (cx + rx * cos(t2)).toFloat()
            val endY = (cy + ry * sin(t2)).toFloat()
            conicTo(ctrlX, ctrlY, endX, endY, weight)
            theta = t2
        }
    }

    private companion object {
        /** `(4/3) * (sqrt(2) - 1)` — Hugues' constant for 90° cubic-Bézier circle approximation. */
        const val OVAL_KAPPA: Float = 0.5522847498307933f
        /**
         * `√2/2` — conic weight for a 90° quarter-circle/ellipse.
         * Matches Skia's `SK_ScalarRoot2Over2` (`include/core/SkScalar.h:23`),
         * used by `SkPathRawShapes::Oval` / `RRect`.
         */
        const val OVAL_CONIC_WEIGHT: Float = 0.707106781f
        /** Sub-pixel tolerance for "current point already on the arc start" check in [emitArc]. */
        const val ARC_JOIN_EPS: Float = 1e-4f
    }
}
