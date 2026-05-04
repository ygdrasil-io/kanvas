package org.skia.foundation

import org.skia.math.SkRect
import org.skia.math.SkScalar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
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
        // Mirrors SkPathBuilder.cpp:136-156 — if the previous verb is also a
        // move, replace its point in place. Each contour can carry at most
        // one move verb (the last one specified).
        if (verbs.isNotEmpty() && verbs.last() == SkPath.Verb.kMove) {
            val n = coords.size
            coords[n - 2] = x
            coords[n - 1] = y
        } else {
            verbs.add(SkPath.Verb.kMove); coords.add(x); coords.add(y)
        }
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
    ): SkPathBuilder = apply {
        val cx = (oval.left + oval.right) * 0.5f
        val cy = (oval.top + oval.bottom) * 0.5f
        val l = oval.left; val t = oval.top
        val r = oval.right; val b = oval.bottom
        val w = OVAL_CONIC_WEIGHT
        moveTo(r, cy)
        if (dir == SkPathDirection.kCW) {
            // right → bottom: control at (R, B), end at (cx, B).
            conicTo(r, b, cx, b, w)
            conicTo(l, b, l, cy, w)
            conicTo(l, t, cx, t, w)
            conicTo(r, t, r, cy, w)
        } else {
            // right → top: control at (R, T), end at (cx, T).
            conicTo(r, t, cx, t, w)
            conicTo(l, t, l, cy, w)
            conicTo(l, b, cx, b, w)
            conicTo(r, b, r, cy, w)
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
    ): SkPathBuilder = apply {
        when (rrect.getType()) {
            SkRRect.Type.kEmpty_Type -> return@apply
            SkRRect.Type.kRect_Type -> { addRect(rrect.rect(), dir); return@apply }
            SkRRect.Type.kOval_Type -> { addOval(rrect.rect(), dir); return@apply }
            SkRRect.Type.kSimple_Type,
            SkRRect.Type.kNinePatch_Type,
            SkRRect.Type.kComplex_Type -> emitRRectCorners(rrect, dir)
        }
    }

    private fun emitRRectCorners(rrect: SkRRect, dir: SkPathDirection) {
        val rect = rrect.rect()
        val tl = rrect.radii(SkRRect.Corner.kUpperLeft_Corner)
        val tr = rrect.radii(SkRRect.Corner.kUpperRight_Corner)
        val br = rrect.radii(SkRRect.Corner.kLowerRight_Corner)
        val bl = rrect.radii(SkRRect.Corner.kLowerLeft_Corner)
        val w = OVAL_CONIC_WEIGHT
        val l = rect.left; val t = rect.top
        val r = rect.right; val b = rect.bottom

        // Mirrors SkPathRawShapes::set_as_rrect / gRRectVerbs_LineStart with
        // start at the top-left corner's end-of-arc on the top edge: each
        // corner becomes a single conic with control at the bbox corner
        // and weight √2/2 (same as the oval).
        if (dir == SkPathDirection.kCW) {
            moveTo(l + tl.fX, t)
            lineTo(r - tr.fX, t)
            // Top-right corner: control (r, t), end (r, t + tr.fY).
            conicTo(r, t, r, t + tr.fY, w)
            lineTo(r, b - br.fY)
            // Bottom-right corner: control (r, b), end (r - br.fX, b).
            conicTo(r, b, r - br.fX, b, w)
            lineTo(l + bl.fX, b)
            // Bottom-left corner: control (l, b), end (l, b - bl.fY).
            conicTo(l, b, l, b - bl.fY, w)
            lineTo(l, t + tl.fY)
            // Top-left corner: control (l, t), end (l + tl.fX, t).
            conicTo(l, t, l + tl.fX, t, w)
        } else {
            moveTo(l + tl.fX, t)
            // Top-left corner reversed: control (l, t), end (l, t + tl.fY).
            conicTo(l, t, l, t + tl.fY, w)
            lineTo(l, b - bl.fY)
            // Bottom-left corner reversed: control (l, b), end (l + bl.fX, b).
            conicTo(l, b, l + bl.fX, b, w)
            lineTo(r - br.fX, b)
            // Bottom-right corner reversed: control (r, b), end (r, b - br.fY).
            conicTo(r, b, r, b - br.fY, w)
            lineTo(r, t + tr.fY)
            // Top-right corner reversed: control (r, t), end (r - tr.fX, t).
            conicTo(r, t, r - tr.fX, t, w)
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
     * an empty contour implicitly emit a move:
     *  - empty builder → `moveTo(0, 0)`
     *  - last verb is `kClose` → `moveTo(<start of just-closed contour>)`
     *
     * Mirrors `SkPathBuilder.h:1011-1018` (`ensureMove`).
     */
    private fun ensureContour() {
        if (hasContour) return
        if (verbs.isNotEmpty() && verbs.last() == SkPath.Verb.kClose) {
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
