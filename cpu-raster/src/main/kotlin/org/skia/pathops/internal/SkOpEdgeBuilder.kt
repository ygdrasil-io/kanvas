/*
 * Copyright 2026 The kanvas-skia authors.
 *
 * Mirrors Skia's `class SkOpEdgeBuilder` from
 * `src/pathops/SkOpEdgeBuilder.{h,cpp}`. Reads `SkPath` verbs into
 * an `SkOpContourHead` via the `SkOpContourBuilder` (D1.2.e).
 *
 * Phase D1.2.f — minimum-viable port :
 *  - `preFetch` walks the verbs array and flattens it into a
 *    pre-processed (verb, point) stream with implicit `kClose`s
 *    inserted before each `kMove` and at end-of-path. Includes
 *    `force_small_to_zero` for numerical stability.
 *  - `walk` dispatches the pre-processed stream to the
 *    `SkOpContourBuilder` ; a `kMove` opens a fresh contour, a
 *    `kClose` flushes/closes the current one.
 *  - `addOperand` allows the binary-op two-path mode.
 *
 * Curvature-splitting heuristics — `SkChopQuadAtMaxCurvature` /
 * `SkConic::chopAt` / `SkDCubic::ComplexBreak` and the
 * `SkReduceOrder::Quad/Cubic/Conic` degenerate-curve simplifier
 * (~150 LOC across two helper files) — are *not* ported in this
 * slice. The simplified walker passes verbs through untouched,
 * which is correct (intersections still find all crossings) but
 * may produce slightly more sub-divisions in pathological cases.
 * Curvature splitting can be added later for intersection-quality
 * tuning without changing this file's interface.
 */
package org.skia.pathops.internal


import org.graphiks.math.FLT_EPSILON_ORDERABLE_ERR
import org.graphiks.math.SkDPoint
import kotlin.math.abs
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathFillType
import org.graphiks.math.SkPoint

/** Path-op fill mask. Mirrors `enum SkPathOpsMask`. */
internal object SkPathOpsMask {
    const val kWinding: Int = -1
    const val kNo: Int = 0
    const val kEvenOdd: Int = 1
}

internal class SkOpEdgeBuilder(
    private var fPath: SkPath,
    private val fContoursHead: SkOpContourHead,
    private var fAllowOpenContours: Boolean = false,
) {

    private val fContourBuilder: SkOpContourBuilder = SkOpContourBuilder(fContoursHead)
    private val fPathVerbs: MutableList<SkPath.Verb> = mutableListOf()
    private val fPathPts: MutableList<SkPoint> = mutableListOf()
    private val fWeights: MutableList<Float> = mutableListOf()

    /** `fXorMask[0]` = subject path mask, `fXorMask[1]` = operand. */
    private val fXorMask: IntArray = IntArray(2)

    /** Index into `fPathVerbs` where the operand path starts. */
    private var fSecondHalf: Int = 0

    private var fOperand: Boolean = false

    /** True when the path has non-finite values. */
    var fUnparseable: Boolean = false
        private set

    init { init() }

    /**
     * Per-construction init — reads fill type from the current path
     * and pre-fetches the verb stream. Mirrors `SkOpEdgeBuilder::init`.
     */
    fun init() {
        fOperand = false
        val mask = fillMaskFor(fPath)
        fXorMask[0] = mask
        fXorMask[1] = mask
        fUnparseable = false
        fSecondHalf = preFetch()
    }

    /** Append a second operand path (for binary ops). Mirrors `addOperand`. */
    fun addOperand(path: SkPath) {
        // The previous preFetch added an implicit `kDone` (we use end-of-list
        // as the equivalent — nothing to pop here).
        fPath = path
        fXorMask[1] = fillMaskFor(path)
        preFetch()
    }

    /** Mirrors `SkOpEdgeBuilder::xorMask`. */
    fun xorMask(): Int = fXorMask[if (fOperand) 1 else 0]

    /** Top-level entry point. Mirrors `SkOpEdgeBuilder::finish`. */
    fun finish(): Boolean {
        fOperand = false
        if (fUnparseable || !walk()) return false
        complete()
        val contour = fContourBuilder.contour()
        if (contour.count() == 0) fContoursHead.remove(contour)
        return true
    }

    /** Flush any buffered builder state and finalize the current contour. */
    fun complete() {
        fContourBuilder.flush()
        val contour = fContourBuilder.contour()
        if (contour.count() != 0) {
            contour.complete()
            // No `setContour(null)` since SkOpContourBuilder requires a
            // non-null contour ; we just stop adding to it.
        }
    }

    fun head(): SkOpContour = fContoursHead

    // ─── Internals ─────────────────────────────────────────────────

    /**
     * Map `path.fillType` to the path-ops mask. Only the even-odd bit
     * matters here ; the inverse bit is purely a rendering concern and
     * is canonicalised away by `gOpInverse` / `gOutInverse` in
     * [SkPathOps.Op] before reaching the pipeline. Mirrors upstream's
     * `(int)fPath->getFillType() & 1`
     * (`SkOpEdgeBuilder.cpp:25` and `:57`).
     */
    private fun fillMaskFor(path: SkPath): Int =
        if (path.fillType.isEvenOdd()) SkPathOpsMask.kEvenOdd
        else SkPathOpsMask.kWinding

    /**
     * Auto-close the current contour by appending a line back to its
     * start (or by snapping the last endpoint to the start), then
     * emit a `kClose`. Mirrors `SkOpEdgeBuilder::closeContour`.
     */
    private fun closeContour(curveEnd: SkPoint, curveStart: SkPoint) {
        if (!approximatelyEqualSkPt(curveEnd, curveStart)) {
            fPathVerbs.add(SkPath.Verb.kLine)
            fPathPts.add(curveStart)
        } else {
            // Snap or back-out the trailing line so endpoint matches start.
            val verbCount = fPathVerbs.size
            val ptsCount = fPathPts.size
            if (SkPath.Verb.kLine == fPathVerbs[verbCount - 1]
                && fPathPts[ptsCount - 2] == curveStart
            ) {
                fPathVerbs.removeAt(verbCount - 1)
                fPathPts.removeAt(ptsCount - 1)
            } else {
                fPathPts[ptsCount - 1] = curveStart
            }
        }
        fPathVerbs.add(SkPath.Verb.kClose)
    }

    /**
     * Walk the SkPath verbs and flatten into `fPathVerbs` / `fPathPts`
     * / `fWeights`. Inserts implicit `kClose`s at the end of each
     * non-open contour. Mirrors `SkOpEdgeBuilder::preFetch`.
     *
     * Returns the *next* index after the last verb appended (used as
     * the operand-start index for binary ops).
     *
     * Skips the `SkReduceOrder` degenerate-curve simplifier ; passes
     * curves through verbatim.
     */
    private fun preFetch(): Int {
        if (!fPath.isFinite()) {
            fUnparseable = true
            return 0
        }
        var curveStart = SkPoint(0f, 0f)
        val curve = arrayOf(SkPoint(), SkPoint(), SkPoint(), SkPoint())
        var lastCurve = false
        var coordIdx = 0
        var weightIdx = 0
        for (rawVerb in fPath.verbs) {
            when (rawVerb) {
                SkPath.Verb.kMove -> {
                    if (!fAllowOpenContours && lastCurve) closeContour(curve[0], curveStart)
                    fPathVerbs.add(rawVerb)
                    val px = fPath.coords[coordIdx++]
                    val py = fPath.coords[coordIdx++]
                    curve[0] = forceSmallToZero(SkPoint(px, py))
                    fPathPts.add(curve[0])
                    curveStart = curve[0]
                    lastCurve = false
                }
                SkPath.Verb.kLine -> {
                    val ex = fPath.coords[coordIdx++]
                    val ey = fPath.coords[coordIdx++]
                    curve[1] = forceSmallToZero(SkPoint(ex, ey))
                    if (approximatelyEqualSkPt(curve[0], curve[1])) {
                        // Skip degenerate ; back-fill curve[0] if last verb wasn't kLine/kMove.
                        val lastVerb = if (fPathVerbs.isNotEmpty()) fPathVerbs.last() else null
                        if (lastVerb != SkPath.Verb.kLine && lastVerb != SkPath.Verb.kMove
                            && fPathPts.isNotEmpty()
                        ) {
                            fPathPts[fPathPts.size - 1] = curve[1]
                            curve[0] = curve[1]
                        }
                        continue
                    }
                    fPathVerbs.add(rawVerb)
                    fPathPts.add(curve[1])
                    curve[0] = curve[1]
                    lastCurve = true
                }
                SkPath.Verb.kQuad -> {
                    curve[1] = forceSmallToZero(SkPoint(fPath.coords[coordIdx++], fPath.coords[coordIdx++]))
                    curve[2] = forceSmallToZero(SkPoint(fPath.coords[coordIdx++], fPath.coords[coordIdx++]))
                    fPathVerbs.add(rawVerb)
                    fPathPts.add(curve[1]); fPathPts.add(curve[2])
                    curve[0] = curve[2]
                    lastCurve = true
                }
                SkPath.Verb.kConic -> {
                    curve[1] = forceSmallToZero(SkPoint(fPath.coords[coordIdx++], fPath.coords[coordIdx++]))
                    curve[2] = forceSmallToZero(SkPoint(fPath.coords[coordIdx++], fPath.coords[coordIdx++]))
                    val w = fPath.conicWeights[weightIdx++]
                    fPathVerbs.add(rawVerb)
                    fPathPts.add(curve[1]); fPathPts.add(curve[2])
                    fWeights.add(w)
                    curve[0] = curve[2]
                    lastCurve = true
                }
                SkPath.Verb.kCubic -> {
                    curve[1] = forceSmallToZero(SkPoint(fPath.coords[coordIdx++], fPath.coords[coordIdx++]))
                    curve[2] = forceSmallToZero(SkPoint(fPath.coords[coordIdx++], fPath.coords[coordIdx++]))
                    curve[3] = forceSmallToZero(SkPoint(fPath.coords[coordIdx++], fPath.coords[coordIdx++]))
                    fPathVerbs.add(rawVerb)
                    fPathPts.add(curve[1]); fPathPts.add(curve[2]); fPathPts.add(curve[3])
                    curve[0] = curve[3]
                    lastCurve = true
                }
                SkPath.Verb.kClose -> {
                    closeContour(curve[0], curveStart)
                    lastCurve = false
                }
                SkPath.Verb.kDone -> error("kDone is iterator-only, never stored")
            }
        }
        if (!fAllowOpenContours && lastCurve) closeContour(curve[0], curveStart)
        return fPathVerbs.size
    }

    /**
     * Walk the pre-fetched verb stream and dispatch to
     * `fContourBuilder`. Mirrors the simplified path of
     * `SkOpEdgeBuilder::walk` (no curvature splitting).
     */
    private fun walk(): Boolean {
        var ptIdx = 0
        var weightIdx = 0
        var contour: SkOpContour = fContourBuilder.contour()
        var moveToPtrBump = 0
        for (i in fPathVerbs.indices) {
            if (i == fSecondHalf) fOperand = true
            val verb = fPathVerbs[i]
            when (verb) {
                SkPath.Verb.kMove -> {
                    if (contour.count() != 0) {
                        if (fAllowOpenContours) complete()
                        else if (!close()) return false
                    }
                    if (contour.count() != 0 || contour !== fContoursHead) {
                        // Allocate a fresh contour for the new sub-path.
                        contour = fContoursHead.appendContour()
                        fContourBuilder.setContour(contour)
                    }
                    contour.init(fOperand, fXorMask[if (fOperand) 1 else 0] == SkPathOpsMask.kEvenOdd)
                    ptIdx += moveToPtrBump
                    moveToPtrBump = 1
                }
                SkPath.Verb.kLine -> {
                    // pts[ptIdx] is the segment start (previous verb's end / the
                    // move target for the first verb of a contour) ;
                    // pts[ptIdx + 1] is the line's terminal point.
                    fContourBuilder.addLine(arrayOf(fPathPts[ptIdx], fPathPts[ptIdx + 1]))
                    ptIdx += 1
                }
                SkPath.Verb.kQuad -> {
                    fContourBuilder.addQuad(arrayOf(
                        fPathPts[ptIdx], fPathPts[ptIdx + 1], fPathPts[ptIdx + 2],
                    ))
                    ptIdx += 2
                }
                SkPath.Verb.kConic -> {
                    val w = fWeights[weightIdx++]
                    fContourBuilder.addConic(arrayOf(
                        fPathPts[ptIdx], fPathPts[ptIdx + 1], fPathPts[ptIdx + 2],
                    ), w)
                    ptIdx += 2
                }
                SkPath.Verb.kCubic -> {
                    fContourBuilder.addCubic(arrayOf(
                        fPathPts[ptIdx], fPathPts[ptIdx + 1], fPathPts[ptIdx + 2], fPathPts[ptIdx + 3],
                    ))
                    ptIdx += 3
                }
                SkPath.Verb.kClose -> if (!close()) return false
                SkPath.Verb.kDone -> error("kDone is iterator-only, never stored")
            }
        }
        fContourBuilder.flush()
        if (contour.count() != 0 && !fAllowOpenContours && !close()) return false
        return true
    }

    private fun close(): Boolean { complete(); return true }

    companion object {
        private const val FLT_EPSILON_ORDERABLE_ERR: Float = 1.9073486328125e-6f // 16 * FLT_EPSILON

        /** Mirrors `force_small_to_zero` static helper. */
        fun forceSmallToZero(pt: SkPoint): SkPoint = SkPoint(
            fX = if (abs(pt.fX) < FLT_EPSILON_ORDERABLE_ERR) 0f else pt.fX,
            fY = if (abs(pt.fY) < FLT_EPSILON_ORDERABLE_ERR) 0f else pt.fY,
        )

        /**
         * `SkDPoint::ApproximatelyEqual` lifted to single-precision —
         * used by `force_small_to_zero`'s neighborhood check.
         */
        private fun approximatelyEqualSkPt(a: SkPoint, b: SkPoint): Boolean {
            return SkDPoint.ApproximatelyEqual(a, b)
        }
    }
}
