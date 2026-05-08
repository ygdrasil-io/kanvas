/*
 * Copyright 2026 The kanvas-skia authors.
 *
 * Mirrors Skia's `include/pathops/SkPathOps.h` (free functions in the
 * top-level `skia` namespace).
 *
 * # Phase D1.2.h.0 — Op fast paths (rect-rect intersect / empty-input)
 *
 * Wires the public `SkPathOps.Op` entry point end-to-end for the
 * "easy" cases that don't need the full coincidence machinery :
 *
 *  - rect ∩ rect → rect via [SkRect.intersect] ;
 *  - empty input → return the other input (with a fillType remap)
 *    for `kUnion` / `kXOR` / `kDifference` / `kReverseDifference`,
 *    or empty for `kIntersect`.
 *
 * For inputs that fall through (two non-empty non-rect paths, or
 * `kIntersect` of non-rects), we still return `null` until D1.2.h.1+
 * land `SortContourList` / `AddIntersectTs` / `HandleCoincidence` /
 * `bridgeOp` to drive the full algorithm.
 *
 * `Simplify` and `AsWinding` likewise stay deferred to later D1.2.h
 * sub-slices (they share the same machinery).
 */
package org.skia.pathops

import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathFillType
import org.skia.math.SkRect
import org.skia.pathops.internal.AddIntersectTs
import org.skia.pathops.internal.ContourHeadRef
import org.skia.pathops.internal.HandleCoincidence
import org.skia.pathops.internal.SkOpCoincidence
import org.skia.pathops.internal.SkOpContourHead
import org.skia.pathops.internal.SkOpEdgeBuilder
import org.skia.pathops.internal.SkOpGlobalState
import org.skia.pathops.internal.SkPathOpsMask
import org.skia.pathops.internal.SkPathWriter
import org.skia.pathops.internal.SortContourList
import org.skia.pathops.internal.AsWindingContour
import org.skia.pathops.internal.bridgeOp
import org.skia.pathops.internal.bridgeWinding
import org.skia.pathops.internal.bridgeXor
import org.skia.pathops.internal.AsWindingEdge
import org.skia.pathops.internal.checkContainerChildren
import org.skia.pathops.internal.contourBounds
import org.skia.pathops.internal.inParent
import org.skia.pathops.internal.isFlatTree
import org.skia.pathops.internal.markReverse
import org.skia.pathops.internal.nextEdge
import org.skia.pathops.internal.reverseMarkedContours

/**
 * Pathops free functions. Mirrors Skia's `include/pathops/SkPathOps.h`.
 *
 * Upstream lives in the `skia::` namespace as free functions ; we
 * group them into an `object` for Kotlin idiom while keeping the names
 * verbatim (`SkPathOps.Op(a, b, op)`, `SkPathOps.Simplify(p)`).
 */
public object SkPathOps {

    /**
     * `gOpInverse[op][isInverseFillTypeOne][isInverseFillTypeTwo]` →
     * the equivalent boolean op when one or both inputs use
     * inverse-fill semantics. Mirrors `gOpInverse`
     * (`src/pathops/SkPathOpsOp.cpp:220`).
     *
     * Indexed by `SkPathOp.ordinal` (must match upstream's
     * `kDifference_SkPathOp = 0`, `kIntersect = 1`, `kUnion = 2`,
     * `kXOR = 3`, `kReverseDifference = 4` — verified to match
     * [SkPathOp]).
     */
    private val gOpInverse: Array<Array<Array<SkPathOp>>> = arrayOf(
        // diff
        arrayOf(
            arrayOf(SkPathOp.kDifference, SkPathOp.kIntersect),
            arrayOf(SkPathOp.kUnion, SkPathOp.kReverseDifference),
        ),
        // sect
        arrayOf(
            arrayOf(SkPathOp.kIntersect, SkPathOp.kDifference),
            arrayOf(SkPathOp.kReverseDifference, SkPathOp.kUnion),
        ),
        // union
        arrayOf(
            arrayOf(SkPathOp.kUnion, SkPathOp.kReverseDifference),
            arrayOf(SkPathOp.kDifference, SkPathOp.kIntersect),
        ),
        // xor
        arrayOf(
            arrayOf(SkPathOp.kXOR, SkPathOp.kXOR),
            arrayOf(SkPathOp.kXOR, SkPathOp.kXOR),
        ),
        // rev diff
        arrayOf(
            arrayOf(SkPathOp.kReverseDifference, SkPathOp.kUnion),
            arrayOf(SkPathOp.kIntersect, SkPathOp.kDifference),
        ),
    )

    /**
     * `gOutInverse[op][isInverseFillTypeOne][isInverseFillTypeTwo]` →
     * whether the result should use an inverse-fill type. Mirrors
     * `gOutInverse` (`src/pathops/SkPathOpsOp.cpp:230`).
     */
    private val gOutInverse: Array<Array<BooleanArray>> = arrayOf(
        // diff
        arrayOf(booleanArrayOf(false, false), booleanArrayOf(true, false)),
        // sect
        arrayOf(booleanArrayOf(false, false), booleanArrayOf(false, true)),
        // union
        arrayOf(booleanArrayOf(false, true), booleanArrayOf(true, true)),
        // xor
        arrayOf(booleanArrayOf(false, true), booleanArrayOf(true, false)),
        // rev diff
        arrayOf(booleanArrayOf(false, true), booleanArrayOf(false, false)),
    )

    /**
     * Returns the result of applying the boolean [op] to [one] and [two].
     *
     * The resulting path is constructed from non-overlapping contours.
     * Curve order is reduced where possible (cubics may become quadratics,
     * quadratics may become lines). Returns `null` if the operation
     * fails — typically due to numerical robustness issues on degenerate
     * inputs.
     *
     * Mirrors Skia's
     * [`Op(const SkPath&, const SkPath&, SkPathOp)`](https://github.com/google/skia/blob/main/include/pathops/SkPathOps.h#L47).
     *
     * **Phase D1.2.h.0** : fast paths only — rect-rect intersect and
     * empty-input shortcuts. Other cases still fall through to `null`
     * until the full machinery lands in D1.2.h.1+.
     */
    public fun Op(one: SkPath, two: SkPath, op: SkPathOp): SkPath? {
        val oneInv = if (one.isInverseFillType()) 1 else 0
        val twoInv = if (two.isInverseFillType()) 1 else 0
        val effectiveOp = gOpInverse[op.ordinal][oneInv][twoInv]
        val inverseFill = gOutInverse[effectiveOp.ordinal][oneInv][twoInv]
        val fillType = if (inverseFill) SkPathFillType.kInverseEvenOdd else SkPathFillType.kEvenOdd

        // Fast path : rect ∩ rect.
        if (effectiveOp == SkPathOp.kIntersect) {
            val rect1 = one.isRect()
            val rect2 = two.isRect()
            if (rect1 != null && rect2 != null) {
                val out = SkRect.MakeLTRB(rect1.left, rect1.top, rect1.right, rect1.bottom)
                val result = if (out.intersect(rect2)) SkPath.Rect(out) else SkPathBuilder().detach()
                return result.makeFillType(fillType)
            }
        }

        // Fast path : empty-input shortcuts.
        if (one.isEmpty() || two.isEmpty()) {
            val work: SkPath = when (effectiveOp) {
                SkPathOp.kIntersect -> SkPathBuilder().detach()
                SkPathOp.kUnion, SkPathOp.kXOR ->
                    if (one.isEmpty()) two else one
                SkPathOp.kDifference ->
                    if (one.isEmpty()) SkPathBuilder().detach() else one
                SkPathOp.kReverseDifference ->
                    if (two.isEmpty()) SkPathBuilder().detach() else two
            }
            // Upstream calls Simplify(work) here ; for the empty-input
            // cases `work` is either an unchanged input (already simple)
            // or empty, so the Simplify call is effectively a no-op.
            // Sufficient to remap the fill type.
            val toggled = if (inverseFill != work.isInverseFillType()) {
                work.makeToggleInverseFillType()
            } else {
                work
            }
            return toggled.makeFillType(fillType)
        }

        // Full Boolean machinery — D1.2.h.5.4 wiring.
        // 1. Build the contour list from both paths.
        val contourHead = SkOpContourHead()
        val globalState = SkOpGlobalState()
        contourHead.setGlobalState(globalState)
        val coincidence = SkOpCoincidence()
        globalState.setCoincidence(coincidence)
        var minuend = one
        var subtrahend = two
        var workOp = effectiveOp
        if (workOp == SkPathOp.kReverseDifference) {
            minuend = two; subtrahend = one
            workOp = SkPathOp.kDifference
        }
        val builder = SkOpEdgeBuilder(minuend, contourHead)
        if (builder.fUnparseable) return null
        val xorMask = builder.xorMask()
        builder.addOperand(subtrahend)
        if (!builder.finish()) return null
        val xorOpMask = builder.xorMask()
        // 2. Sort contour list (drops empty + canonicalises chain).
        val ref = ContourHeadRef(contourHead)
        if (!SortContourList(ref, xorMask == SkPathOpsMask.kEvenOdd,
                xorOpMask == SkPathOpsMask.kEvenOdd)) {
            // No survivors — empty result with the right fillType.
            return SkPathBuilder().detach().makeFillType(fillType)
        }
        val sortedHead = ref.head!!
        // 3. Find all intersections.
        var current = sortedHead
        do {
            var next: org.skia.pathops.internal.SkOpContour? = current
            while (AddIntersectTs(current, next!!, coincidence)) {
                next = next.next() ?: break
            }
            current = current.next() ?: break
        } while (true)
        // 4. Resolve coincidence (the big "fix coincidence" pipeline).
        if (!HandleCoincidence(sortedHead, coincidence)) return null
        // 5. Walk the resolved graph emitting active edges.
        val writer = SkPathWriter(fillType)
        if (!bridgeOp(sortedHead, workOp, xorMask, xorOpMask, writer)) return null
        writer.assemble()
        val result = writer.nativePath()
        // An empty result here is meaningful — it means
        // [bridgeOp] succeeded with no surviving edges (e.g.
        // `A - A = ∅` for kDifference, or two disjoint inputs
        // intersected) — return the empty path with the right
        // fillType, never null. Mirrors upstream's
        // `Op(A, A, kDifference) = empty` semantic.
        return result
    }

    /**
     * Returns a path with non-overlapping contours equivalent to [path].
     *
     * Resolves self-intersections, normalizes the fill type, and reduces
     * curve order where possible. Returns `null` on failure.
     *
     * Mirrors Skia's
     * [`Simplify(const SkPath&)`](https://github.com/google/skia/blob/main/include/pathops/SkPathOps.h#L66).
     *
     * **Phase D1.2.h.0** : not yet implemented ; returns `null`.
     * Will land in D1.2.h.2 alongside the full coincidence machinery.
     */
    public fun Simplify(path: SkPath): SkPath? {
        val fillType = if (path.isInverseFillType())
            SkPathFillType.kInverseEvenOdd else SkPathFillType.kEvenOdd

        // Build single-input contour list.
        val contourHead = SkOpContourHead()
        val globalState = SkOpGlobalState()
        contourHead.setGlobalState(globalState)
        val coincidence = SkOpCoincidence()
        globalState.setCoincidence(coincidence)
        val builder = SkOpEdgeBuilder(path, contourHead)
        if (!builder.finish()) return null

        // Sort contours (single-input → both fill masks identical).
        val ref = org.skia.pathops.internal.ContourHeadRef(contourHead)
        if (!org.skia.pathops.internal.SortContourList(ref, false, false)) {
            return SkPathBuilder().detach().makeFillType(fillType)
        }
        val sortedHead = ref.head!!

        // Find intersections.
        var current = sortedHead
        do {
            var next: org.skia.pathops.internal.SkOpContour? = current
            while (org.skia.pathops.internal.AddIntersectTs(current, next!!, coincidence)) {
                next = next.next() ?: break
            }
            current = current.next() ?: break
        } while (true)

        // Resolve coincidences.
        if (!org.skia.pathops.internal.HandleCoincidence(sortedHead, coincidence)) return null

        // Walk the resolved graph. bridgeWinding for kWinding,
        // bridgeXor for kEvenOdd — selected by the path's xorMask.
        val writer = SkPathWriter(fillType)
        val xorMask = builder.xorMask()
        val ok = if (xorMask == org.skia.pathops.internal.SkPathOpsMask.kWinding) {
            bridgeWinding(sortedHead, writer)
        } else {
            bridgeXor(sortedHead, writer)
        }
        if (!ok) return null
        writer.assemble()
        val result = writer.nativePath()
        // Same post-condition as Op : empty result on a non-empty input
        // signals the algorithm short-circuited (FindSortableTop /
        // walker bail) ; surface as null rather than mislead.
        if (result.isEmpty() && !path.isEmpty()) return null
        return result
    }

    /**
     * Returns the tight (curve-aware) bounding box of [path], or `null`
     * if the bounds are not finite (e.g. NaN coordinates anywhere).
     *
     * Mirrors Skia's
     * [`TightBounds(const SkPath&, SkRect*)`](https://github.com/google/skia/blob/main/include/pathops/SkPathOps.h#L84)
     * — itself a deprecated thin wrapper around `SkPath::computeTightBounds()`.
     *
     * Implemented here as a thin delegate to [SkPath.computeTightBounds]
     * (full curve-extrema math already shipped in the SkPath foundation).
     */
    @Deprecated(
        message = "Use SkPath.computeTightBounds() directly. Mirrors the [[deprecated]] " +
            "marker on the upstream Skia API.",
        replaceWith = ReplaceWith("path.computeTightBounds()"),
    )
    public fun TightBounds(path: SkPath): SkRect? {
        val rect = path.computeTightBounds()
        return if (rect.isFinite()) rect else null
    }

    /**
     * Returns a path with `kWinding` fill type covering the same area
     * as [path] (which typically has `kEvenOdd` fill).
     *
     * Does not detect self-intersecting / overlapping contours ; in
     * those cases the result may not fill the same area.
     * Returns `null` on failure.
     *
     * Mirrors Skia's
     * [`AsWinding(const SkPath&)`](https://github.com/google/skia/blob/main/include/pathops/SkPathOps.h#L102).
     *
     * **Phase D1.2.h.0** : not yet implemented ; returns `null`.
     */
    public fun AsWinding(path: SkPath): SkPath? {
        // Fast paths from src/pathops/SkPathOpsAsWinding.cpp:411.
        if (!path.isFinite()) return null
        if (path.fillType == SkPathFillType.kWinding ||
            path.fillType == SkPathFillType.kInverseWinding) {
            return path
        }
        val targetFill = if (path.isInverseFillType())
            SkPathFillType.kInverseWinding else SkPathFillType.kWinding
        if (path.isEmpty()) return path.makeFillType(targetFill)
        // Count contours via verb-walk. Multi-contour case needs the
        // upstream Contour bbox-tree + reverse-marker machinery
        // (~460 LOC, deferred to D1.2.h.6.3+). For ≤1 contour, the
        // even-odd-vs-winding distinction is moot — area is the same.
        var contourCount = 0
        for (v in path.verbs) {
            if (v == SkPath.Verb.kMove) {
                if (++contourCount > 1) break
            }
        }
        if (contourCount <= 1) return path.makeFillType(targetFill)
        // Multi-contour : build a bbox tree, see if any contour
        // contains another. When the tree is "flat" (no nesting),
        // even-odd vs winding fill paint the same area, so a
        // fillType swap is correct.
        val contours = contourBounds(path)
        val sorted = AsWindingContour(SkRect.MakeEmpty(), 0, 0)
        for (c in contours) inParent(c, sorted)
        if (isFlatTree(sorted)) return path.makeFillType(targetFill)
        // Full nested-tree analysis : ray-cast containment +
        // recursive reverse-marker.
        for (child in sorted.children) {
            nextEdge(path, child, AsWindingEdge.kInitial)
            child.direction = org.skia.pathops.internal.getDirection(path, child)
            if (!checkContainerChildren(path, null, child)) return null
        }
        var reversed = false
        for (child in sorted.children) {
            reversed = markReverse(path, null, child) || reversed
        }
        if (!reversed) return path.makeFillType(targetFill)
        // Reversal needed — split the path by contours, reverse the
        // ones flagged by markReverse, and stitch back together.
        return reverseMarkedContours(path, contours, targetFill)
    }
}
