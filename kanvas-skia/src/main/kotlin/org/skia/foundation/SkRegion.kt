package org.skia.foundation

import org.skia.math.SkIRect

/**
 * Iso-aligned port of Skia's
 * [`SkRegion`](https://github.com/google/skia/blob/main/include/core/SkRegion.h).
 *
 * A region is a 2D set of pixel coordinates expressible as a finite
 * union of axis-aligned, half-open `[left, right) × [top, bottom)`
 * integer rectangles. Used to represent complex (concave / multi-piece)
 * device clips that the bbox-only [SkIRect] clip cannot capture.
 *
 * **Internal representation** : a sorted list of horizontal *bands*.
 * A band covers a Y-range `[top, bottom)` and contains a sorted, even-
 * length `IntArray` of `(left, right)` interval pairs along X. Bands
 * are sorted by `top` ascending ; adjacent bands never share their
 * Y-extent and never carry identical interval lists (those would be
 * merged into one taller band — *canonical form*).
 *
 * Skia's run-encoding adds packed sentinel values for fast linear
 * traversal ; we use plain `IntArray`s for clarity. The asymptotic
 * complexity matches.
 *
 * **Phase I3.1.a (this slice)** ships :
 *  - data model + canonicalisation (bands list ; rect / empty fast
 *    paths) ;
 *  - constructors, [setEmpty], [setRect], [set] ;
 *  - state queries [isEmpty] / [isRect] / [isComplex] / [getBounds] /
 *    [computeRegionComplexity] ;
 *  - point + rect [contains] queries ;
 *  - [Iterator] over the rectangle decomposition ;
 *  - [Op] enum (set-op opcodes — declared here, consumed in I3.1.b).
 *
 * **Phase I3.1.b (next)** will add :
 *  - [setPath] (rasterise an [SkPath] to bands at integer scanline
 *    granularity) ;
 *  - [op] set operations (union / intersect / difference / xor /
 *    reverse-difference / replace) via scanline merging.
 */
public class SkRegion private constructor(
    private var bands: List<Band>,
    private var fBounds: SkIRect,
    private var fIsRectFastPath: Boolean,
) {

    /**
     * One horizontal scanline band of the region.
     *
     * @property top    inclusive Y origin of the band.
     * @property bottom exclusive Y end ; `bottom > top` always.
     * @property xs     even-length sorted array of `(left, right)`
     *                  interval pairs along X. Each pair satisfies
     *                  `left < right` ; pairs are disjoint and sorted
     *                  ascending.
     */
    internal data class Band(val top: Int, val bottom: Int, val xs: IntArray) {
        // Identity equals/hashCode — bands are mutated by replacement
        // ; structural equality on IntArray would be expensive and is
        // unused. Set-op helpers compare via [contentEquals] inline.
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = System.identityHashCode(this)

        internal fun isSingleInterval(): Boolean = xs.size == 2

        internal fun rectAt(intervalIdx: Int): SkIRect = SkIRect(
            xs[intervalIdx * 2], top,
            xs[intervalIdx * 2 + 1], bottom,
        )
    }

    /** Mirrors Skia's `SkRegion::Op`. Set-op opcodes consumed by [op]. */
    public enum class Op { kDifference, kIntersect, kUnion, kXOR, kReverseDifference, kReplace }

    /** Empty region. */
    public constructor() : this(emptyList(), SkIRect(0, 0, 0, 0), false)

    /** Rectangular region. Empty [rect] yields the empty region. */
    public constructor(rect: SkIRect) : this(emptyList(), SkIRect(0, 0, 0, 0), false) {
        setRect(rect)
    }

    /** Copy ctor. */
    public constructor(other: SkRegion) : this(emptyList(), SkIRect(0, 0, 0, 0), false) {
        set(other)
    }

    // ─── State mutators ────────────────────────────────────────────

    /** Reset to the empty region. Returns `false` (mirrors Skia's contract — empty regions return false from setters). */
    public fun setEmpty(): Boolean {
        bands = emptyList()
        fBounds = SkIRect(0, 0, 0, 0)
        fIsRectFastPath = false
        return false
    }

    /**
     * Replace this region with the rectangle [r] (or empty if [r] is
     * empty / inverted). Returns `true` if the resulting region is
     * non-empty.
     */
    public fun setRect(r: SkIRect): Boolean {
        if (r.isEmpty) return setEmpty()
        bands = listOf(Band(r.top, r.bottom, intArrayOf(r.left, r.right)))
        fBounds = SkIRect(r.left, r.top, r.right, r.bottom)
        fIsRectFastPath = true
        return true
    }

    /**
     * Replace this region with [other]'s contents (deep copy of band
     * list — band [Band.xs] arrays are shared by reference, but bands
     * are themselves immutable from a callers' perspective so that's
     * safe).
     */
    public fun set(other: SkRegion): Boolean {
        bands = other.bands.toList()
        fBounds = SkIRect(other.fBounds.left, other.fBounds.top, other.fBounds.right, other.fBounds.bottom)
        fIsRectFastPath = other.fIsRectFastPath
        return !isEmpty()
    }

    // ─── State queries ─────────────────────────────────────────────

    /** `true` if the region covers no pixels. */
    public fun isEmpty(): Boolean = bands.isEmpty()

    /** `true` if the region is a single non-empty rectangle. */
    public fun isRect(): Boolean = fIsRectFastPath || (bands.size == 1 && bands[0].isSingleInterval())

    /** `true` if the region is non-empty and not a single rectangle. */
    public fun isComplex(): Boolean = !isEmpty() && !isRect()

    /**
     * Return the axis-aligned union bounds of the region. Empty for
     * the empty region. Returns a *defensive copy* — callers may
     * mutate it without corrupting this region's internal state.
     */
    public fun getBounds(): SkIRect = SkIRect(fBounds.left, fBounds.top, fBounds.right, fBounds.bottom)

    /**
     * Number of axis-aligned rectangles needed to express this region
     * exactly — the count of `(band, interval)` pairs. `0` for empty,
     * `1` for a single rectangle, larger for complex shapes.
     *
     * Mirrors Skia's `SkRegion::computeRegionComplexity()`.
     */
    public fun computeRegionComplexity(): Int = bands.sumOf { it.xs.size / 2 }

    // ─── Containment ───────────────────────────────────────────────

    /**
     * Half-open containment (`left ≤ x < right`, `top ≤ y < bottom`)
     * — matches Skia's `SkRegion::contains(x, y)`. Empty regions
     * never contain any pixel.
     */
    public fun contains(x: Int, y: Int): Boolean {
        if (bands.isEmpty()) return false
        if (y < fBounds.top || y >= fBounds.bottom) return false
        if (x < fBounds.left || x >= fBounds.right) return false
        // Linear search across bands ; O(log n) with bisection if
        // perf becomes a bottleneck — for our 213-GM workload the
        // typical band count is < 20 and the linear walk is faster.
        for (band in bands) {
            if (y < band.top) return false
            if (y >= band.bottom) continue
            // Walk x intervals.
            var i = 0
            while (i < band.xs.size) {
                val l = band.xs[i]
                val r = band.xs[i + 1]
                if (x < l) return false
                if (x < r) return true
                i += 2
            }
            return false
        }
        return false
    }

    /**
     * Returns `true` iff every pixel of [r] (under half-open
     * semantics) lies within this region. Empty [r] returns `false`
     * (matches Skia's `SkRegion::contains(SkIRect)` empty handling).
     */
    public fun contains(r: SkIRect): Boolean {
        if (r.isEmpty || isEmpty()) return false
        if (!fBounds.containsNoEmptyCheck(r)) return false
        if (isRect()) return true  // single-band region's bounds == region
        // For each scanline of [r], the bands must collectively cover
        // [r.left, r.right). We require that some single band fully
        // spans [r.top, r.bottom) AND has one interval that covers
        // [r.left, r.right) — the canonical form guarantees that any
        // contiguous Y-range of a region that's a single rectangle
        // collapses into one band, so anything more complex than that
        // means partial coverage at some Y.
        for (band in bands) {
            if (band.bottom <= r.top) continue
            if (band.top > r.top) return false
            // band.top ≤ r.top < band.bottom — this band must cover
            // [r.left, r.right) AND extend down to r.bottom.
            if (band.bottom < r.bottom) return false
            return bandCoversInterval(band, r.left, r.right)
        }
        return false
    }

    private fun bandCoversInterval(band: Band, left: Int, right: Int): Boolean {
        var i = 0
        while (i < band.xs.size) {
            val l = band.xs[i]
            val r = band.xs[i + 1]
            if (l <= left && r >= right) return true
            if (l >= right) return false
            i += 2
        }
        return false
    }

    // ─── Iterator ──────────────────────────────────────────────────

    /**
     * Mirrors Skia's `SkRegion::Iterator`. Walks the rectangle
     * decomposition of [rgn] in band-major, then interval-major
     * order. Snapshots the bands list at construction time — mutating
     * the source region during iteration is undefined.
     */
    public class Iterator(rgn: SkRegion) {
        private val bands: List<Band> = rgn.bands
        private var bandIdx: Int = 0
        private var intervalIdx: Int = 0

        /** `true` once the iterator has emitted every rectangle. */
        public fun done(): Boolean = bandIdx >= bands.size

        /**
         * Current rectangle. Throws [NoSuchElementException] if
         * [done].
         *
         * Returns a fresh [SkIRect] each call ; callers may mutate it
         * without affecting the iterator or the source region.
         */
        public fun rect(): SkIRect {
            if (done()) throw NoSuchElementException("SkRegion.Iterator exhausted")
            return bands[bandIdx].rectAt(intervalIdx)
        }

        /** Advance to the next rectangle. Idempotent at end-of-region. */
        public fun next() {
            if (done()) return
            intervalIdx++
            if (intervalIdx * 2 >= bands[bandIdx].xs.size) {
                bandIdx++
                intervalIdx = 0
            }
        }
    }

    // ─── Set ops (deferred to I3.1.b — stubs) ──────────────────────

    /**
     * Phase I3.1.b — combine `this` with [rgn] under [op]. Currently
     * unimplemented ; throws on call.
     */
    public fun op(@Suppress("UNUSED_PARAMETER") rgn: SkRegion, @Suppress("UNUSED_PARAMETER") op: Op): Boolean {
        throw UnsupportedOperationException("SkRegion.op(SkRegion) lands in Phase I3.1.b")
    }

    /**
     * Phase I3.1.b — combine `this` with [rect] under [op]. Currently
     * unimplemented ; throws on call.
     */
    public fun op(@Suppress("UNUSED_PARAMETER") rect: SkIRect, @Suppress("UNUSED_PARAMETER") op: Op): Boolean {
        throw UnsupportedOperationException("SkRegion.op(SkIRect) lands in Phase I3.1.b")
    }

    /**
     * Phase I3.1.b — rasterise [path] (intersected with [clip]) into
     * the band representation. Currently unimplemented ; throws on
     * call.
     */
    public fun setPath(
        @Suppress("UNUSED_PARAMETER") path: SkPath,
        @Suppress("UNUSED_PARAMETER") clip: SkRegion,
    ): Boolean {
        throw UnsupportedOperationException("SkRegion.setPath lands in Phase I3.1.b")
    }
}
