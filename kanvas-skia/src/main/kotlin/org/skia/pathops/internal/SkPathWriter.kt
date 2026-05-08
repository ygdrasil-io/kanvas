/*
 * Copyright 2026 The kanvas-skia authors.
 *
 * Mirrors Skia's `class SkPathWriter` from `src/pathops/SkPathWriter.{h,cpp}`.
 *
 * Phase D1.2.i — per-contour writer + simple assembly.
 *
 * # What is SkPathWriter ?
 *
 * The contour-walker in `SkOpSegment` (D1.2.c.2) emits a series of
 * `moveTo` / `lineTo` / `quadTo` / `cubicTo` / `conicTo` calls as it
 * stitches together output contours. Some of these emissions form
 * closed contours directly ; others form *partial* contours whose
 * starts and ends need to be matched up to form the final boundary
 * (e.g. when an intersection bisects a contour, the two halves are
 * each emitted as partials and assembled here).
 *
 * `SkPathWriter` :
 *  - Buffers a "deferred move" (the start of a contour) so that the
 *    moveTo isn't emitted until the first non-line verb (or the
 *    `update` of a curve endpoint).
 *  - Buffers a "deferred line" so that consecutive collinear lines
 *    collapse into a single `lineTo`.
 *  - On `finishContour`, if the current contour ends at its start
 *    (`isClosed`), emits `close` and adds the contour to the final
 *    `fBuilder`. Otherwise it goes into `fPartials` for later
 *    assembly.
 *  - On `assemble`, walks the partials and connects each partial's
 *    end-point to the closest other partial's start-point until all
 *    are joined.
 *
 * What ships in D1.2.i :
 *  - All per-contour emission methods (`deferredMove` / `deferredLine`
 *    / `quadTo` / `cubicTo` / `conicTo` / `finishContour` / `close`
 *    / `lineTo` / `moveTo` / `matchedLast` / `changedSlopes` /
 *    `update` / `init` / `isClosed` / `hasMove` / `nativePath`).
 *  - `someAssemblyRequired` — true if any partials remain.
 *  - `assemble` simple variant : when there are no partials it's a
 *    no-op. The full matching algorithm (~150 LOC of pair-distance
 *    sort + link walking) is deferred to D1.2.i.2 ; calling it with
 *    partials present throws [NotImplementedError].
 */
package org.skia.pathops.internal

import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathFillType
import org.skia.math.SkPoint

internal class SkPathWriter(fillType: SkPathFillType) {

    /** Final accumulated path. Closed contours are added here directly. */
    private val fBuilder: SkPathBuilder = SkPathBuilder().also { /* fillType set on detach */ }
    private val fFillType: SkPathFillType = fillType

    /** Current contour under construction. */
    private val fCurrent: SkPathBuilder = SkPathBuilder()

    /** Open / unclosed contours awaiting [assemble]. */
    val fPartials: MutableList<SkPathBuilder> = mutableListOf()

    /** Possible (start, end) pt-T pairs for partial contours. */
    val fEndPtTs: MutableList<SkOpPtT> = mutableListOf()

    /** Pending move / line — `[0]` = deferred move source, `[1]` = deferred line source. */
    private val fDefer: Array<SkOpPtT?> = arrayOf(null, null)

    /** First pt-T of the current contour. */
    private var fFirstPtT: SkOpPtT? = null

    init { init() }

    /** Mirrors `SkPathWriter::init`. */
    fun init() {
        fCurrent.reset()
        fFirstPtT = null
        fDefer[0] = null
        fDefer[1] = null
    }

    fun hasMove(): Boolean = fFirstPtT == null

    /** True iff the current contour's last emitted point matches its first. */
    fun isClosed(): Boolean = matchedLast(fFirstPtT)

    /** Detach the final builder into an [SkPath]. Mirrors `nativePath`. */
    fun nativePath(): SkPath = fBuilder.detach().makeFillType(fFillType)

    // ─── Per-verb writes ───────────────────────────────────────────

    /**
     * Emit a move : if no line has been deferred yet, just record this
     * pt-T as the contour start ; otherwise finish the previous
     * contour first. Mirrors `deferredMove`.
     */
    fun deferredMove(pt: SkOpPtT) {
        if (fDefer[1] == null) {
            fFirstPtT = pt
            fDefer[0] = pt
            return
        }
        require(fDefer[0] != null)
        if (!matchedLast(pt)) {
            finishContour()
            fFirstPtT = pt
            fDefer[0] = pt
        }
    }

    /**
     * Emit a line : returns true on success, false if the line is
     * degenerate (same as the previously-deferred line target).
     * Mirrors `deferredLine`.
     */
    fun deferredLine(pt: SkOpPtT): Boolean {
        require(fFirstPtT != null) { "deferredLine before deferredMove" }
        require(fDefer[0] != null)
        if (fDefer[0] === pt) return true // FIXME (upstream) : degenerate line
        if (pt.contains(fDefer[0]!!)) return true // FIXME : degenerate
        if (matchedLast(pt)) return false
        if (fDefer[1] != null && changedSlopes(pt)) {
            lineTo()
            fDefer[0] = fDefer[1]
        }
        fDefer[1] = pt
        return true
    }

    /** Mirrors `quadTo`. */
    fun quadTo(pt1: SkPoint, pt2: SkOpPtT) {
        val pt2pt = update(pt2)
        fCurrent.quadTo(pt1.fX, pt1.fY, pt2pt.fX, pt2pt.fY)
    }

    /** Mirrors `cubicTo`. */
    fun cubicTo(pt1: SkPoint, pt2: SkPoint, pt3: SkOpPtT) {
        val pt3pt = update(pt3)
        fCurrent.cubicTo(pt1.fX, pt1.fY, pt2.fX, pt2.fY, pt3pt.fX, pt3pt.fY)
    }

    /** Mirrors `conicTo`. */
    fun conicTo(pt1: SkPoint, pt2: SkOpPtT, weight: Float) {
        val pt2pt = update(pt2)
        fCurrent.conicTo(pt1.fX, pt1.fY, pt2pt.fX, pt2pt.fY, weight)
    }

    /**
     * Wrap up the current contour — emit any deferred line, then
     * close it (if the contour is closed) or queue it as a partial.
     * Mirrors `finishContour`.
     */
    fun finishContour() {
        if (!matchedLast(fDefer[0])) {
            if (fDefer[1] == null) return
            lineTo()
        }
        if (fCurrent.isEmpty()) return
        if (isClosed()) {
            close()
        } else {
            require(fDefer[1] != null)
            fEndPtTs.add(fFirstPtT!!)
            fEndPtTs.add(fDefer[1]!!)
            fPartials.add(snapshotCurrent())
            init()
        }
    }

    fun someAssemblyRequired(): Boolean {
        finishContour()
        return fEndPtTs.isNotEmpty()
    }

    /**
     * Stitch any partial contours into closed boundaries by matching
     * each partial's start to the closest other partial's end.
     * Mirrors `assemble`.
     *
     * **Phase D1.2.i** : the trivial case (no partials) is a no-op.
     * The full pair-distance-sort + link-walking algorithm
     * (~150 LOC) is deferred to D1.2.i.2 — most simple closed-contour
     * tests don't exercise it. If partials remain after `finishContour`,
     * this throws [NotImplementedError] so the missing path is loud.
     */
    fun assemble() {
        if (!someAssemblyRequired()) return
        throw NotImplementedError(
            "SkPathWriter.assemble() with partials lands in Phase D1.2.i.2"
        )
    }

    // ─── Private helpers ───────────────────────────────────────────

    /** Mirrors `close`. */
    private fun close() {
        if (fCurrent.isEmpty()) return
        require(isClosed())
        fCurrent.close()
        // Concatenate the closed contour into the final builder.
        val snap = fCurrent.detach()
        fBuilder.addPath(snap, mode = SkPath.AddPathMode.kAppend)
        init()
    }

    /** Emit a `lineTo` for the deferred line target. Mirrors `lineTo`. */
    private fun lineTo() {
        if (fCurrent.isEmpty()) moveTo()
        val target = fDefer[1]!!
        fCurrent.lineTo(target.fPt.fX, target.fPt.fY)
    }

    /** Emit a `moveTo` for the contour start. Mirrors `moveTo`. */
    private fun moveTo() {
        val first = fFirstPtT!!
        fCurrent.moveTo(first.fPt.fX, first.fPt.fY)
    }

    /**
     * True iff [test] is the same as the last-emitted pt-T (or in its
     * opp loop). Mirrors `matchedLast`.
     */
    private fun matchedLast(test: SkOpPtT?): Boolean {
        if (test === fDefer[1]) return true
        if (test == null) return false
        if (fDefer[1] == null) return false
        return test.contains(fDefer[1]!!)
    }

    /**
     * True if appending [ptT] to the deferred line would change its
     * slope (i.e. they're not collinear). Mirrors `changedSlopes`.
     */
    private fun changedSlopes(ptT: SkOpPtT): Boolean {
        if (matchedLast(fDefer[0])) return false
        val a = fDefer[0]!!.fPt; val b = fDefer[1]!!.fPt; val c = ptT.fPt
        val deferDx = b.fX - a.fX; val deferDy = b.fY - a.fY
        val lineDx = c.fX - b.fX; val lineDy = c.fY - b.fY
        return deferDx * lineDy != deferDy * lineDx
    }

    /**
     * Emit any deferred move / line and snap the result point to the
     * contour start if [pt] is in [fFirstPtT]'s opp loop. Returns the
     * final point to use. Mirrors `update`.
     */
    private fun update(pt: SkOpPtT): SkPoint {
        if (fDefer[1] == null) moveTo()
        else if (!matchedLast(fDefer[0])) lineTo()
        var result = pt.fPt
        val first = fFirstPtT
        if (first != null && result != first.fPt && first.contains(pt)) {
            result = first.fPt
        }
        fDefer[0] = pt
        fDefer[1] = pt
        return result
    }

    /** Take a stable snapshot of the current contour for later assembly. */
    private fun snapshotCurrent(): SkPathBuilder {
        val snap = SkPathBuilder()
        // Copy by detaching + re-adding ; the resulting builder is detached
        // from fCurrent's mutable state.
        val detached = fCurrent.detach()
        snap.addPath(detached, mode = SkPath.AddPathMode.kAppend)
        return snap
    }
}
