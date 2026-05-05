package org.skia.foundation

import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkScalar
import org.skia.math.SkVector
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Immutable path. Built with [SkPathBuilder] and produced via
 * [SkPathBuilder.detach] or via the static factories below
 * (`Rect` / `Circle` / `Oval` / `RRect` / `Line` / `Polygon`), which
 * mirror Skia 4.x.
 *
 * Storage layout — mirrors upstream Skia, parallel arrays:
 *   - `verbs[i]` records the verb kind.
 *   - `coords` is a flat sequence of `(x, y)` pairs, consumed
 *     left-to-right as verbs are walked. The number of points each
 *     verb consumes is encoded in [Verb.pointCount].
 *   - `conicWeights[j]` carries the weight of the j-th `kConic`
 *     verb, in occurrence order.
 *
 * Walk pattern (used by `SkBitmapDevice.buildEdges`):
 * ```
 * var coordIdx = 0; var weightIdx = 0
 * for (verb in path.verbs) {
 *     when (verb) {
 *         kMove  -> { px = coords[coordIdx++]; py = coords[coordIdx++] }
 *         kLine  -> { ...; px = coords[coordIdx++]; py = coords[coordIdx++] }
 *         kQuad  -> { c1; end (4 floats consumed) }
 *         kConic -> { c1; end (4 floats); w = weights[weightIdx++] }
 *         kCubic -> { c1; c2; end (6 floats consumed) }
 *         kClose -> { /* 0 points */ }
 *     }
 * }
 * ```
 */
public class SkPath internal constructor(
    internal val verbs: Array<Verb>,
    internal val coords: FloatArray,
    internal val conicWeights: FloatArray,
    public val fillType: SkPathFillType,
) {
    public enum class Verb(public val pointCount: Int) {
        kMove(1),
        kLine(1),
        kQuad(2),    // control + end
        kConic(2),   // control + end (with weight in conicWeights)
        kCubic(3),   // 2 controls + end
        kClose(0),
    }

    /**
     * How [SkPathBuilder.addPath] joins the source path to the destination
     * builder. Mirrors `SkPath::AddPathMode` (`include/core/SkPath.h:617-628`).
     *
     * - [kAppend] : copy the source verb stream verbatim. Source contours
     *   stay separate from the destination's last contour.
     * - [kExtend] : extend the destination's last contour by replacing the
     *   source's first `kMove` with a `lineTo` to the (transformed) move
     *   point. Subsequent contours within the source are appended as-is.
     *   If the destination is empty, behaves like [kAppend]. If the
     *   destination's last verb is `kClose`, the implicit move emitted
     *   by `ensureContour` re-anchors the pen at the just-closed
     *   contour's start (see Phase 1.2) before the line is drawn.
     */
    public enum class AddPathMode { kAppend, kExtend }

    public fun isEmpty(): Boolean = verbs.isEmpty()

    /**
     * True if [fillType] describes the area *outside* the path (i.e. is
     * `kInverseWinding` or `kInverseEvenOdd`). Mirrors
     * `SkPath::isInverseFillType` (`include/core/SkPath.h:273`).
     */
    public fun isInverseFillType(): Boolean = fillType.isInverse()

    /**
     * Return a copy of this path with [newFillType] in place of [fillType];
     * verb stream and coords are shared (no allocation when the requested
     * fill type is already the current one). Mirrors `SkPath::makeFillType`
     * (`include/core/SkPath.h:266`).
     */
    public fun makeFillType(newFillType: SkPathFillType): SkPath =
        if (newFillType == fillType) this
        else SkPath(verbs, coords, conicWeights, newFillType)

    /**
     * Return a copy of this path with the inverse bit of [fillType]
     * toggled. Mirrors `SkPath::makeToggleInverseFillType`
     * (`include/core/SkPath.h:279`).
     */
    public fun makeToggleInverseFillType(): SkPath =
        SkPath(verbs, coords, conicWeights, fillType.toggleInverse())

    /**
     * True if the last verb is `kClose`. Mirrors `SkPath::isLastContourClosed`
     * (`src/core/SkPath.cpp:148-152`).
     */
    public fun isLastContourClosed(): Boolean =
        verbs.isNotEmpty() && verbs.last() == Verb.kClose

    /**
     * True if every coordinate stored in [coords] is finite (`!isNaN`,
     * `!isInfinite`). Mirrors `SkPath::isFinite`. An empty path returns true.
     */
    public fun isFinite(): Boolean {
        for (c in coords) if (!c.isFinite()) return false
        return true
    }

    /**
     * Number of points stored in the path's flat coord array
     * (= sum of new points emitted by each verb). Mirrors
     * `SkPath::countPoints` (`include/core/SkPath.h:440`).
     */
    public fun countPoints(): Int = coords.size / 2

    /**
     * True iff [other] shares this path's verb stream and conic-weight
     * sequence — the precondition for [makeInterpolate]. Two paths are
     * interpolatable when corresponding points can be lerped without
     * changing verb identity (a `kQuad` against a `kCubic` would
     * silently re-shape the curve, so we reject it). Mirrors
     * `SkPath::isInterpolatable` (`include/core/SkPath.h:210`).
     */
    public fun isInterpolatable(other: SkPath): Boolean {
        if (verbs.size != other.verbs.size) return false
        for (i in verbs.indices) if (verbs[i] != other.verbs[i]) return false
        if (conicWeights.size != other.conicWeights.size) return false
        for (i in conicWeights.indices) {
            if (conicWeights[i] != other.conicWeights[i]) return false
        }
        return true
    }

    /**
     * Returns a new path whose points are a weighted average of `this`
     * and [ending]:
     *
     * ```
     * out[i] = this[i] * weight + ending[i] * (1 - weight)
     * ```
     *
     * `weight = 1` reproduces `this`; `weight = 0` reproduces [ending];
     * values outside `[0, 1]` extrapolate. Verbs and conic weights are
     * inherited from `this`; the [fillType] is also kept. Returns
     * `null` (matching Skia's "empty SkPath" sentinel) when the two
     * paths aren't [isInterpolatable]. Mirrors `SkPath::makeInterpolate`
     * (`include/core/SkPath.h:232`).
     */
    public fun makeInterpolate(ending: SkPath, weight: SkScalar): SkPath? {
        if (!isInterpolatable(ending)) return null
        val n = coords.size
        if (n != ending.coords.size) return null   // defence against drift
        val out = FloatArray(n)
        val u = 1f - weight
        for (i in 0 until n) {
            out[i] = coords[i] * weight + ending.coords[i] * u
        }
        return SkPath(verbs, out, conicWeights, fillType)
    }

    /**
     * Kotlin-idiomatic alias for [makeInterpolate]. Mirrors Skia's
     * out-pointer overload `bool SkPath::interpolate(const SkPath&,
     * SkScalar, SkPath* out) const` (`include/core/SkPath.h:255`) —
     * here we surface the result as a nullable return.
     */
    public fun interpolate(ending: SkPath, weight: SkScalar): SkPath? =
        makeInterpolate(ending, weight)

    /**
     * Text dump of the path's verb stream. One verb per line, control
     * points / weights inlined. Mirrors `SkPath::dumpToString`
     * (`include/core/SkPath.h:1085`). Useful for diagnostics; the
     * format is not stable across Skia versions and shouldn't be
     * parsed.
     */
    public fun dumpToString(): String {
        val sb = StringBuilder()
        sb.append("path(fillType=").append(fillType.name).append(", verbs=").append(verbs.size).append(") {\n")
        var coordIdx = 0
        var weightIdx = 0
        for (verb in verbs) {
            sb.append("  ")
            when (verb) {
                Verb.kMove -> {
                    sb.append("moveTo(")
                    sb.append(coords[coordIdx++]).append(", ").append(coords[coordIdx++])
                    sb.append(")\n")
                }
                Verb.kLine -> {
                    sb.append("lineTo(")
                    sb.append(coords[coordIdx++]).append(", ").append(coords[coordIdx++])
                    sb.append(")\n")
                }
                Verb.kQuad -> {
                    sb.append("quadTo(")
                    sb.append(coords[coordIdx++]).append(", ").append(coords[coordIdx++]).append(", ")
                    sb.append(coords[coordIdx++]).append(", ").append(coords[coordIdx++])
                    sb.append(")\n")
                }
                Verb.kConic -> {
                    sb.append("conicTo(")
                    sb.append(coords[coordIdx++]).append(", ").append(coords[coordIdx++]).append(", ")
                    sb.append(coords[coordIdx++]).append(", ").append(coords[coordIdx++]).append(", w=")
                    sb.append(conicWeights[weightIdx++])
                    sb.append(")\n")
                }
                Verb.kCubic -> {
                    sb.append("cubicTo(")
                    sb.append(coords[coordIdx++]).append(", ").append(coords[coordIdx++]).append(", ")
                    sb.append(coords[coordIdx++]).append(", ").append(coords[coordIdx++]).append(", ")
                    sb.append(coords[coordIdx++]).append(", ").append(coords[coordIdx++])
                    sb.append(")\n")
                }
                Verb.kClose -> sb.append("close()\n")
            }
        }
        sb.append("}")
        return sb.toString()
    }

    /**
     * Print [dumpToString] to stderr. Mirrors `SkPath::dump`
     * (`include/core/SkPath.h:1071`).
     */
    public fun dump() {
        System.err.println(dumpToString())
    }

    /**
     * Number of verbs in the path. Mirrors `SkPath::countVerbs`
     * (`include/core/SkPath.h:441`).
     */
    public fun countVerbs(): Int = verbs.size

    /**
     * Bitmask of segment types present in the verb stream. Bits match
     * `SkPathSegmentMask` (`include/core/SkPathTypes.h:53-58`):
     * `1` = line, `2` = quad, `4` = conic, `8` = cubic. Move and close
     * never set bits.
     */
    public fun getSegmentMasks(): Int {
        var mask = 0
        for (v in verbs) {
            when (v) {
                Verb.kLine -> mask = mask or 1
                Verb.kQuad -> mask = mask or 2
                Verb.kConic -> mask = mask or 4
                Verb.kCubic -> mask = mask or 8
                Verb.kMove, Verb.kClose -> {}
            }
        }
        return mask
    }

    /**
     * Last point on the path, or `null` if the path is empty. Mirrors
     * `SkPath::getLastPt` (the `optional<SkPoint>` overload at
     * `include/core/SkPath.h:449`).
     */
    public fun getLastPt(): SkPoint? {
        if (coords.isEmpty()) return null
        val n = coords.size
        return SkPoint(coords[n - 2], coords[n - 1])
    }

    /**
     * If this path is exactly a single line — verb stream `kMove + kLine`,
     * with no close — returns the (start, end) point pair. Otherwise null.
     * Mirrors `SkPath::isLine` (`src/core/SkPath.cpp:154-167`).
     */
    public fun isLine(): Pair<SkPoint, SkPoint>? {
        if (verbs.size != 2 || verbs[1] != Verb.kLine) return null
        // verbs[0] is necessarily kMove (builder invariant).
        return SkPoint(coords[0], coords[1]) to SkPoint(coords[2], coords[3])
    }

    /**
     * If this path is the canonical axis-aligned rectangle contour
     * `kMove + 3×kLine + kClose` produced by [SkPathBuilder.addRect],
     * returns the bounds. Otherwise null.
     *
     * Conservative compared to upstream `SkPath::isRect` — Skia's
     * `SkPathPriv::IsRectContour` recognises a few extra shapes (closed
     * via final `lineTo`, leading/trailing zero-length moves, …) that we
     * don't bother with here. All paths produced by [SkPathBuilder.addRect]
     * are recognised.
     */
    public fun isRect(): SkRect? {
        if (verbs.size != 5) return null
        if (verbs[0] != Verb.kMove ||
            verbs[1] != Verb.kLine ||
            verbs[2] != Verb.kLine ||
            verbs[3] != Verb.kLine ||
            verbs[4] != Verb.kClose
        ) return null
        val x0 = coords[0]; val y0 = coords[1]
        val x1 = coords[2]; val y1 = coords[3]
        val x2 = coords[4]; val y2 = coords[5]
        val x3 = coords[6]; val y3 = coords[7]
        // Each consecutive segment must be axis-aligned (one of dx, dy = 0).
        // Walking the 4 edges (the kClose closes back to (x0, y0)) the points
        // must form an axis-aligned rectangle: opposite corners share an axis.
        val xs = floatArrayOf(x0, x1, x2, x3)
        val ys = floatArrayOf(y0, y1, y2, y3)
        var minX = xs[0]; var maxX = xs[0]
        var minY = ys[0]; var maxY = ys[0]
        for (i in 1..3) {
            if (xs[i] < minX) minX = xs[i]
            if (xs[i] > maxX) maxX = xs[i]
            if (ys[i] < minY) minY = ys[i]
            if (ys[i] > maxY) maxY = ys[i]
        }
        // Each xi ∈ {minX, maxX} and yi ∈ {minY, maxY}, and the 4 (x, y)
        // pairs must hit each rect corner exactly once.
        val seenCorners = BooleanArray(4)
        for (i in 0..3) {
            val cornerX = if (xs[i] == minX) 0 else if (xs[i] == maxX) 1 else return null
            val cornerY = if (ys[i] == minY) 0 else if (ys[i] == maxY) 1 else return null
            val idx = cornerY * 2 + cornerX
            if (seenCorners[idx]) return null
            seenCorners[idx] = true
        }
        // Edges must be axis-aligned (consecutive points share x or y).
        if (x0 != x1 && y0 != y1) return null
        if (x1 != x2 && y1 != y2) return null
        if (x2 != x3 && y2 != y3) return null
        if (x3 != x0 && y3 != y0) return null
        return SkRect.MakeLTRB(minX, minY, maxX, maxY)
    }

    /**
     * If this path is the canonical oval contour `kMove + 4×kConic + kClose`
     * produced by [SkPathBuilder.addOval] — all four conic weights equal
     * `√2/2` and the move/conic-end points sit on the four cardinal
     * positions of an axis-aligned ellipse — returns the bounding rect.
     * Otherwise null.
     *
     * Pattern-match approach (vs Skia's cached `fOvalInfo`): Phase 2 made
     * the verb stream canonical, so the recognition is reliable.
     */
    public fun isOval(): SkRect? {
        if (verbs.size != 6) return null
        if (verbs[0] != Verb.kMove ||
            verbs[1] != Verb.kConic ||
            verbs[2] != Verb.kConic ||
            verbs[3] != Verb.kConic ||
            verbs[4] != Verb.kConic ||
            verbs[5] != Verb.kClose
        ) return null
        if (conicWeights.size != 4) return null
        val w = SQRT2_OVER_2
        for (cw in conicWeights) if (abs(cw - w) > CONIC_WEIGHT_EPS) return null
        // Conic ends are the cardinals; the move start is also a cardinal.
        // Coords layout: move(2) + conic(2 ctrl + 2 end) × 4 = 18 floats.
        // Cardinal points indices: 0..1 (move), 4..5, 8..9, 12..13, 16..17.
        if (coords.size != 18) return null
        // Collect the four "end" points of conics + the move = 4 cardinals
        // (the last conic's end equals the move start, so 4 unique points).
        val pxs = floatArrayOf(coords[0], coords[4], coords[8], coords[12])
        val pys = floatArrayOf(coords[1], coords[5], coords[9], coords[13])
        var minX = pxs[0]; var maxX = pxs[0]
        var minY = pys[0]; var maxY = pys[0]
        for (i in 1..3) {
            if (pxs[i] < minX) minX = pxs[i]
            if (pxs[i] > maxX) maxX = pxs[i]
            if (pys[i] < minY) minY = pys[i]
            if (pys[i] > maxY) maxY = pys[i]
        }
        val cx = (minX + maxX) * 0.5f
        val cy = (minY + maxY) * 0.5f
        // The cardinals must be {(maxX, cy), (cx, minY), (minX, cy), (cx, maxY)}
        // in some order. Each pair (px, py) must equal a cardinal.
        for (i in 0..3) {
            val onCx = abs(pxs[i] - cx) <= CARDINAL_EPS
            val onCy = abs(pys[i] - cy) <= CARDINAL_EPS
            // Exactly one must be on-axis (cardinal positions are (cx, ±ry)
            // or (±rx, cy)).
            if (onCx == onCy) return null
        }
        // Last conic's end must equal the move start.
        if (abs(coords[16] - coords[0]) > CARDINAL_EPS ||
            abs(coords[17] - coords[1]) > CARDINAL_EPS
        ) return null
        return SkRect.MakeLTRB(minX, minY, maxX, maxY)
    }

    /**
     * If this path is a canonical rounded-rect contour produced by
     * [SkPathBuilder.addRRect] (in either of the two verb-stream variants
     * — `LineStart`: `kMove + (kLine + kConic) × 4 + kClose`, 10 verbs,
     * or `ConicStart`: `kMove + (kConic + kLine) × 3 + kConic + kClose`,
     * 9 verbs), returns the matching [SkRRect]. Otherwise null.
     *
     * The detector skips paths that came in via [SkPathBuilder.addRect]
     * or [SkPathBuilder.addOval] (those are recognised by [isRect] /
     * [isOval]); use the type-specific detectors when the broader query
     * matters.
     *
     * Both verb-stream variants are supported regardless of the
     * `(startIndex, dir)` combination that produced them — the detector
     * walks the stream, extracts conic control points (= bbox corners)
     * and cardinal endpoints, then derives per-corner radii from the
     * nearest cardinals on adjacent edges.
     */
    public fun isRRect(): SkRRect? {
        // 10 verbs = LineStart, 9 verbs = ConicStart.
        if (verbs.size != 10 && verbs.size != 9) return null
        if (verbs[0] != Verb.kMove || verbs.last() != Verb.kClose) return null
        var lineCount = 0; var conicCount = 0
        for (i in 1 until verbs.size - 1) {
            when (verbs[i]) {
                Verb.kLine -> lineCount++
                Verb.kConic -> conicCount++
                else -> return null
            }
        }
        if (conicCount != 4) return null
        if (verbs.size == 10 && lineCount != 4) return null
        if (verbs.size == 9 && lineCount != 3) return null
        if (conicWeights.size != 4) return null
        val w = SQRT2_OVER_2
        for (cw in conicWeights) if (abs(cw - w) > CONIC_WEIGHT_EPS) return null

        // Walk the verb stream collecting:
        //   - 4 conic control points (one per kConic), expected to coincide
        //     with the bbox corners,
        //   - all cardinal points (move + every line endpoint + every conic
        //     endpoint), expected to lie on the bbox edges.
        val controlX = FloatArray(4); val controlY = FloatArray(4)
        val cardX = FloatArray(9); val cardY = FloatArray(9)
        var ci = 0
        var ck = 0
        var coordIdx = 0
        cardX[ck] = coords[0]; cardY[ck] = coords[1]; ck++
        coordIdx = 2
        for (i in 1 until verbs.size - 1) {
            when (verbs[i]) {
                Verb.kLine -> {
                    cardX[ck] = coords[coordIdx]; cardY[ck] = coords[coordIdx + 1]; ck++
                    coordIdx += 2
                }
                Verb.kConic -> {
                    controlX[ci] = coords[coordIdx]
                    controlY[ci] = coords[coordIdx + 1]
                    ci++
                    coordIdx += 2
                    cardX[ck] = coords[coordIdx]; cardY[ck] = coords[coordIdx + 1]; ck++
                    coordIdx += 2
                }
                else -> return null
            }
        }
        // Bbox = bounding rect of the 4 control points.
        var l = controlX[0]; var r = controlX[0]
        var t = controlY[0]; var b = controlY[0]
        for (i in 1..3) {
            if (controlX[i] < l) l = controlX[i]
            if (controlX[i] > r) r = controlX[i]
            if (controlY[i] < t) t = controlY[i]
            if (controlY[i] > b) b = controlY[i]
        }
        if (l >= r || t >= b) return null
        // Each control must occupy a unique bbox corner.
        val seenCorner = BooleanArray(4)
        for (i in 0..3) {
            val xb = if (controlX[i] == l) 0 else if (controlX[i] == r) 1 else return null
            val yb = if (controlY[i] == t) 0 else if (controlY[i] == b) 1 else return null
            val idx = yb * 2 + xb
            if (seenCorner[idx]) return null
            seenCorner[idx] = true
        }
        // Per-corner radii: smallest distance from the corner to any cardinal
        // on the adjacent edge in the matching axis (x for the horizontal
        // edge, y for the vertical edge). For a rrect produced by addRRect,
        // each edge holds exactly one cardinal at distance = corner radius
        // (the corner's "after" cardinal on one edge, "before" cardinal on
        // the other).
        fun findRadii(cornerX: Float, cornerY: Float): SkVector? {
            var rx = Float.POSITIVE_INFINITY
            var ry = Float.POSITIVE_INFINITY
            for (i in 0 until ck) {
                val px = cardX[i]; val py = cardY[i]
                if (py == cornerY && px != cornerX) {
                    val dx = abs(px - cornerX)
                    if (dx < rx) rx = dx
                }
                if (px == cornerX && py != cornerY) {
                    val dy = abs(py - cornerY)
                    if (dy < ry) ry = dy
                }
            }
            if (!rx.isFinite() || !ry.isFinite()) return null
            return SkVector(rx, ry)
        }
        val tlR = findRadii(l, t) ?: return null
        val trR = findRadii(r, t) ?: return null
        val brR = findRadii(r, b) ?: return null
        val blR = findRadii(l, b) ?: return null
        return SkRRect.MakeRectRadii(
            SkRect.MakeLTRB(l, t, r, b),
            arrayOf(tlR, trR, brR, blR),
        )
    }

    /**
     * **Fast bounds** — axis-aligned bounding box of every control point
     * stored in [coords]. Mirrors Skia's `SkPath::getBounds()`.
     *
     * For paths made entirely of `kMove` and `kLine` verbs this is exact;
     * curves' control points may extend slightly beyond the actual curve,
     * so the box is always a *conservative* (i.e. ≥) bound. Tight curve
     * bounds (Skia's `computeTightBounds()`) requires solving Bézier
     * derivative roots and is deferred to a follow-up.
     *
     * Empty path → an empty rect at the origin.
     */
    public fun computeBounds(): SkRect {
        if (coords.isEmpty()) return SkRect.MakeLTRB(0f, 0f, 0f, 0f)
        var minX = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var i = 0
        while (i < coords.size) {
            val x = coords[i]; val y = coords[i + 1]
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
            i += 2
        }
        return SkRect.MakeLTRB(minX, minY, maxX, maxY)
    }

    /**
     * **Tight bounds** — true min/max of the actual path geometry,
     * including curve interiors. Mirrors `SkPath::computeTightBounds`
     * (`include/core/SkPath.h:542`,
     * `src/core/SkPathRef.cpp` `ComputePtBounds` impl).
     *
     * For each curve segment, internal extrema are found by solving
     * the parametric derivative `dB/dt = 0`:
     *  - `kQuad`: linear derivative → at most 1 root per axis
     *    (`t = (P0 − P1) / (P0 − 2 P1 + P2)`).
     *  - `kCubic`: quadratic derivative → at most 2 roots per axis,
     *    solved via the standard discriminant.
     *  - `kConic`: rational derivative — for the canonical quarter-arc
     *    conics emitted by `addOval` / `addRRect` / `arcTo` (control
     *    point at the bbox corner of the arc), the curve never extends
     *    past the (start, end, control) bbox, so the conservative
     *    control-point bound is also tight. For arbitrary conics we
     *    sample 16 uniformly-spaced points on the curve as a fallback —
     *    error is well below pixel-size for any reasonable weight.
     *
     * Empty path → `(0, 0, 0, 0)`. Unlike [computeBounds] this is **not**
     * cached.
     */
    public fun computeTightBounds(): SkRect {
        if (verbs.isEmpty()) return SkRect.MakeLTRB(0f, 0f, 0f, 0f)
        var minX = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        fun extend(x: Float, y: Float) {
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
        var px = 0f; var py = 0f
        var coordIdx = 0
        var weightIdx = 0
        for (verb in verbs) {
            when (verb) {
                Verb.kMove -> {
                    px = coords[coordIdx++]; py = coords[coordIdx++]
                    extend(px, py)
                }
                Verb.kLine -> {
                    val ex = coords[coordIdx++]; val ey = coords[coordIdx++]
                    extend(ex, ey)
                    px = ex; py = ey
                }
                Verb.kQuad -> {
                    val cx = coords[coordIdx++]; val cy = coords[coordIdx++]
                    val ex = coords[coordIdx++]; val ey = coords[coordIdx++]
                    extend(ex, ey)
                    quadExtremum(px, cx, ex)?.let { tX ->
                        extend(quadAt(px, cx, ex, tX), quadAt(py, cy, ey, tX))
                    }
                    quadExtremum(py, cy, ey)?.let { tY ->
                        extend(quadAt(px, cx, ex, tY), quadAt(py, cy, ey, tY))
                    }
                    px = ex; py = ey
                }
                Verb.kConic -> {
                    val cx = coords[coordIdx++]; val cy = coords[coordIdx++]
                    val ex = coords[coordIdx++]; val ey = coords[coordIdx++]
                    val w = conicWeights[weightIdx++]
                    extend(ex, ey)
                    // Sample 16 uniformly-spaced points along the conic.
                    // For the quarter-arc conics produced by addOval /
                    // addRRect / arcTo (control at bbox corner, weight √2/2),
                    // the curve stays within bbox(start, end, control), so
                    // the control-point bbox is already a tight bound. The
                    // sampled fallback handles arbitrary conics.
                    val n = 16
                    for (k in 1 until n) {
                        val t = k.toFloat() / n
                        val u = 1f - t
                        val numW = u * u + 2f * u * t * w + t * t
                        val vx = (u * u * px + 2f * u * t * w * cx + t * t * ex) / numW
                        val vy = (u * u * py + 2f * u * t * w * cy + t * t * ey) / numW
                        extend(vx, vy)
                    }
                    px = ex; py = ey
                }
                Verb.kCubic -> {
                    val c1x = coords[coordIdx++]; val c1y = coords[coordIdx++]
                    val c2x = coords[coordIdx++]; val c2y = coords[coordIdx++]
                    val ex = coords[coordIdx++]; val ey = coords[coordIdx++]
                    extend(ex, ey)
                    for (t in cubicExtrema(px, c1x, c2x, ex)) {
                        extend(cubicAt(px, c1x, c2x, ex, t), cubicAt(py, c1y, c2y, ey, t))
                    }
                    for (t in cubicExtrema(py, c1y, c2y, ey)) {
                        extend(cubicAt(px, c1x, c2x, ex, t), cubicAt(py, c1y, c2y, ey, t))
                    }
                    px = ex; py = ey
                }
                Verb.kClose -> {}
            }
        }
        return SkRect.MakeLTRB(minX, minY, maxX, maxY)
    }

    /**
     * Translated copy. Mirrors Skia's `SkPath::makeOffset(dx, dy)` —
     * verbs and conic weights are preserved untouched, only point
     * coordinates shift. Returns `this` when both deltas are zero
     * (no allocation).
     */
    public fun makeOffset(dx: SkScalar, dy: SkScalar): SkPath {
        if (dx == 0f && dy == 0f) return this
        val n = coords.size
        val out = FloatArray(n)
        var i = 0
        while (i < n) {
            out[i] = coords[i] + dx
            out[i + 1] = coords[i + 1] + dy
            i += 2
        }
        return SkPath(verbs, out, conicWeights, fillType)
    }

    /**
     * Apply [m] to every control point, returning a new [SkPath]. Verbs and
     * conic weights are preserved as-is — Bézier control points transform
     * naturally under affine maps. Mirrors Skia's `SkPath::transform(SkMatrix)`
     * for the affine case.
     *
     * Identity matrix → returns `this` (no allocation).
     */
    public fun makeTransform(m: SkMatrix): SkPath {
        if (m.isIdentity) return this
        val n = coords.size
        val out = FloatArray(n)
        val sx = m.sx; val kx = m.kx; val tx = m.tx
        val ky = m.ky; val sy = m.sy; val ty = m.ty
        var i = 0
        while (i < n) {
            val x = coords[i]; val y = coords[i + 1]
            out[i] = sx * x + kx * y + tx
            out[i + 1] = ky * x + sy * y + ty
            i += 2
        }
        return SkPath(verbs, out, conicWeights, fillType)
    }

    /**
     * Scale-only convenience overload of [makeTransform]. Mirrors
     * `SkPath::makeScale` (`include/core/SkPath.h:671`).
     */
    public fun makeScale(sx: SkScalar, sy: SkScalar): SkPath =
        makeTransform(SkMatrix.MakeScale(sx, sy))

    /**
     * Like [makeTransform], but returns `null` if the resulting path
     * carries any non-finite coord (`NaN`, `±∞`). Mirrors
     * `SkPath::tryMakeTransform` (`include/core/SkPath.h:638`).
     */
    public fun tryMakeTransform(m: SkMatrix): SkPath? {
        val out = makeTransform(m)
        return if (out.isFinite()) out else null
    }

    /**
     * Translate-only finite-checking variant. Mirrors
     * `SkPath::tryMakeOffset` (`include/core/SkPath.h:640`).
     */
    public fun tryMakeOffset(dx: SkScalar, dy: SkScalar): SkPath? =
        tryMakeTransform(SkMatrix.MakeTrans(dx, dy))

    /**
     * Scale-only finite-checking variant. Mirrors `SkPath::tryMakeScale`
     * (`include/core/SkPath.h:644`).
     */
    public fun tryMakeScale(sx: SkScalar, sy: SkScalar): SkPath? =
        tryMakeTransform(SkMatrix.MakeScale(sx, sy))

    public companion object {
        /** `√2/2` — canonical conic weight for a 90° quarter-circle/ellipse. */
        private const val SQRT2_OVER_2: Float = 0.707106781f
        /** Tolerance on conic-weight equality during oval / rrect detection. */
        private const val CONIC_WEIGHT_EPS: Float = 1e-4f
        /** Tolerance on cardinal-point coincidence during oval / rrect detection. */
        private const val CARDINAL_EPS: Float = 1e-4f
        /**
         * `SK_ScalarNearlyZero` from `include/core/SkScalar.h:25` —
         * `1 / 4096`. Used by the non-exact `Is*Degenerate` predicates.
         */
        private const val NEARLY_ZERO: Float = 1f / 4096f

        // -----------------------------------------------------------------
        // Bézier extremum / evaluation helpers (used by computeTightBounds).
        // -----------------------------------------------------------------

        /**
         * Solve `dB/dt = 0` for a 1-D quadratic Bézier on `[P0, P1, P2]`.
         * Returns the root in `(0, 1)` or `null`. Linear case (denominator
         * zero) and roots at the endpoints are dropped — they don't add
         * any extremum beyond what extending the endpoints already covers.
         */
        private fun quadExtremum(p0: Float, p1: Float, p2: Float): Float? {
            val denom = p0 - 2f * p1 + p2
            if (denom == 0f) return null
            val t = (p0 - p1) / denom
            return if (t > 0f && t < 1f) t else null
        }

        /** Evaluate a 1-D quadratic Bézier at `t`. */
        private fun quadAt(p0: Float, p1: Float, p2: Float, t: Float): Float {
            val u = 1f - t
            return u * u * p0 + 2f * u * t * p1 + t * t * p2
        }

        /**
         * Solve `dB/dt = 0` for a 1-D cubic Bézier. Up to 2 roots in
         * `(0, 1)`, returned as a fresh `FloatArray`. Derivation:
         *
         * ```
         * P'(t) = 3 [(1−t)² A + 2(1−t)t B + t² C]    A = P1−P0, B = P2−P1, C = P3−P2
         *       = 3 [(A − 2B + C) t² + (2B − 2A) t + A]
         * ```
         */
        private fun cubicExtrema(p0: Float, p1: Float, p2: Float, p3: Float): FloatArray {
            val a = -p0 + 3f * p1 - 3f * p2 + p3
            val b = 2f * p0 - 4f * p1 + 2f * p2
            val c = p1 - p0
            val out = FloatArray(2)
            var n = 0
            if (a == 0f) {
                if (b != 0f) {
                    val t = -c / b
                    if (t > 0f && t < 1f) out[n++] = t
                }
            } else {
                val disc = b * b - 4f * a * c
                if (disc >= 0f) {
                    val sd = sqrt(disc.toDouble()).toFloat()
                    val t1 = (-b - sd) / (2f * a)
                    val t2 = (-b + sd) / (2f * a)
                    if (t1 > 0f && t1 < 1f) out[n++] = t1
                    if (t2 > 0f && t2 < 1f) out[n++] = t2
                }
            }
            return if (n == out.size) out else out.copyOf(n)
        }

        /** Evaluate a 1-D cubic Bézier at `t`. */
        private fun cubicAt(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
            val u = 1f - t
            return u * u * u * p0 +
                3f * u * u * t * p1 +
                3f * u * t * t * p2 +
                t * t * t * p3
        }

        // -----------------------------------------------------------------
        // Static degeneracy predicates — mirror SkPath::IsLineDegenerate /
        // IsQuadDegenerate / IsCubicDegenerate (include/core/SkPath.h:381,
        // 394, 409, src/core/SkPath.cpp:128-146).
        // -----------------------------------------------------------------

        /**
         * `true` if the line `[p1, p2]` has effectively zero length.
         *
         * - `exact = true` : strict bit-equality of `p1` and `p2`.
         * - `exact = false`: each axis differs by less than
         *   `SK_ScalarNearlyZero = 1/4096`.
         */
        public fun IsLineDegenerate(p1: SkPoint, p2: SkPoint, exact: Boolean): Boolean =
            if (exact) p1.fX == p2.fX && p1.fY == p2.fY
            else nearlyEquals(p1, p2)

        /**
         * `true` if the three control points `[p1, p2, p3]` collapse to
         * a single position (zero-length quad).
         */
        public fun IsQuadDegenerate(p1: SkPoint, p2: SkPoint, p3: SkPoint, exact: Boolean): Boolean =
            if (exact) p1.fX == p2.fX && p1.fY == p2.fY && p2.fX == p3.fX && p2.fY == p3.fY
            else nearlyEquals(p1, p2) && nearlyEquals(p2, p3)

        /**
         * `true` if the four control points `[p1..p4]` collapse to a
         * single position (zero-length cubic).
         */
        public fun IsCubicDegenerate(
            p1: SkPoint, p2: SkPoint, p3: SkPoint, p4: SkPoint, exact: Boolean,
        ): Boolean = if (exact) {
            p1.fX == p2.fX && p1.fY == p2.fY &&
                p2.fX == p3.fX && p2.fY == p3.fY &&
                p3.fX == p4.fX && p3.fY == p4.fY
        } else {
            nearlyEquals(p1, p2) && nearlyEquals(p2, p3) && nearlyEquals(p3, p4)
        }

        private fun nearlyEquals(a: SkPoint, b: SkPoint): Boolean =
            abs(a.fX - b.fX) < NEARLY_ZERO && abs(a.fY - b.fY) < NEARLY_ZERO

        /** Closed rectangular contour. */
        public fun Rect(
            rect: SkRect,
            dir: SkPathDirection = SkPathDirection.kCW,
        ): SkPath = SkPathBuilder().addRect(rect, dir).detach()

        /**
         * Closed rectangular contour with explicit [fillType] and
         * [startIndex] (corner to begin at). Mirrors
         * `SkPath::Rect(SkRect, SkPathFillType, SkPathDirection, unsigned)`
         * (`include/core/SkPath.h:89-94`).
         */
        public fun Rect(
            rect: SkRect,
            fillType: SkPathFillType,
            dir: SkPathDirection = SkPathDirection.kCW,
            startIndex: Int = 0,
        ): SkPath = SkPathBuilder()
            .setFillType(fillType)
            .addRect(rect, dir, startIndex)
            .detach()

        /** Closed circular contour, axis-aligned. */
        public fun Circle(
            cx: SkScalar, cy: SkScalar, r: SkScalar,
            dir: SkPathDirection = SkPathDirection.kCW,
        ): SkPath = SkPathBuilder().addCircle(cx, cy, r, dir).detach()

        /** Closed elliptical contour, axis-aligned. */
        public fun Oval(
            rect: SkRect,
            dir: SkPathDirection = SkPathDirection.kCW,
        ): SkPath = SkPathBuilder().addOval(rect, dir).detach()

        /**
         * Closed elliptical contour starting at the cardinal selected by
         * [startIndex] (`0..3` mapping to top / right / bottom / left CW).
         * Mirrors `SkPath::Oval(SkRect, SkPathDirection, unsigned)`
         * (`include/core/SkPath.h:96`).
         */
        public fun Oval(
            rect: SkRect,
            dir: SkPathDirection,
            startIndex: Int,
        ): SkPath = SkPathBuilder().addOval(rect, dir, startIndex).detach()

        /**
         * Closed rounded-rectangle contour. Mirrors Skia's
         * `SkPath::RRect(const SkRRect&, SkPathDirection)`. The four
         * corners are emitted as quarter-conics with weight `√2/2`.
         *
         * If [rrect] has type [SkRRect.Type.kEmpty_Type], the returned
         * path is empty; if it's [SkRRect.Type.kRect_Type] or
         * [SkRRect.Type.kOval_Type], the path collapses to the appropriate
         * specialised contour.
         */
        public fun RRect(
            rrect: SkRRect,
            dir: SkPathDirection = SkPathDirection.kCW,
        ): SkPath = SkPathBuilder().addRRect(rrect, dir).detach()

        /**
         * Closed rounded-rect contour starting at cardinal [startIndex]
         * (`0..7` indexed CW from "top edge, just past TL corner").
         * Mirrors `SkPath::RRect(const SkRRect&, SkPathDirection, unsigned)`
         * (`include/core/SkPath.h:100`).
         */
        public fun RRect(
            rrect: SkRRect,
            dir: SkPathDirection,
            startIndex: Int,
        ): SkPath = SkPathBuilder().addRRect(rrect, dir, startIndex).detach()

        /** Single line segment from `a` to `b`, no close. */
        public fun Line(
            a: Pair<SkScalar, SkScalar>,
            b: Pair<SkScalar, SkScalar>,
        ): SkPath = SkPathBuilder()
            .moveTo(a.first, a.second)
            .lineTo(b.first, b.second)
            .detach()

        /**
         * Polygon contour: a single contour through `points`. If
         * `isClosed` is true an explicit `close` verb is emitted at the
         * end. Mirrors Skia's `SkPath::Polygon`.
         */
        public fun Polygon(
            points: Array<Pair<SkScalar, SkScalar>>,
            isClosed: Boolean,
            fillType: SkPathFillType = SkPathFillType.kWinding,
            @Suppress("UNUSED_PARAMETER") isVolatile: Boolean = false,
        ): SkPath = SkPathBuilder()
            .setFillType(fillType)
            .addPolygon(points, isClosed)
            .detach()
    }
}
