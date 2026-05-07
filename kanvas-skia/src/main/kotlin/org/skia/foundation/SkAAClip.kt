package org.skia.foundation

import org.skia.math.SkIRect

/**
 * Iso-aligned port of Skia's
 * [`SkAAClip`](https://github.com/google/skia/blob/main/src/core/SkAAClip.h).
 *
 * Like [SkRegion] but with **fractional coverage** : every band stores
 * a sequence of `(width, alpha)` runs that partition the band's
 * X-extent and assign each run a coverage byte in `[0, 0xFF]`. AA-aware
 * paths produced by `clipPath(path, doAntiAlias = true)` benefit from
 * the alpha runs at edge pixels — rect-aligned clips collapse to a
 * single 0xFF run and behave the same as the binary [SkRegion] case.
 *
 * **Internal representation** : a sorted list of horizontal *bands*.
 * A band covers `[top, bottom)` along Y and carries two parallel
 * arrays :
 *  - `widths` : pixel widths per run ; `widths.sum() == fBounds.width()` ;
 *  - `alphas` : coverage byte per run, `alphas.size == widths.size`.
 *
 * Alpha-zero runs are valid (transparent regions inside the bounds) ;
 * the first non-zero run determines the band's effective left edge,
 * the last non-zero run the right edge. Bands are sorted by `top`
 * ascending ; consecutive bands with content-equal `(widths, alphas)`
 * collapse into a single taller band — *canonical form*.
 *
 * Skia stores `widths` as packed `uint8_t` (with overflow markers
 * for runs > 255 px) ; we use plain `IntArray` for clarity. The
 * asymptotic complexity matches.
 *
 * **Phase I3.2.a (this slice)** ships :
 *  - data model + canonical form for the rect / empty / region-promoted
 *    cases ;
 *  - constructors : empty, from [SkIRect], from [SkRegion], copy ctor ;
 *  - mutators : [setEmpty], [setRect], [setRegion], [set] ;
 *  - queries : [isEmpty] / [isRect] / [getBounds] ;
 *  - read-only [getRowCount] / [computeRunCount] for testability.
 *
 * **Phase I3.2.b (next)** will add :
 *  - `setPath(path, clip, doAA)` — true AA path rasterisation feeding
 *    coverage bytes per pixel ;
 *  - `op(other, op)` — set operations that combine alpha runs (Skia
 *    uses `min` for intersect, `max` for union, etc.).
 *
 * **Phase I3.3 (final)** will replace `SkBitmapDevice`'s ad-hoc
 * `clipMask: ByteArray` (Phase 7q) with a `SkRasterClip` carrying
 * either a `SkRegion` (binary clips, fast path) or an `SkAAClip` (AA
 * clips).
 */
public class SkAAClip private constructor(
    internal var bands: List<Band>,
    internal var fBounds: SkIRect,
    internal var fIsRectFastPath: Boolean,
) {

    /**
     * One horizontal band with RLE alpha runs along X.
     *
     * @property top    inclusive Y origin of the band.
     * @property bottom exclusive Y end ; `bottom > top`.
     * @property widths pixel widths per run ; `widths.sum() ==
     *                  outerBounds.width()` for the enclosing
     *                  [SkAAClip]. All entries are positive.
     * @property alphas coverage byte per run, `0..255`. Index-aligned
     *                  with [widths].
     */
    internal data class Band(
        val top: Int,
        val bottom: Int,
        val widths: IntArray,
        val alphas: ByteArray,
    ) {
        // Identity equals/hashCode — bands are immutable from the API
        // surface and content-equality is asserted via `equalsRuns`
        // when canonicalising.
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = System.identityHashCode(this)

        /** Total width of all runs ; equals the enclosing bounds' width. */
        internal fun runWidth(): Int {
            var w = 0
            for (e in widths) w += e
            return w
        }

        /** `true` iff `(widths, alphas)` content-equal. */
        internal fun equalsRuns(other: Band): Boolean =
            widths.contentEquals(other.widths) && alphas.contentEquals(other.alphas)
    }

    /** Empty AA clip. */
    public constructor() : this(emptyList(), SkIRect(0, 0, 0, 0), false)

    /** Rectangular AA clip with full coverage (`0xFF` everywhere). */
    public constructor(rect: SkIRect) : this(emptyList(), SkIRect(0, 0, 0, 0), false) {
        setRect(rect)
    }

    /**
     * Promote a binary [SkRegion] to an AA clip — every covered pixel
     * gets `0xFF` coverage, gaps inside the bounding box get `0x00`.
     */
    public constructor(rgn: SkRegion) : this(emptyList(), SkIRect(0, 0, 0, 0), false) {
        setRegion(rgn)
    }

    /** Copy ctor. */
    public constructor(other: SkAAClip) : this(emptyList(), SkIRect(0, 0, 0, 0), false) {
        set(other)
    }

    // ─── State mutators ────────────────────────────────────────────

    /** Reset to empty. Returns `false` (mirrors Skia's empty-setter contract). */
    public fun setEmpty(): Boolean {
        bands = emptyList()
        fBounds = SkIRect(0, 0, 0, 0)
        fIsRectFastPath = false
        return false
    }

    /**
     * Replace with the rectangular AA clip covering [r] at full
     * coverage. Empty / inverted [r] yields the empty clip.
     */
    public fun setRect(r: SkIRect): Boolean {
        if (r.isEmpty) return setEmpty()
        bands = listOf(
            Band(
                top = r.top, bottom = r.bottom,
                widths = intArrayOf(r.right - r.left),
                alphas = byteArrayOf(0xFF.toByte()),
            ),
        )
        fBounds = SkIRect(r.left, r.top, r.right, r.bottom)
        fIsRectFastPath = true
        return true
    }

    /**
     * Promote the binary region [rgn] to an AA clip. Every pixel
     * covered by [rgn] gets `0xFF` coverage ; gaps inside the
     * region's bounding box get `0x00`. Region rects sharing a
     * `(top, bottom)` Y range collapse into one [Band] with
     * interleaved alpha runs.
     */
    public fun setRegion(rgn: SkRegion): Boolean {
        if (rgn.isEmpty()) return setEmpty()
        if (rgn.isRect()) return setRect(rgn.getBounds())

        val outer = rgn.getBounds()
        val outerLeft = outer.left
        val outerRight = outer.right

        // Collect rects + group by (top, bottom). Skia regions emit
        // their rects in band-major order with bands sorted by Y, so
        // we can group consecutive same-Y rects into one band.
        val newBands = ArrayList<Band>()
        val it = SkRegion.Iterator(rgn)
        // Cursor for the current (top, bottom) accumulation.
        var curTop = -1
        var curBot = -1
        val curRects = ArrayList<SkIRect>()
        while (!it.done()) {
            val rect = it.rect()
            if (rect.top == curTop && rect.bottom == curBot) {
                curRects.add(rect)
            } else {
                if (curRects.isNotEmpty()) {
                    newBands.add(buildAaBandFromRects(curTop, curBot, curRects, outerLeft, outerRight))
                    curRects.clear()
                }
                curTop = rect.top
                curBot = rect.bottom
                curRects.add(rect)
            }
            it.next()
        }
        if (curRects.isNotEmpty()) {
            newBands.add(buildAaBandFromRects(curTop, curBot, curRects, outerLeft, outerRight))
        }

        // Coalesce consecutive bands with identical runs.
        val coalesced = ArrayList<Band>(newBands.size)
        for (b in newBands) {
            if (coalesced.isNotEmpty()) {
                val prev = coalesced[coalesced.size - 1]
                if (prev.bottom == b.top && prev.equalsRuns(b)) {
                    coalesced[coalesced.size - 1] = Band(prev.top, b.bottom, prev.widths, prev.alphas)
                    continue
                }
            }
            coalesced.add(b)
        }

        bands = coalesced
        fBounds = SkIRect(outer.left, outer.top, outer.right, outer.bottom)
        fIsRectFastPath = coalesced.size == 1 && coalesced[0].widths.size == 1 &&
            coalesced[0].alphas[0] == 0xFF.toByte()
        return true
    }

    /** Replace this AA clip's contents with a copy of [other]. */
    public fun set(other: SkAAClip): Boolean {
        bands = other.bands.toList()
        fBounds = SkIRect(other.fBounds.left, other.fBounds.top, other.fBounds.right, other.fBounds.bottom)
        fIsRectFastPath = other.fIsRectFastPath
        return !isEmpty()
    }

    // ─── State queries ─────────────────────────────────────────────

    /** `true` if no pixels are covered. */
    public fun isEmpty(): Boolean = bands.isEmpty()

    /**
     * `true` if every pixel inside the bounds has full (`0xFF`)
     * coverage and there are no gaps — i.e. the clip is equivalent
     * to a single rectangular [SkRegion].
     */
    public fun isRect(): Boolean = fIsRectFastPath

    /** Bounding rect ; defensive copy. */
    public fun getBounds(): SkIRect = SkIRect(fBounds.left, fBounds.top, fBounds.right, fBounds.bottom)

    /** Number of bands (Y-distinct horizontal slabs). Useful for tests. */
    internal fun getRowCount(): Int = bands.size

    /** Total `(width, alpha)` run count across every band. Useful for tests. */
    internal fun computeRunCount(): Int = bands.sumOf { it.widths.size }

    // ─── setPath (Phase I3.2.b) ────────────────────────────────────

    /**
     * Phase I3.2.b — rasterise [path] (intersected with [clip]) into
     * AA alpha runs. The [path]'s [SkPathFillType] drives the inside
     * test (winding / even-odd / inverse).
     *
     * @param doAA `false` → binary rasterisation (every covered pixel
     *             gets `0xFF`, equivalent to [SkRegion.setPath] +
     *             [setRegion]) ; `true` → 4×4 supersampled coverage,
     *             yielding alphas in `{0, 16, 32, ..., 240, 255}` at
     *             edge pixels.
     */
    public fun setPath(path: SkPath, clip: SkRegion, doAA: Boolean): Boolean {
        if (clip.isEmpty()) return setEmpty()
        if (!path.isFinite()) return setEmpty()
        if (path.isEmpty()) {
            return if (path.fillType.isInverse()) setRegion(clip) else setEmpty()
        }

        if (!doAA) {
            // Binary rasterisation : delegate to SkRegion.setPath then
            // promote ; every interior pixel ends up at 0xFF coverage.
            val region = SkRegion()
            region.setPath(path, clip)
            return setRegion(region)
        }

        return setPathAA(path, clip)
    }

    /**
     * Phase I3.2.b — AA rasterisation via 4×4 supersampling.
     *
     * Algorithm :
     *  1. Scale [path] by 4× via [SkPath.makeTransform] ;
     *  2. rasterise the scaled path through [SkRegion.setPath] using
     *     the 4× -scaled [clip] — each integer pixel of the resulting
     *     region corresponds to one 4×4 sub-sample of the original ;
     *  3. allocate a `width × height` 8-bit coverage buffer over the
     *     path's clipped bounds (in *original* pixel coordinates) ;
     *  4. walk the supersampled region's rectangles via
     *     [SkRegion.Iterator], accumulating one count per original
     *     pixel for every sub-sample inside ;
     *  5. scale counts (0..16) to alpha bytes (0..255) ;
     *  6. RLE-encode each row, group consecutive rows with content-
     *     equal `(widths, alphas)` into a single band, drop all-zero
     *     rows ;
     *  7. for inverse fills, complement the coverage buffer against
     *     the clip's bounds (`alpha → 255 - alpha`).
     */
    private fun setPathAA(path: SkPath, clip: SkRegion): Boolean {
        val isInverse = path.fillType.isInverse()
        val cb = clip.getBounds()

        // Determine the integer pixel bounds. For inverse fills, the
        // result spans the entire clip ; for non-inverse, only the
        // path's tight bounds intersected with the clip.
        val pathBounds = path.computeTightBounds()
        val origLeft: Int
        val origTop: Int
        val origRight: Int
        val origBot: Int
        if (isInverse) {
            origLeft = cb.left; origTop = cb.top
            origRight = cb.right; origBot = cb.bottom
        } else {
            origLeft = maxOf(kotlin.math.floor(pathBounds.left.toDouble()).toInt(), cb.left)
            origTop = maxOf(kotlin.math.floor(pathBounds.top.toDouble()).toInt(), cb.top)
            origRight = minOf(kotlin.math.ceil(pathBounds.right.toDouble()).toInt(), cb.right)
            origBot = minOf(kotlin.math.ceil(pathBounds.bottom.toDouble()).toInt(), cb.bottom)
        }
        if (origLeft >= origRight || origTop >= origBot) {
            return if (isInverse) setRegion(clip) else setEmpty()
        }

        val w = origRight - origLeft
        val h = origBot - origTop

        // 4× supersample : scale path into a temp region whose pixels
        // correspond to original sub-pixels.
        val scaled = path.makeTransform(org.skia.math.SkMatrix.MakeScale(4f, 4f))
        val ssClip = SkRegion(SkIRect(origLeft * 4, origTop * 4, origRight * 4, origBot * 4))
        // Use the non-inverse half of the fill rule for raster — we
        // complement the coverage buffer afterwards.
        val rasterPath = if (isInverse) {
            scaled.makeFillType(scaled.fillType.toggleInverse())
        } else {
            scaled
        }
        val ssRegion = SkRegion()
        if (!ssRegion.setPath(rasterPath, ssClip) && !isInverse) {
            return setEmpty()
        }

        // Coverage accumulator : `coverage[y * w + x]` ∈ 0..16 (sub-
        // sample count) ; scaled to 0..255 alpha after accumulation.
        val coverage = ByteArray(w * h)
        val it = SkRegion.Iterator(ssRegion)
        while (!it.done()) {
            val r = it.rect()
            for (ssY in r.top until r.bottom) {
                val origY = (ssY shr 2) - origTop  // floorDiv(ssY, 4) - origTop
                if (origY < 0 || origY >= h) { continue }
                val rowOff = origY * w
                for (ssX in r.left until r.right) {
                    val origX = (ssX shr 2) - origLeft
                    if (origX < 0 || origX >= w) continue
                    val idx = rowOff + origX
                    coverage[idx] = ((coverage[idx].toInt() and 0xFF) + 1).toByte()
                }
            }
            it.next()
        }

        // 0..16 → 0..255.
        for (i in coverage.indices) {
            val c = coverage[i].toInt() and 0xFF
            coverage[i] = (c * 255 / 16).toByte()
        }
        if (isInverse) {
            for (i in coverage.indices) {
                coverage[i] = (255 - (coverage[i].toInt() and 0xFF)).toByte()
            }
        }

        // RLE-encode rows ; coalesce consecutive rows with identical
        // runs into a single band ; drop all-zero rows.
        val newBands = buildBandsFromCoverage(coverage, origLeft, origTop, w, h)
        if (newBands.isEmpty()) return setEmpty()

        bands = newBands
        fBounds = computeUnionBounds(newBands, origLeft, origRight)
        fIsRectFastPath = newBands.size == 1 && newBands[0].widths.size == 1 &&
            newBands[0].alphas[0] == 0xFF.toByte()
        return true
    }

    // ─── Internal helpers (Phase I3.2.a) ───────────────────────────

    /**
     * Build one [Band] for a contiguous Y range `[top, bottom)` from
     * the list of binary rects covering it (already sorted in band-
     * major order by [SkRegion.Iterator]).
     *
     * Rects at the same `(top, bottom)` are guaranteed to be sorted
     * by X and disjoint per the [SkRegion] canonical form.
     *
     * The output `(widths, alphas)` partitions `[outerLeft,
     * outerRight)` into alternating `(gap, alpha=0)` and `(rect,
     * alpha=0xFF)` runs.
     */
    private fun buildAaBandFromRects(
        top: Int, bottom: Int, rects: List<SkIRect>,
        outerLeft: Int, outerRight: Int,
    ): Band {
        val widths = ArrayList<Int>(rects.size * 2 + 1)
        val alphas = ArrayList<Byte>(rects.size * 2 + 1)
        var x = outerLeft
        for (r in rects) {
            if (r.left > x) {
                widths.add(r.left - x)
                alphas.add(0)
            }
            widths.add(r.right - r.left)
            alphas.add(0xFF.toByte())
            x = r.right
        }
        if (x < outerRight) {
            widths.add(outerRight - x)
            alphas.add(0)
        }
        return Band(
            top = top, bottom = bottom,
            widths = IntArray(widths.size) { widths[it] },
            alphas = ByteArray(alphas.size) { alphas[it] },
        )
    }

    /**
     * Phase I3.2.b — group rows of [coverage] (laid out row-major, w
     * pixels per row, h rows total) into [Band]s. Each row is RLE-
     * compressed via [rleEncodeRow] ; consecutive rows with content-
     * equal `(widths, alphas)` arrays coalesce into a single band ;
     * all-zero rows are dropped (gap in the band list).
     *
     * Y origin is `origTop` (added to per-row indices when emitting
     * bands). The bands' X width invariant `widths.sum() == w` is
     * preserved by [rleEncodeRow].
     */
    private fun buildBandsFromCoverage(
        coverage: ByteArray, origLeft: Int, origTop: Int, w: Int, h: Int,
    ): List<Band> {
        val out = ArrayList<Band>()
        var pendingTop = 0
        var pending: Pair<IntArray, ByteArray>? = null

        for (y in 0 until h) {
            val rowStart = y * w
            val row = rleEncodeRow(coverage, rowStart, w)
            val isAllZero = row.second.all { it == 0.toByte() }
            if (isAllZero) {
                pending?.let { (pw, pa) ->
                    out.add(Band(origTop + pendingTop, origTop + y, pw, pa))
                }
                pending = null
                continue
            }
            val current = pending
            if (current == null) {
                pending = row
                pendingTop = y
            } else if (
                !row.first.contentEquals(current.first) ||
                !row.second.contentEquals(current.second)
            ) {
                out.add(Band(origTop + pendingTop, origTop + y, current.first, current.second))
                pending = row
                pendingTop = y
            }
        }
        pending?.let { (pw, pa) ->
            out.add(Band(origTop + pendingTop, origTop + h, pw, pa))
        }
        return out
    }

    /**
     * Run-length encode one row of [coverage] starting at byte
     * [rowStart], spanning [w] pixels. Returns parallel `(widths,
     * alphas)` arrays where `widths.sum() == w` and `alphas[i]` is
     * the alpha byte for run `i`.
     */
    private fun rleEncodeRow(coverage: ByteArray, rowStart: Int, w: Int): Pair<IntArray, ByteArray> {
        val widths = ArrayList<Int>()
        val alphas = ArrayList<Byte>()
        var i = 0
        while (i < w) {
            val a = coverage[rowStart + i]
            var j = i + 1
            while (j < w && coverage[rowStart + j] == a) j++
            widths.add(j - i)
            alphas.add(a)
            i = j
        }
        return Pair(
            IntArray(widths.size) { widths[it] },
            ByteArray(alphas.size) { alphas[it] },
        )
    }

    /**
     * Phase I3.2.b — compute the tight bounding rect of [bands].
     * `top` is the first band's `top`, `bottom` is the last band's
     * `bottom`, `left` / `right` are tightened by inspecting the
     * leading / trailing zero-alpha runs per band (a row that starts
     * with `(20, 0), (30, 255)` has effective `left = origLeft + 20`).
     */
    private fun computeUnionBounds(bands: List<Band>, outerLeft: Int, outerRight: Int): SkIRect {
        var minLeft = outerRight
        var maxRight = outerLeft
        for (b in bands) {
            // Skip leading zero runs.
            var x = outerLeft
            var firstNonZero = -1
            var lastNonZero = -1
            for (i in b.widths.indices) {
                val nextX = x + b.widths[i]
                if (b.alphas[i] != 0.toByte()) {
                    if (firstNonZero < 0) firstNonZero = x
                    lastNonZero = nextX
                }
                x = nextX
            }
            if (firstNonZero >= 0) {
                if (firstNonZero < minLeft) minLeft = firstNonZero
                if (lastNonZero > maxRight) maxRight = lastNonZero
            }
        }
        return SkIRect(
            left = minLeft, top = bands[0].top,
            right = maxRight, bottom = bands[bands.size - 1].bottom,
        )
    }
}
