/*
 * Copyright 2026 The kanvas-skia authors.
 *
 * Mirrors free functions and helpers from Skia's
 * `src/pathops/SkPathOpsAsWinding.cpp` — the kEvenOdd → kWinding
 * conversion driver.
 *
 * Phase D1.2.h.6.3 — Multi-contour AsWinding **partial impl** :
 * port the contour bbox-tree builder (`Contour` + `contourBounds`
 * + `inParent`) and use it to detect flat-tree inputs (no contour
 * contains another).
 *
 * Phase D1.2.h.6.4 — Add `getDirection` (signed-area test) and a
 * **2-level-nested fast path**.
 *
 * Phase D1.2.h.6.5 — Full nested analysis : `containsEdge`,
 * `leftEdge`, `nextEdge`, `containerContains`,
 * `checkContainerChildren`, `markReverse`. Replaces the 2-level
 * fast path with the upstream's general bbox-tree-walking
 * algorithm.
 *
 * Phase D1.2.h.6.6 — Reversal emit : `reverseAddPath` (walk a
 * source path in reverse and append onto a builder),
 * `reverseMarkedContours` (split-and-emit per `Contour.reverse`
 * flag). **Closes the AsWinding chantier** — `AsWinding` is now
 * end-to-end functional for nested-contour inputs.
 */
package org.skia.pathops.internal


import org.skia.math.zero_or_one
import org.skia.foundation.SkPath
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * One contour of an `AsWinding` input — its bbox + the verb-stream
 * range that materialises it. Children are populated by [inParent]
 * when a contour's bbox is fully contained by another's.
 *
 * Mirrors `struct Contour`
 * (`src/pathops/SkPathOpsAsWinding.cpp:29`).
 */
internal class AsWindingContour(
    val bounds: SkRect,
    val verbStart: Int,
    val verbEnd: Int,
) {
    val children: MutableList<AsWindingContour> = mutableListOf()

    /**
     * Outside-point on this contour — the leftmost-X / topmost-Y
     * point reachable from the contour's edges. Set by
     * [nextEdge] in [Edge.kInitial] mode, then read by
     * [containerContains] when checking against a candidate parent.
     *
     * Mirrors `Contour::fMinXY` (`SkPathOpsAsWinding.cpp:44`).
     */
    var minXY: SkPoint = SkPoint(SK_ScalarMax, SK_ScalarMax)

    /**
     * Set true by [containerContains] when this contour's
     * `minXY` is enclosed by the candidate parent's edges
     * (`winding != 0`). Used by [markReverse] to decide which
     * level a grandchild belongs to.
     *
     * Mirrors `Contour::fContained`
     * (`SkPathOpsAsWinding.cpp:48`).
     */
    var contained: Boolean = false

    /**
     * Set true by [markReverse] when this contour shares its
     * parent's direction and therefore needs reversing on emit.
     * Mirrors `Contour::fReverse`
     * (`SkPathOpsAsWinding.cpp:49`).
     */
    var reverse: Boolean = false

    /**
     * Computed direction (kCCW / kNone / kCW) — set by
     * [markReverse]'s `getDirection` call. Mirrors
     * `Contour::fDirection` (`SkPathOpsAsWinding.cpp:47`).
     */
    var direction: AsWindingDirection = AsWindingDirection.kNone

    companion object {
        /** Skia's `SK_ScalarMax`. */
        internal const val SK_ScalarMax: Float = Float.MAX_VALUE
    }
}

/**
 * Walk the path's verb stream and emit one [AsWindingContour] per
 * contour (range bounded by `kMove`s). Mirrors
 * `OpAsWinding::contourBounds` (`SkPathOpsAsWinding.cpp:191`).
 *
 * Each contour's bounds is the union of its constituent verbs'
 * point bounds (start, control, end). Empty contours (a `kMove`
 * not followed by any drawing verbs) are dropped to match
 * upstream's `bounds.isEmpty()` guard.
 */
internal fun contourBounds(path: SkPath): List<AsWindingContour> {
    val result = mutableListOf<AsWindingContour>()
    var lastStart = 0
    var verbStart = 0
    var coordIdx = 0
    var bounds = SkRect.MakeEmpty()
    var hasBounds = false
    fun extend(x: Float, y: Float) {
        if (!hasBounds) {
            bounds = SkRect.MakeLTRB(x, y, x, y)
            hasBounds = true
        } else {
            val l = minOf(bounds.left, x)
            val t = minOf(bounds.top, y)
            val r = maxOf(bounds.right, x)
            val b = maxOf(bounds.bottom, y)
            bounds = SkRect.MakeLTRB(l, t, r, b)
        }
    }
    for (v in path.verbs) {
        when (v) {
            SkPath.Verb.kMove -> {
                if (hasBounds) {
                    result.add(AsWindingContour(bounds, lastStart, verbStart))
                    lastStart = verbStart
                }
                bounds = SkRect.MakeEmpty()
                hasBounds = false
                val x = path.coords[coordIdx]; val y = path.coords[coordIdx + 1]
                coordIdx += 2
                extend(x, y)
            }
            SkPath.Verb.kLine -> {
                val x = path.coords[coordIdx]; val y = path.coords[coordIdx + 1]
                coordIdx += 2; extend(x, y)
            }
            SkPath.Verb.kQuad, SkPath.Verb.kConic -> {
                repeat(2) {
                    extend(path.coords[coordIdx], path.coords[coordIdx + 1])
                    coordIdx += 2
                }
            }
            SkPath.Verb.kCubic -> {
                repeat(3) {
                    extend(path.coords[coordIdx], path.coords[coordIdx + 1])
                    coordIdx += 2
                }
            }
            SkPath.Verb.kClose -> { /* no points */ }
            SkPath.Verb.kDone -> error("kDone is iterator-only, never stored")
        }
        ++verbStart
    }
    if (hasBounds) {
        result.add(AsWindingContour(bounds, lastStart, verbStart))
    }
    return result
}

/**
 * Recursive bbox-tree builder. Insert [contour] into [parent]'s
 * child list at the deepest level where [contour]'s bbox fits ; if
 * any of [parent]'s existing children fit inside [contour]'s bbox,
 * promote them to be [contour]'s children.
 *
 * Mirrors `OpAsWinding::inParent`
 * (`src/pathops/SkPathOpsAsWinding.cpp:317`).
 */
internal fun inParent(contour: AsWindingContour, parent: AsWindingContour) {
    for (test in parent.children) {
        if (test.bounds.contains(contour.bounds)) {
            inParent(contour, test)
            return
        }
    }
    val iter = parent.children.iterator()
    while (iter.hasNext()) {
        val child = iter.next()
        if (contour.bounds.contains(child.bounds)) {
            contour.children.add(child)
            iter.remove()
        }
    }
    parent.children.add(contour)
}

/**
 * True if [root]'s top-level children form a "flat" tree —
 * i.e. no contour is contained inside another. For this case
 * even-odd vs winding fill is moot : both rules paint the same
 * area, so a fillType swap suffices.
 *
 * Mirrors the early-out `if (std::all_of(...) → empty children)`
 * in `AsWinding` (`SkPathOpsAsWinding.cpp:438`).
 */
internal fun isFlatTree(root: AsWindingContour): Boolean =
    root.children.all { it.children.isEmpty() }

/**
 * Contour-direction enum used during reverse-marker analysis.
 * Mirrors `Contour::Direction`
 * (`src/pathops/SkPathOpsAsWinding.cpp:30`).
 *
 * `kCCW` = `-1`, `kNone` = `0`, `kCW` = `+1` — preserves the
 * upstream sign-arithmetic convention.
 */
internal enum class AsWindingDirection(val sign: Int) { kCCW(-1), kNone(0), kCW(1) }

private fun toDirection(dy: Float): AsWindingDirection = when {
    dy > 0 -> AsWindingDirection.kCCW
    dy < 0 -> AsWindingDirection.kCW
    else -> AsWindingDirection.kNone
}

/**
 * Compute [contour]'s direction (CW / CCW) by summing the signed
 * area of its constituent line / quad / conic / cubic verbs.
 * Mirrors `OpAsWinding::getDirection`
 * (`src/pathops/SkPathOpsAsWinding.cpp:217`).
 *
 * Strategy : per verb, accumulate `(p0.y - pN.y) * (p0.x + pN.x)`
 * (the shoelace formula on endpoints). Negative total → CCW,
 * positive → CW.
 *
 * Curve verbs collapse to their endpoints — an inexact
 * approximation that nonetheless agrees with upstream because the
 * direction sign is invariant under polygon-vs-curve substitution
 * for non-self-intersecting paths.
 */
internal fun getDirection(path: SkPath, contour: AsWindingContour): AsWindingDirection {
    var verbCount = -1
    var coordIdx = 0
    var penX = 0f; var penY = 0f
    var totalSignedArea = 0f
    for (v in path.verbs) {
        ++verbCount
        // Advance the pen for each verb, regardless of whether it
        // falls inside the contour range we're measuring.
        if (verbCount < contour.verbStart || verbCount >= contour.verbEnd) {
            // Still need to advance the coord cursor.
            when (v) {
                SkPath.Verb.kMove -> {
                    penX = path.coords[coordIdx]; penY = path.coords[coordIdx + 1]
                    coordIdx += 2
                }
                SkPath.Verb.kLine -> {
                    penX = path.coords[coordIdx]; penY = path.coords[coordIdx + 1]
                    coordIdx += 2
                }
                SkPath.Verb.kQuad, SkPath.Verb.kConic -> {
                    coordIdx += 2 // control
                    penX = path.coords[coordIdx]; penY = path.coords[coordIdx + 1]
                    coordIdx += 2
                }
                SkPath.Verb.kCubic -> {
                    coordIdx += 4 // 2 controls
                    penX = path.coords[coordIdx]; penY = path.coords[coordIdx + 1]
                    coordIdx += 2
                }
                SkPath.Verb.kClose -> { /* no points */ }
                SkPath.Verb.kDone -> error("kDone is iterator-only, never stored")
            }
            continue
        }
        when (v) {
            SkPath.Verb.kMove -> {
                penX = path.coords[coordIdx]; penY = path.coords[coordIdx + 1]
                coordIdx += 2
            }
            SkPath.Verb.kLine -> {
                val ex = path.coords[coordIdx]; val ey = path.coords[coordIdx + 1]
                coordIdx += 2
                totalSignedArea += (penY - ey) * (penX + ex)
                penX = ex; penY = ey
            }
            SkPath.Verb.kQuad, SkPath.Verb.kConic -> {
                coordIdx += 2 // skip control
                val ex = path.coords[coordIdx]; val ey = path.coords[coordIdx + 1]
                coordIdx += 2
                totalSignedArea += (penY - ey) * (penX + ex)
                penX = ex; penY = ey
            }
            SkPath.Verb.kCubic -> {
                coordIdx += 4 // skip 2 controls
                val ex = path.coords[coordIdx]; val ey = path.coords[coordIdx + 1]
                coordIdx += 2
                totalSignedArea += (penY - ey) * (penX + ex)
                penX = ex; penY = ey
            }
            SkPath.Verb.kClose -> { /* no points */ }
            SkPath.Verb.kDone -> error("kDone is iterator-only, never stored")
        }
    }
    return if (totalSignedArea < 0) AsWindingDirection.kCCW else AsWindingDirection.kCW
}

/**
 * 2-level-nested AsWinding analysis : check that every (parent,
 * child) pair has alternating directions (one CW, one CCW). When
 * all pairs alternate, no reversal is needed and the input is
 * already a well-formed winding-equivalent path — caller can
 * `makeFillType`.
 *
 * Returns false (reversal needed → caller bail-outs to null) when
 * any pair shares direction, or when the tree is deeper than 2
 * levels (caller falls through to null until h.6.5).
 */
/**
 * One verb of a path-iteration walk : the verb itself, the pts
 * array (with pts[0] = pen position, pts[1..N] = the verb's
 * stored coords), and the conic weight (1.0 for non-conic verbs).
 */
internal data class AsWindingVerbRec(
    val verb: SkPath.Verb,
    val pts: Array<SkPoint>,
    val weight: Float,
    val verbIndex: Int,
)

/**
 * Walk [path]'s verb stream and yield one [AsWindingVerbRec] per
 * verb (including kMove and kClose). For curve verbs, `pts[0]` is
 * the pen position carried over from the previous verb's
 * endpoint.
 *
 * Used by [getDirection] (re-implemented inline for now), and by
 * [nextEdge] / [contains_edge] etc. in the full nested AsWinding
 * machinery.
 */
internal inline fun forEachVerb(
    path: SkPath,
    body: (AsWindingVerbRec) -> Unit,
) {
    var penX = 0f; var penY = 0f
    var coordIdx = 0
    var conicIdx = 0
    var verbIndex = -1
    for (v in path.verbs) {
        ++verbIndex
        when (v) {
            SkPath.Verb.kMove -> {
                val x = path.coords[coordIdx]; val y = path.coords[coordIdx + 1]
                coordIdx += 2
                body(AsWindingVerbRec(v, arrayOf(SkPoint(x, y)), 1f, verbIndex))
                penX = x; penY = y
            }
            SkPath.Verb.kLine -> {
                val x = path.coords[coordIdx]; val y = path.coords[coordIdx + 1]
                coordIdx += 2
                body(AsWindingVerbRec(v,
                    arrayOf(SkPoint(penX, penY), SkPoint(x, y)),
                    1f, verbIndex))
                penX = x; penY = y
            }
            SkPath.Verb.kQuad -> {
                val cx = path.coords[coordIdx]; val cy = path.coords[coordIdx + 1]
                val ex = path.coords[coordIdx + 2]; val ey = path.coords[coordIdx + 3]
                coordIdx += 4
                body(AsWindingVerbRec(v,
                    arrayOf(SkPoint(penX, penY), SkPoint(cx, cy), SkPoint(ex, ey)),
                    1f, verbIndex))
                penX = ex; penY = ey
            }
            SkPath.Verb.kConic -> {
                val cx = path.coords[coordIdx]; val cy = path.coords[coordIdx + 1]
                val ex = path.coords[coordIdx + 2]; val ey = path.coords[coordIdx + 3]
                coordIdx += 4
                val w = path.conicWeights[conicIdx++]
                body(AsWindingVerbRec(v,
                    arrayOf(SkPoint(penX, penY), SkPoint(cx, cy), SkPoint(ex, ey)),
                    w, verbIndex))
                penX = ex; penY = ey
            }
            SkPath.Verb.kCubic -> {
                val c1x = path.coords[coordIdx]; val c1y = path.coords[coordIdx + 1]
                val c2x = path.coords[coordIdx + 2]; val c2y = path.coords[coordIdx + 3]
                val ex = path.coords[coordIdx + 4]; val ey = path.coords[coordIdx + 5]
                coordIdx += 6
                body(AsWindingVerbRec(v,
                    arrayOf(SkPoint(penX, penY), SkPoint(c1x, c1y),
                            SkPoint(c2x, c2y), SkPoint(ex, ey)),
                    1f, verbIndex))
                penX = ex; penY = ey
            }
            SkPath.Verb.kClose -> {
                body(AsWindingVerbRec(v, arrayOf(SkPoint(penX, penY)), 1f, verbIndex))
            }
            SkPath.Verb.kDone -> error("kDone is iterator-only, never stored")
        }
    }
}

/**
 * "Verb point count" : number of new points (not counting pts[0])
 * a curve verb adds. Mirrors `VerbPtCount`
 * (`SkPathOpsAsWinding.cpp:52`) — line=1, quad=2, conic=2, cubic=3.
 */
private fun verbPtCount(verb: SkPath.Verb): Int = when (verb) {
    SkPath.Verb.kLine -> 1
    SkPath.Verb.kQuad, SkPath.Verb.kConic -> 2
    SkPath.Verb.kCubic -> 3
    else -> 0
}

/**
 * Find the leftmost-X (and most-toppedmost-Y on tie) point of a
 * curve verb. For curves, accounts for X-axis extrema via
 * [SkDQuad.FindExtrema] / [SkDConic.FindExtrema] /
 * [SkDCubic.FindExtrema] when the curve isn't monotonic in X.
 *
 * Mirrors `left_edge` (`SkPathOpsAsWinding.cpp:127`).
 */
internal fun leftEdge(rec: AsWindingVerbRec): SkPoint {
    val pts = rec.pts
    val verb = rec.verb
    return when (verb) {
        SkPath.Verb.kLine -> if (pts[0].fX < pts[1].fX) pts[0] else pts[1]
        SkPath.Verb.kQuad -> {
            val q = SkDQuad().apply { set(pts[0], pts[1], pts[2]) }
            if (q.monotonicInX()) {
                if (pts[0].fX < pts[2].fX) pts[0] else pts[2]
            } else {
                val tValues = DoubleArray(1)
                val rootCount = SkDQuad.FindExtrema(
                    doubleArrayOf(q[0].x, q[1].x, q[2].x), tValues)
                if (rootCount > 0) q.ptAtT(tValues[0]).asSkPoint()
                else if (pts[0].fX < pts[2].fX) pts[0] else pts[2]
            }
        }
        SkPath.Verb.kConic -> {
            val c = SkDConic().apply { set(pts[0], pts[1], pts[2], rec.weight) }
            if (c.monotonicInX()) {
                if (pts[0].fX < pts[2].fX) pts[0] else pts[2]
            } else {
                val tValues = DoubleArray(1)
                val rootCount = SkDConic.FindExtrema(
                    doubleArrayOf(c.pts[0].x, c.pts[1].x, c.pts[2].x),
                    rec.weight, tValues)
                if (rootCount > 0) c.ptAtT(tValues[0]).asSkPoint()
                else if (pts[0].fX < pts[2].fX) pts[0] else pts[2]
            }
        }
        SkPath.Verb.kCubic -> {
            val cu = SkDCubic().apply { set(pts[0], pts[1], pts[2], pts[3]) }
            if (!cu.monotonicInX()) {
                val tValues = DoubleArray(2)
                val rootCount = SkDCubic.FindExtrema(
                    doubleArrayOf(cu.pts[0].x, cu.pts[1].x, cu.pts[2].x, cu.pts[3].x),
                    tValues)
                if (rootCount > 0) {
                    var best = cu.ptAtT(tValues[0]).asSkPoint()
                    for (idx in 1 until rootCount) {
                        val cand = cu.ptAtT(tValues[idx]).asSkPoint()
                        if (cand.fX < best.fX) best = cand
                    }
                    return best
                }
            }
            if (pts[0].fX < pts[3].fX) pts[0] else pts[3]
        }
        else -> error("leftEdge unexpected verb : $verb")
    }
}

/**
 * Count signed crossings of the horizontal ray from [edge] going
 * left, hitting [rec]'s curve. Returns the per-crossing direction
 * sum (`+1` for kCW slope at hit, `-1` for kCCW, `0` for
 * horizontal).
 *
 * Mirrors `contains_edge` (`SkPathOpsAsWinding.cpp:71`).
 */
internal fun containsEdge(rec: AsWindingVerbRec, edge: SkPoint): Int {
    val pts = rec.pts
    val verb = rec.verb
    val n = verbPtCount(verb)
    // Curve bbox.
    var minX = pts[0].fX; var maxX = pts[0].fX
    var minY = pts[0].fY; var maxY = pts[0].fY
    for (i in 1..n) {
        if (pts[i].fX < minX) minX = pts[i].fX
        if (pts[i].fX > maxX) maxX = pts[i].fX
        if (pts[i].fY < minY) minY = pts[i].fY
        if (pts[i].fY > maxY) maxY = pts[i].fY
    }
    if (minY > edge.fY) return 0
    if (maxY <= edge.fY) return 0 // edge at line end → avoid double-count
    if (minX >= edge.fX) return 0
    // Horizontal intercept at y = edge.fY. Upstream uses
    // `CurveIntercept[verb * 2]` (no `+ xy_index(dir)` term) — i.e.
    // the `_h` variant. We pass [SkOpRayDir.kLeft] (xy_index = 0)
    // to select that branch in our [CurveIntercept] dispatch.
    val tVals = DoubleArray(3)
    val tCount = CurveIntercept(verbToSegVerb(verb), SkOpRayDir.kLeft, pts, rec.weight,
        edge.fY, tVals)
    var count = tCount
    // Drop intersections to the right of edge.
    var index = 0
    while (index < count) {
        val xAtT = curvePointXAtT(rec, tVals[index])
        if (xAtT < edge.fX) { ++index; continue }
        if (xAtT > edge.fX) {
            tVals[index] = tVals[--count]
            continue
        }
        // Equality — drop unless both pts ends are left of edge.
        if (pts[0].fX < edge.fX && pts[n].fX < edge.fX) {
            ++index
            continue
        }
        tVals[index] = tVals[--count]
    }
    // Use first-derivative sign at each surviving t to direction.
    val directions = arrayOfNulls<AsWindingDirection>(3)
    for (i in 0 until count) {
        directions[i] = toDirection(curveSlopeYAtT(rec, tVals[i]).toFloat())
    }
    var winding = 0
    for (i in 0 until count) {
        // Skip intersections that end at edge and go up.
        if (zero_or_one(tVals[i]) && directions[i] != AsWindingDirection.kCCW) continue
        winding += directions[i]?.sign ?: 0
    }
    return winding
}

/** Map a path verb to the equivalent segment verb for [CurveIntercept]. */
private fun verbToSegVerb(v: SkPath.Verb): SkOpSegment.SegVerb = when (v) {
    SkPath.Verb.kLine -> SkOpSegment.SegVerb.kLine
    SkPath.Verb.kQuad -> SkOpSegment.SegVerb.kQuad
    SkPath.Verb.kConic -> SkOpSegment.SegVerb.kConic
    SkPath.Verb.kCubic -> SkOpSegment.SegVerb.kCubic
    else -> SkOpSegment.SegVerb.kUnset
}

/** Curve point's X at parameter [t]. */
private fun curvePointXAtT(rec: AsWindingVerbRec, t: Double): Float {
    val pts = rec.pts
    return when (rec.verb) {
        SkPath.Verb.kLine -> {
            val u = 1.0 - t
            (u * pts[0].fX + t * pts[1].fX).toFloat()
        }
        SkPath.Verb.kQuad ->
            SkDQuad().apply { set(pts[0], pts[1], pts[2]) }.ptAtT(t).x.toFloat()
        SkPath.Verb.kConic ->
            SkDConic().apply { set(pts[0], pts[1], pts[2], rec.weight) }.ptAtT(t).x.toFloat()
        SkPath.Verb.kCubic ->
            SkDCubic().apply { set(pts[0], pts[1], pts[2], pts[3]) }.ptAtT(t).x.toFloat()
        else -> 0f
    }
}

/** Curve slope's Y at parameter [t]. */
private fun curveSlopeYAtT(rec: AsWindingVerbRec, t: Double): Double {
    val pts = rec.pts
    return when (rec.verb) {
        SkPath.Verb.kLine -> (pts[1].fY - pts[0].fY).toDouble()
        SkPath.Verb.kQuad ->
            SkDQuad().apply { set(pts[0], pts[1], pts[2]) }.dxdyAtT(t).y
        SkPath.Verb.kConic ->
            SkDConic().apply { set(pts[0], pts[1], pts[2], rec.weight) }.dxdyAtT(t).y
        SkPath.Verb.kCubic ->
            SkDCubic().apply { set(pts[0], pts[1], pts[2], pts[3]) }.dxdyAtT(t).y
        else -> 0.0
    }
}

/**
 * Mode for [nextEdge] : either find the leftmost edge (kInitial)
 * or sum [containsEdge] across all edges (kCompare). Mirrors
 * `OpAsWinding::Edge` (`SkPathOpsAsWinding.cpp:182`).
 */
internal enum class AsWindingEdge { kInitial, kCompare }

/**
 * Walk [contour]'s verb-stream range. Skips horizontal edges
 * (which carry zero winding contribution).
 *
 * - In [kInitial][AsWindingEdge.kInitial] mode, scan every edge's
 *   leftmost-X point and update [contour].minXY to the smallest
 *   one (lex-by-X-then-Y).
 * - In [kCompare][AsWindingEdge.kCompare] mode, sum
 *   [containsEdge] for each edge against [contour].minXY,
 *   returning the total signed winding.
 *
 * Mirrors `OpAsWinding::nextEdge`
 * (`SkPathOpsAsWinding.cpp:253`).
 */
internal fun nextEdge(
    path: SkPath,
    contour: AsWindingContour,
    mode: AsWindingEdge,
): Int {
    var winding = 0
    forEachVerb(path) { rec ->
        if (rec.verbIndex < contour.verbStart) return@forEachVerb
        if (rec.verbIndex >= contour.verbEnd) return@forEachVerb
        val verb = rec.verb
        if (verb != SkPath.Verb.kLine && verb != SkPath.Verb.kQuad &&
            verb != SkPath.Verb.kConic && verb != SkPath.Verb.kCubic) {
            return@forEachVerb
        }
        // Horizontal edge filter.
        var horizontal = true
        for (idx in 1..verbPtCount(verb)) {
            if (rec.pts[0].fY != rec.pts[idx].fY) { horizontal = false; break }
        }
        if (horizontal) return@forEachVerb
        if (mode == AsWindingEdge.kCompare) {
            winding += containsEdge(rec, contour.minXY)
            return@forEachVerb
        }
        // kInitial.
        val minXY = leftEdge(rec)
        if (minXY.fX > contour.minXY.fX) return@forEachVerb
        if (minXY.fX == contour.minXY.fX && minXY.fY != contour.minXY.fY) return@forEachVerb
        contour.minXY = minXY
    }
    return winding
}

/**
 * Test whether [contour] contains [test]. First populate
 * [test].minXY via `nextEdge(kInitial)` if not already set, then
 * run [contour]'s edges through `nextEdge(kCompare)` against
 * [test].minXY. Records the result in [test].contained.
 *
 * Returns true on success ; false when the winding sum falls
 * outside the expected `[-1, 1]` range (degenerate input).
 *
 * Mirrors `OpAsWinding::containerContains`
 * (`SkPathOpsAsWinding.cpp:300`).
 */
internal fun containerContains(
    path: SkPath,
    contour: AsWindingContour,
    test: AsWindingContour,
): Boolean {
    if (test.minXY.fX == AsWindingContour.SK_ScalarMax) {
        nextEdge(path, test, AsWindingEdge.kInitial)
    }
    contour.minXY = test.minXY
    val winding = nextEdge(path, contour, AsWindingEdge.kCompare)
    test.contained = winding != 0
    return winding in -1..1
}

/**
 * Recursive containment-check : depth-first walk the bbox tree
 * verifying every (parent, child) pair via [containerContains].
 *
 * Mirrors `OpAsWinding::checkContainerChildren`
 * (`SkPathOpsAsWinding.cpp:337`).
 */
internal fun checkContainerChildren(
    path: SkPath,
    parent: AsWindingContour?,
    child: AsWindingContour,
): Boolean {
    for (grandChild in child.children) {
        if (!checkContainerChildren(path, child, grandChild)) return false
    }
    if (parent != null) {
        if (!containerContains(path, parent, child)) return false
    }
    return true
}

/**
 * Walk the bbox tree assigning per-contour direction + reverse
 * flags. Returns true if any contour was marked for reversal.
 *
 * Mirrors `OpAsWinding::markReverse`
 * (`SkPathOpsAsWinding.cpp:351`).
 */
internal fun markReverse(
    path: SkPath,
    parent: AsWindingContour?,
    child: AsWindingContour,
): Boolean {
    var reversed = false
    for (grandChild in child.children) {
        val effectiveParent = if (grandChild.contained) child else parent
        reversed = markReverse(path, effectiveParent, grandChild) || reversed
    }
    child.direction = getDirection(path, child)
    if (parent != null && parent.direction == child.direction) {
        child.reverse = true
        // Negate direction (kCCW.sign = -1 → kCW, kCW.sign = +1 → kCCW).
        child.direction = when (child.direction) {
            AsWindingDirection.kCCW -> AsWindingDirection.kCW
            AsWindingDirection.kCW -> AsWindingDirection.kCCW
            AsWindingDirection.kNone -> AsWindingDirection.kNone
        }
        return true
    }
    return reversed
}

internal fun no2LevelReverseNeeded(
    path: SkPath,
    sortedRoot: AsWindingContour,
): Boolean {
    for (parent in sortedRoot.children) {
        if (parent.children.isEmpty()) continue
        val parentDir = getDirection(path, parent)
        for (child in parent.children) {
            if (child.children.isNotEmpty()) return false // 3+ levels deep
            val childDir = getDirection(path, child)
            if (parentDir == childDir) return false // same direction → reverse needed
        }
    }
    return true
}

// ─── Reversal emit (D1.2.h.6.6) ──────────────────────────────────

/**
 * Append [src] reversed onto [builder]. Walks `src`'s verb stream
 * back-to-front, advancing the points cursor backwards through
 * `src.coords` and re-emitting each verb on `builder` with its
 * direction flipped (kMove ↔ kClose, control points reversed).
 *
 * Mirrors `SkPathBuilder::privateReverseAddPath`
 * (`src/core/SkPathBuilder.cpp:896`) — top-level helper here
 * since our [SkPathBuilder] doesn't expose a native reverse-add.
 */
public fun reverseAddPath(builder: org.skia.foundation.SkPathBuilder, src: SkPath) {
    val verbs = src.verbs
    if (verbs.isEmpty()) return
    // Cursor : points to the *index past* the next coord pair to read
    // (i.e. start at end, walk down).
    var ptCursor = src.coords.size / 2 // 1 = last point ; 0 = before first
    var conicCursor = src.conicWeights.size
    var needMove = true
    var needClose = false
    var i = verbs.size - 1
    while (i >= 0) {
        val v = verbs[i]
        val n = ptsInVerb(v)
        if (needMove) {
            // Read the last point as the next moveTo target.
            ptCursor -= 1
            val mx = src.coords[ptCursor * 2]; val my = src.coords[ptCursor * 2 + 1]
            builder.moveTo(mx, my)
            needMove = false
        }
        ptCursor -= n
        when (v) {
            SkPath.Verb.kMove -> {
                if (needClose) {
                    builder.close()
                    needClose = false
                }
                needMove = true
                ptCursor += 1 // restore so next iter's "needMove" reads it
            }
            SkPath.Verb.kLine -> {
                val x = src.coords[ptCursor * 2]; val y = src.coords[ptCursor * 2 + 1]
                builder.lineTo(x, y)
            }
            SkPath.Verb.kQuad -> {
                // Original verb : pts[0] = pen, pts[1] = control, pts[2] = end.
                // Reversed : start at original end, control = original control,
                // end = original pen. For builder.quadTo(c, e), c = pts[1]
                // (original control), e = pts[0] (original pen).
                val c1x = src.coords[(ptCursor + 1) * 2]
                val c1y = src.coords[(ptCursor + 1) * 2 + 1]
                val ex = src.coords[ptCursor * 2]; val ey = src.coords[ptCursor * 2 + 1]
                builder.quadTo(c1x, c1y, ex, ey)
            }
            SkPath.Verb.kConic -> {
                conicCursor -= 1
                val w = src.conicWeights[conicCursor]
                val c1x = src.coords[(ptCursor + 1) * 2]
                val c1y = src.coords[(ptCursor + 1) * 2 + 1]
                val ex = src.coords[ptCursor * 2]; val ey = src.coords[ptCursor * 2 + 1]
                builder.conicTo(c1x, c1y, ex, ey, w)
            }
            SkPath.Verb.kCubic -> {
                // pts[0..3] = pen, c1, c2, end. Reversed : end → c2 → c1 → pen.
                // builder.cubicTo(c1, c2, e) emits (orig c2, orig c1, orig pen).
                val c2x = src.coords[(ptCursor + 2) * 2]
                val c2y = src.coords[(ptCursor + 2) * 2 + 1]
                val c1x = src.coords[(ptCursor + 1) * 2]
                val c1y = src.coords[(ptCursor + 1) * 2 + 1]
                val ex = src.coords[ptCursor * 2]; val ey = src.coords[ptCursor * 2 + 1]
                builder.cubicTo(c2x, c2y, c1x, c1y, ex, ey)
            }
            SkPath.Verb.kClose -> {
                needClose = true
            }
            SkPath.Verb.kDone -> error("kDone is iterator-only, never stored")
        }
        --i
    }
}

private fun ptsInVerb(v: SkPath.Verb): Int = when (v) {
    SkPath.Verb.kMove -> 1
    SkPath.Verb.kLine -> 1
    SkPath.Verb.kQuad, SkPath.Verb.kConic -> 2
    SkPath.Verb.kCubic -> 3
    SkPath.Verb.kClose -> 0
    SkPath.Verb.kDone -> 0
}

/**
 * Walk [path]'s verb stream split by [contours] ; for each contour
 * with `reverse = false`, append directly to the result builder ;
 * for those with `reverse = true`, build a temporary path then
 * [reverseAddPath] it onto the result. Returns the reconstituted
 * path with [fillType].
 *
 * Mirrors `OpAsWinding::reverseMarkedContours`
 * (`src/pathops/SkPathOpsAsWinding.cpp:366`).
 */
internal fun reverseMarkedContours(
    path: SkPath,
    contours: List<AsWindingContour>,
    fillType: org.skia.foundation.SkPathFillType,
): SkPath {
    val result = org.skia.foundation.SkPathBuilder().setFillType(fillType)
    var verbCursor = 0
    val rec = AsWindingVerbCursor(path)
    for (contour in contours) {
        val tempBuilder = if (contour.reverse) org.skia.foundation.SkPathBuilder()
                          else result
        // Walk verbs from current position up to contour.verbEnd.
        while (verbCursor < contour.verbEnd) {
            rec.emitNext(tempBuilder)
            ++verbCursor
        }
        if (contour.reverse) {
            reverseAddPath(result, tempBuilder.detach())
        }
    }
    return result.detach()
}

/**
 * Stateful verb-emit cursor : tracks position in the source path's
 * coord / conic arrays as we walk and emit one verb at a time
 * onto a target builder.
 */
private class AsWindingVerbCursor(private val src: SkPath) {
    private var verbIdx = 0
    private var coordIdx = 0
    private var conicIdx = 0
    private var penX = 0f; private var penY = 0f

    fun emitNext(builder: org.skia.foundation.SkPathBuilder) {
        val v = src.verbs[verbIdx++]
        when (v) {
            SkPath.Verb.kMove -> {
                val x = src.coords[coordIdx]; val y = src.coords[coordIdx + 1]
                coordIdx += 2
                builder.moveTo(x, y); penX = x; penY = y
            }
            SkPath.Verb.kLine -> {
                val x = src.coords[coordIdx]; val y = src.coords[coordIdx + 1]
                coordIdx += 2
                builder.lineTo(x, y); penX = x; penY = y
            }
            SkPath.Verb.kQuad -> {
                val cx = src.coords[coordIdx]; val cy = src.coords[coordIdx + 1]
                val ex = src.coords[coordIdx + 2]; val ey = src.coords[coordIdx + 3]
                coordIdx += 4
                builder.quadTo(cx, cy, ex, ey); penX = ex; penY = ey
            }
            SkPath.Verb.kConic -> {
                val cx = src.coords[coordIdx]; val cy = src.coords[coordIdx + 1]
                val ex = src.coords[coordIdx + 2]; val ey = src.coords[coordIdx + 3]
                coordIdx += 4
                val w = src.conicWeights[conicIdx++]
                builder.conicTo(cx, cy, ex, ey, w); penX = ex; penY = ey
            }
            SkPath.Verb.kCubic -> {
                val c1x = src.coords[coordIdx]; val c1y = src.coords[coordIdx + 1]
                val c2x = src.coords[coordIdx + 2]; val c2y = src.coords[coordIdx + 3]
                val ex = src.coords[coordIdx + 4]; val ey = src.coords[coordIdx + 5]
                coordIdx += 6
                builder.cubicTo(c1x, c1y, c2x, c2y, ex, ey); penX = ex; penY = ey
            }
            SkPath.Verb.kClose -> { builder.close() }
            SkPath.Verb.kDone -> error("kDone is iterator-only, never stored")
        }
    }
}
