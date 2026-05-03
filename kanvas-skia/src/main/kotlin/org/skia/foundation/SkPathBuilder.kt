package org.skia.foundation

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
     * `SkPathBuilder::arcTo(SkPoint p1, SkPoint p2, SkScalar radius)`.
     *
     * Given the current pen position `P0`, append a circular arc of
     * the given `radius` that is tangent to the line segment `P0→p1`
     * at one end and tangent to `p1→p2` at the other. The arc replaces
     * the sharp corner at `p1`. A straight `lineTo` is emitted from
     * the current point to the first tangent point if necessary.
     *
     * Degenerate cases:
     *  - No current contour → emits `moveTo(p1)` and returns.
     *  - `radius ≤ 0`, the three points are collinear, or any of the
     *    `P0→p1` / `p1→p2` segments has zero length → emits `lineTo(p1)`
     *    and returns. This matches Skia's behaviour.
     *
     * Math: with `θ` = angle at `p1` between `p1→P0` and `p1→p2`,
     *
     * ```
     * d  = radius / tan(θ/2)              (distance from p1 to tangent points)
     * T0 = p1 + d · û1                    (tangent on the P0→p1 line)
     * T1 = p1 + d · û2                    (tangent on the p1→p2 line)
     * C  = p1 + radius · (û1 + û2) / sin θ  (centre of the arc circle)
     * ```
     *
     * The shorter arc from `T0` to `T1` around `C` (always ≤ π) is the
     * tangent arc. We delegate to the existing oval `arcTo` to emit the
     * cubic-Bézier approximation.
     */
    public fun arcTo(
        x1: SkScalar, y1: SkScalar,
        x2: SkScalar, y2: SkScalar,
        radius: SkScalar,
    ): SkPathBuilder = apply {
        if (!hasContour) { moveTo(x1, y1); return@apply }
        if (radius <= 0f) { lineTo(x1, y1); return@apply }

        val p0x = lastX; val p0y = lastY
        val v1x = p0x - x1; val v1y = p0y - y1
        val v2x = x2 - x1;  val v2y = y2 - y1
        val len1 = sqrt((v1x * v1x + v1y * v1y).toDouble()).toFloat()
        val len2 = sqrt((v2x * v2x + v2y * v2y).toDouble()).toFloat()
        if (len1 < 1e-6f || len2 < 1e-6f) { lineTo(x1, y1); return@apply }

        val u1x = v1x / len1; val u1y = v1y / len1
        val u2x = v2x / len2; val u2y = v2y / len2
        val cosTheta = (u1x * u2x + u1y * u2y).coerceIn(-1f, 1f)
        // Collinear (or near-anti-parallel — tangent arc undefined).
        if (cosTheta > 0.9999f || cosTheta < -0.9999f) {
            lineTo(x1, y1)
            return@apply
        }

        // Half-angle identities, valid for cosθ in (-1, 1).
        val cosHalf = sqrt(((1.0 + cosTheta) * 0.5).coerceAtLeast(0.0)).toFloat()
        val sinHalf = sqrt(((1.0 - cosTheta) * 0.5).coerceAtLeast(0.0)).toFloat()
        val tanHalf = sinHalf / cosHalf
        val sinTheta = 2f * sinHalf * cosHalf

        val d = radius / tanHalf
        val t0x = x1 + d * u1x; val t0y = y1 + d * u1y
        val t1x = x1 + d * u2x; val t1y = y1 + d * u2y
        val cx = x1 + radius * (u1x + u2x) / sinTheta
        val cy = y1 + radius * (u1y + u2y) / sinTheta

        if (t0x != lastX || t0y != lastY) lineTo(t0x, t0y)

        val startRad = atan2((t0y - cy).toDouble(), (t0x - cx).toDouble())
        val endRad = atan2((t1y - cy).toDouble(), (t1x - cx).toDouble())
        var sweepRad = endRad - startRad
        // Normalise to (-π, π] — tangent arc is always the shorter arc.
        while (sweepRad > PI) sweepRad -= 2.0 * PI
        while (sweepRad < -PI) sweepRad += 2.0 * PI

        val rect = SkRect.MakeLTRB(cx - radius, cy - radius, cx + radius, cy + radius)
        arcTo(
            rect,
            (startRad * 180.0 / PI).toFloat(),
            (sweepRad * 180.0 / PI).toFloat(),
            forceMoveTo = false,
        )
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
        val k = OVAL_KAPPA
        val l = rect.left; val t = rect.top
        val r = rect.right; val b = rect.bottom

        if (dir == SkPathDirection.kCW) {
            moveTo(l + tl.fX, t)
            lineTo(r - tr.fX, t)
            // Top-right corner: (r - tr.fX, t) → (r, t + tr.fY).
            cubicTo(
                r - tr.fX * (1f - k), t,
                r, t + tr.fY * (1f - k),
                r, t + tr.fY,
            )
            lineTo(r, b - br.fY)
            // Bottom-right corner: (r, b - br.fY) → (r - br.fX, b).
            cubicTo(
                r, b - br.fY * (1f - k),
                r - br.fX * (1f - k), b,
                r - br.fX, b,
            )
            lineTo(l + bl.fX, b)
            // Bottom-left corner: (l + bl.fX, b) → (l, b - bl.fY).
            cubicTo(
                l + bl.fX * (1f - k), b,
                l, b - bl.fY * (1f - k),
                l, b - bl.fY,
            )
            lineTo(l, t + tl.fY)
            // Top-left corner: (l, t + tl.fY) → (l + tl.fX, t).
            cubicTo(
                l, t + tl.fY * (1f - k),
                l + tl.fX * (1f - k), t,
                l + tl.fX, t,
            )
        } else {
            moveTo(l + tl.fX, t)
            // Top-left corner reversed: (l + tl.fX, t) → (l, t + tl.fY).
            cubicTo(
                l + tl.fX * (1f - k), t,
                l, t + tl.fY * (1f - k),
                l, t + tl.fY,
            )
            lineTo(l, b - bl.fY)
            // Bottom-left corner reversed: (l, b - bl.fY) → (l + bl.fX, b).
            cubicTo(
                l, b - bl.fY * (1f - k),
                l + bl.fX * (1f - k), b,
                l + bl.fX, b,
            )
            lineTo(r - br.fX, b)
            // Bottom-right corner reversed: (r - br.fX, b) → (r, b - br.fY).
            cubicTo(
                r - br.fX * (1f - k), b,
                r, b - br.fY * (1f - k),
                r, b - br.fY,
            )
            lineTo(r, t + tr.fY)
            // Top-right corner reversed: (r, t + tr.fY) → (r - tr.fX, t).
            cubicTo(
                r, t + tr.fY * (1f - k),
                r - tr.fX * (1f - k), t,
                r - tr.fX, t,
            )
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
        } else if (abs(lastX - firstX) > ARC_JOIN_EPS || abs(lastY - firstY) > ARC_JOIN_EPS) {
            // Tolerance comparison instead of bit-exact: tangent arcTo and any
            // chained arcTo accumulate ~1 ULP of float error in the centre and
            // start-point reconstruction. A sub-pixel epsilon preserves the
            // visual no-op behaviour without false-positive lineTo verbs.
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
        /** Sub-pixel tolerance for "current point already on the arc start" check in [emitArc]. */
        const val ARC_JOIN_EPS: Float = 1e-4f
    }
}
