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
}
