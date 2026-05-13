/*
 * Copyright 2026 The kanvas-skia authors.
 *
 * Mirrors Skia's `class SkPathWriter` from `src/pathops/SkPathWriter.{h,cpp}`.
 *
 * Phase D1.2.i — per-contour writer.
 * Phase D1.2.i.2 — `assemble` partials stitching (this file).
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
 * D1.2.i ships per-contour emission (`deferredMove` / `deferredLine`
 * / `quadTo` / `cubicTo` / `conicTo` / `finishContour` / `close`
 * / `lineTo` / `moveTo` / `matchedLast` / `changedSlopes` /
 * `update` / `init` / `isClosed` / `hasMove` / `nativePath`) and the
 * trivial no-partials path of [assemble].
 *
 * D1.2.i.2 ships the partials-stitcher : pair-distance matrix +
 * greedy match + link-walking, plus [reverseExtend] for backward
 * traversal. Phase 1 of upstream's `assemble` (lengthen partials
 * along simple segments) is deferred to **D1.2.c.2 / d** ; without it,
 * partials are stitched as-written, which is sufficient for
 * topologically-closed output.
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
     * each partial's start to the closest other partial's end. Mirrors
     * `SkPathWriter::assemble` (`src/pathops/SkPathWriter.cpp:206`).
     *
     * Algorithm :
     *  1. **Lengthen partials along simple segments** — *deferred to
     *     D1.2.c.2 / d* ; this is an optimisation that walks
     *     [SkOpSegment.isSimple] from each open endpoint and absorbs
     *     adjacent un-emitted segments. Without it, partials are
     *     stitched as-written ; the resulting path may have slightly
     *     more diagonal connectors but is still topologically closed.
     *  2. **Pair-distance matrix + greedy match** — for `endCount = 2 *
     *     fPartials.size`, build the (`endCount × endCount`) folded
     *     triangle of squared distances between every pair of
     *     endpoints, sort ascending, and greedily match the closest
     *     pair until all `linkCount = endCount / 2` pairs are joined.
     *     Encoded in `sLink[i]` / `eLink[i]` : a non-MAX value `v`
     *     means partial `i`'s start (resp. end) is matched to partial
     *     `v` if `v >= 0` (= other end), or partial `~v` if `v < 0`
     *     (= same-kind end : start↔start when stored in `sLink`,
     *     end↔end when stored in `eLink`).
     *  3. **Walk the chain** — starting at partial 0, append it
     *     forward to `fBuilder` ; at each transition follow the link
     *     out of the just-emitted partial's exit, emitting the next
     *     partial forward (with `kExtend`, so its `kMove` becomes a
     *     `lineTo`) or reversed (via [reverseExtend]) until we cycle
     *     back to the entry endpoint, at which point we [close]. Pick
     *     the next un-walked partial and repeat until none remain.
     */
    fun assemble() {
        if (!someAssemblyRequired()) return
        val endCount = fEndPtTs.size
        val linkCount = endCount / 2
        require(endCount > 0)
        require(endCount == fPartials.size * 2)

        // ── Phase 2 : pair-distance matrix + sort + greedy match ──
        val entries = endCount * (endCount - 1) / 2
        val distances = DoubleArray(entries)
        val distLookup = IntArray(entries)
        val sortedDist = IntArray(entries)
        var dIndex = 0
        var rRow = 0
        for (rIndex in 0 until endCount - 1) {
            val oPtT = fEndPtTs[rIndex]
            for (iIndex in rIndex + 1 until endCount) {
                val iPtT = fEndPtTs[iIndex]
                val dx = iPtT.fPt.fX.toDouble() - oPtT.fPt.fX.toDouble()
                val dy = iPtT.fPt.fY.toDouble() - oPtT.fPt.fY.toDouble()
                distances[dIndex] = dx * dx + dy * dy
                distLookup[dIndex] = rRow + iIndex
                sortedDist[dIndex] = dIndex
                dIndex++
            }
            rRow += endCount
        }
        require(dIndex == entries)
        // Stable sort sortedDist by distances[dIndex] ascending. (Each
        // entry of sortedDist is a dIndex in 0..entries-1 ; distances and
        // distLookup are both indexed by dIndex.)
        val sortedBoxed = sortedDist.toTypedArray()
        sortedBoxed.sortBy { distances[it] }
        for (i in sortedBoxed.indices) sortedDist[i] = sortedBoxed[i]

        val sLink = IntArray(linkCount) { Int.MAX_VALUE }
        val eLink = IntArray(linkCount) { Int.MAX_VALUE }
        var remaining = linkCount
        for (k in 0 until entries) {
            val pair = distLookup[sortedDist[k]]
            val row = pair / endCount
            val col = pair - row * endCount
            val ndxOne = row shr 1
            val endOne = (row and 1) != 0
            val linkOne = if (endOne) eLink else sLink
            if (linkOne[ndxOne] != Int.MAX_VALUE) continue
            val ndxTwo = col shr 1
            val endTwo = (col and 1) != 0
            val linkTwo = if (endTwo) eLink else sLink
            if (linkTwo[ndxTwo] != Int.MAX_VALUE) continue
            val flip = endOne == endTwo
            linkOne[ndxOne] = if (flip) ndxTwo.inv() else ndxTwo
            linkTwo[ndxTwo] = if (flip) ndxOne.inv() else ndxOne
            if (--remaining == 0) break
        }
        require(remaining == 0)

        // ── Phase 3 : walk the linked chain(s) ──
        var rIndex = 0
        do {
            var forward = true
            var first = true
            val sIndex = sLink[rIndex]
            require(sIndex != Int.MAX_VALUE)
            sLink[rIndex] = Int.MAX_VALUE
            var eIndex: Int
            if (sIndex < 0) {
                eIndex = sLink[sIndex.inv()]
                sLink[sIndex.inv()] = Int.MAX_VALUE
            } else {
                eIndex = eLink[sIndex]
                eLink[sIndex] = Int.MAX_VALUE
            }
            require(eIndex != Int.MAX_VALUE)

            inner@ while (true) {
                val contour = fPartials[rIndex].snapshot()
                if (forward) {
                    fBuilder.addPath(
                        contour,
                        mode = if (first) SkPath.AddPathMode.kAppend
                               else SkPath.AddPathMode.kExtend,
                    )
                } else {
                    require(!first)
                    reverseExtend(contour, fBuilder)
                }
                if (first) first = false

                val target = if ((rIndex != eIndex) xor forward) eIndex else eIndex.inv()
                if (sIndex == target) {
                    fBuilder.close()
                    break@inner
                }
                if (forward) {
                    eIndex = eLink[rIndex]
                    require(eIndex != Int.MAX_VALUE)
                    eLink[rIndex] = Int.MAX_VALUE
                    if (eIndex >= 0) {
                        require(sLink[eIndex] == rIndex)
                        sLink[eIndex] = Int.MAX_VALUE
                    } else {
                        require(eLink[eIndex.inv()] == rIndex.inv())
                        eLink[eIndex.inv()] = Int.MAX_VALUE
                    }
                } else {
                    eIndex = sLink[rIndex]
                    require(eIndex != Int.MAX_VALUE)
                    sLink[rIndex] = Int.MAX_VALUE
                    if (eIndex >= 0) {
                        require(eLink[eIndex] == rIndex)
                        eLink[eIndex] = Int.MAX_VALUE
                    } else {
                        require(sLink[eIndex.inv()] == rIndex.inv())
                        sLink[eIndex.inv()] = Int.MAX_VALUE
                    }
                }
                rIndex = eIndex
                if (rIndex < 0) {
                    forward = !forward
                    rIndex = rIndex.inv()
                }
            }

            // Find the next un-walked partial (sLink still set).
            var nextR = linkCount
            for (i in 0 until linkCount) {
                if (sLink[i] != Int.MAX_VALUE) { nextR = i; break }
            }
            rIndex = nextR
        } while (rIndex < linkCount)
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

    /**
     * Append [src] to [dest], reversed, in `kExtend` mode (i.e. [src]'s
     * leading `kMove` becomes a `lineTo` from [dest]'s current pen).
     * Mirrors `SkPathPriv::ReversePathTo` (used by `assemble` when a
     * partial must be walked backward to close a chain).
     *
     * Reversal rule per verb (forward `p1 → ... → p2` becomes reversed
     * `p2 → ... → p1`) :
     *  - `kLine(p1)` from `p0` ⇒ `kLine(p0)` from `p1`.
     *  - `kQuad(c, p2)` from `p1` ⇒ `kQuad(c, p1)` from `p2`.
     *  - `kConic(c, p2, w)` from `p1` ⇒ `kConic(c, p1, w)` from `p2`.
     *  - `kCubic(c1, c2, p2)` from `p1` ⇒ `kCubic(c2, c1, p1)` from `p2`.
     *
     * Assumes [src] is a single open contour : exactly one leading
     * `kMove`, no `kClose` (the partials produced by `finishContour`
     * always satisfy this).
     */
    private fun reverseExtend(src: SkPath, dest: SkPathBuilder) {
        if (src.isEmpty()) return
        val verbs = src.verbs
        val coords = src.coords
        val weights = src.conicWeights
        // Per-verb start offsets in [coords] (×2 floats per point) and
        // [weights] (1 weight per kConic).
        val pointStart = IntArray(verbs.size + 1)
        val weightStart = IntArray(verbs.size + 1)
        for (i in verbs.indices) {
            pointStart[i + 1] = pointStart[i] + verbs[i].pointCount * 2
            weightStart[i + 1] = weightStart[i] +
                if (verbs[i] == SkPath.StorageVerb.kConic) 1 else 0
        }
        // The reversed contour starts at the LAST emitted point.
        val tail = pointStart.last()
        val lastX = coords[tail - 2]
        val lastY = coords[tail - 1]
        // kExtend semantics : if dest is empty start with a kMove ;
        // otherwise lineTo to anchor the reverse walk to the pen.
        if (dest.isEmpty()) dest.moveTo(lastX, lastY)
        else dest.lineTo(lastX, lastY)
        // Walk verbs backward, emitting each verb's reversal.
        for (i in verbs.indices.reversed()) {
            val v = verbs[i]
            if (v == SkPath.StorageVerb.kMove) continue
            val pStart = pointStart[i]
            // Endpoint of the reversed verb = start point of the
            // forward verb = last point of the previous verb in the
            // stream.
            val endX = coords[pStart - 2]
            val endY = coords[pStart - 1]
            when (v) {
                SkPath.StorageVerb.kLine -> dest.lineTo(endX, endY)
                SkPath.StorageVerb.kQuad -> {
                    val cx = coords[pStart]; val cy = coords[pStart + 1]
                    dest.quadTo(cx, cy, endX, endY)
                }
                SkPath.StorageVerb.kConic -> {
                    val cx = coords[pStart]; val cy = coords[pStart + 1]
                    val w = weights[weightStart[i]]
                    dest.conicTo(cx, cy, endX, endY, w)
                }
                SkPath.StorageVerb.kCubic -> {
                    val c1x = coords[pStart];     val c1y = coords[pStart + 1]
                    val c2x = coords[pStart + 2]; val c2y = coords[pStart + 3]
                    dest.cubicTo(c2x, c2y, c1x, c1y, endX, endY)
                }
                SkPath.StorageVerb.kClose ->
                    error("reverseExtend : kClose inside a partial contour is unexpected")
                SkPath.StorageVerb.kMove -> Unit // unreachable (filtered above)
            }
        }
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
