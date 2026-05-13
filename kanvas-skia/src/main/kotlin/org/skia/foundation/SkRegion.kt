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

    /**
     * Shift every pixel of this region by `(dx, dy)`. Mirrors Skia's
     * `SkRegion::translate(int dx, int dy)`. Empty regions are
     * unaffected. The canonical form is preserved : `(top, bottom)`
     * shifts by `dy` ; every `(left, right)` interval shifts by `dx` ;
     * the relative order of bands and intervals is invariant.
     */
    public fun translate(dx: Int, dy: Int) {
        if (isEmpty()) return
        if (dx == 0 && dy == 0) return
        bands = bands.map { b ->
            val xs = IntArray(b.xs.size) { i -> b.xs[i] + dx }
            Band(b.top + dy, b.bottom + dy, xs)
        }
        fBounds = SkIRect(
            fBounds.left + dx, fBounds.top + dy,
            fBounds.right + dx, fBounds.bottom + dy,
        )
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
     *
     * R2.18 — adds [rgn], [rewind], [reset] to match upstream's full
     * `SkRegion::Iterator` surface.
     */
    public class Iterator {
        private var fRgn: SkRegion? = null
        private var bands: List<Band> = emptyList()
        private var bandIdx: Int = 0
        private var intervalIdx: Int = 0

        /** Default ctor — produces an empty / [done] iterator. Reset before use. */
        public constructor() {
            this.fRgn = null
            this.bands = emptyList()
        }

        public constructor(rgn: SkRegion) {
            reset(rgn)
        }

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

        /** The region this iterator walks, or `null` if default-constructed. */
        public fun rgn(): SkRegion? = fRgn

        /** Reset to start ; returns `true` iff the source region was non-null. */
        public fun rewind(): Boolean {
            bandIdx = 0
            intervalIdx = 0
            return fRgn != null
        }

        /** Point this iterator at [region] and rewind. */
        public fun reset(region: SkRegion) {
            fRgn = region
            bands = region.bands
            bandIdx = 0
            intervalIdx = 0
        }
    }

    /**
     * Mirrors Skia's `SkRegion::Cliperator`. Walks the rectangle
     * decomposition of [region], clipped against [clip]. Each emitted
     * [rect] is the intersection of the underlying region rect and
     * [clip] ; rectangles that don't intersect [clip] are skipped.
     */
    public class Cliperator(region: SkRegion, private val clip: SkIRect) {
        private val iter = Iterator(region)
        private var current: SkIRect? = null

        init { advanceToValid() }

        public fun done(): Boolean = current == null

        /** Current rectangle (clipped). Undefined if [done]. */
        public fun rect(): SkIRect = current
            ?: throw NoSuchElementException("SkRegion.Cliperator exhausted")

        public fun next() {
            if (current == null) return
            iter.next()
            advanceToValid()
        }

        private fun advanceToValid() {
            while (!iter.done()) {
                val r = iter.rect()
                val l = maxOf(r.left, clip.left)
                val t = maxOf(r.top, clip.top)
                val ri = minOf(r.right, clip.right)
                val b = minOf(r.bottom, clip.bottom)
                if (l < ri && t < b) {
                    current = SkIRect(l, t, ri, b)
                    return
                }
                iter.next()
            }
            current = null
        }
    }

    /**
     * Mirrors Skia's `SkRegion::Spanerator`. For a single horizontal
     * scanline [y], walks the X spans of [region] clipped to
     * `[left, right)`. Calling [next] populates `(outLeft, outRight)`
     * with the next intersected span and returns `true`, or returns
     * `false` once exhausted.
     */
    public class Spanerator(region: SkRegion, y: Int, left: Int, right: Int) {
        private val spans: IntArray
        private var cursor: Int = 0
        private val clipLeft: Int = left
        private val clipRight: Int = right

        init {
            spans = if (left >= right) EMPTY else region.findScanlineSpans(y)
        }

        /**
         * Returns the next clipped span. The single-int-array return
         * cell `(outLeft, outRight)` is preferred in Skia ; we encode
         * the result as `(left, right)` via the [Span] data class for
         * Kotlin ergonomics. Returns `null` once exhausted.
         */
        public fun next(): Span? {
            while (cursor + 1 < spans.size) {
                val l = maxOf(spans[cursor], clipLeft)
                val r = minOf(spans[cursor + 1], clipRight)
                cursor += 2
                if (l < r) return Span(l, r)
            }
            return null
        }

        /**
         * Mirrors upstream's `bool next(int* left, int* right)` signature
         * — writes the next span into the two-element array [outLR]
         * (`[0] = left`, `[1] = right`) and returns `true`, or returns
         * `false` once exhausted. `outLR` may be `null` to advance
         * without reading the result.
         */
        public fun next(outLR: IntArray?): Boolean {
            val s = next() ?: return false
            if (outLR != null && outLR.size >= 2) {
                outLR[0] = s.left
                outLR[1] = s.right
            }
            return true
        }

        public data class Span(val left: Int, val right: Int)

        private companion object {
            val EMPTY: IntArray = IntArray(0)
        }
    }

    /**
     * Trace the outline of this region into [builder] as a sequence
     * of `addRect(...)` calls — one rectangle per region piece.
     *
     * Returns `true` iff the region is non-empty (matches upstream's
     * `SkRegion::addBoundaryPath(SkPathBuilder*)` return contract).
     * Mirror of `include/core/SkRegion.h:190`.
     */
    public fun addBoundaryPath(builder: SkPathBuilder): Boolean {
        if (isEmpty()) return false
        val it = Iterator(this)
        while (!it.done()) {
            val r = it.rect()
            builder.addRect(org.skia.math.SkRect.Make(r))
            it.next()
        }
        return true
    }

    /**
     * Return the boundary of this region as a freshly built [SkPath].
     * Empty region yields an empty path. Mirror of upstream
     * `SkRegion::getBoundaryPath()` (`include/core/SkRegion.h:195`).
     */
    public fun getBoundaryPath(): SkPath {
        val b = SkPathBuilder()
        addBoundaryPath(b)
        return b.detach()
    }

    // ─── Set ops (Phase I3.1.b) ────────────────────────────────────

    /**
     * Combine `this` with [rect] under [op]. Wraps [rect] into a
     * temporary single-band region and forwards to [op] of [SkRegion].
     */
    public fun op(rect: SkIRect, op: Op): Boolean = op(SkRegion(rect), op)

    /**
     * Combine `this` with [rgn] under [op] via scanline merging :
     *  1. Walk `this`'s and [rgn]'s band lists in Y order ;
     *  2. for each Y interval where the active band configuration is
     *     constant, run a 1D X-axis interval merge under [op] (see
     *     [mergeXIntervals]) ;
     *  3. coalesce adjacent output bands with identical interval lists
     *     into a single taller band (canonical form).
     *
     * Special cases :
     *  - [Op.kReplace] sets `this` to a copy of [rgn] regardless of
     *    `this`'s prior contents (mirrors Skia's "replace" opcode) ;
     *  - if either operand is empty the appropriate identity / zero
     *    rule applies (e.g. `∅ ∪ B = B`, `∅ ∩ B = ∅`).
     *
     * Returns `true` iff the resulting region is non-empty.
     */
    public fun op(rgn: SkRegion, op: Op): Boolean {
        if (op == Op.kReplace) {
            return set(rgn)
        }
        // Identity / zero shortcuts when either operand is empty.
        if (this.isEmpty() && rgn.isEmpty()) return setEmpty()
        if (this.isEmpty()) return when (op) {
            Op.kUnion, Op.kReverseDifference, Op.kXOR -> set(rgn)
            Op.kIntersect, Op.kDifference -> setEmpty()
            Op.kReplace -> error("kReplace handled above")  // satisfies exhaustive when
        }
        if (rgn.isEmpty()) return when (op) {
            Op.kUnion, Op.kDifference, Op.kXOR -> !isEmpty()
            Op.kIntersect, Op.kReverseDifference -> setEmpty()
            Op.kReplace -> error("kReplace handled above")
        }

        val merged = scanlineMerge(this.bands, rgn.bands, op)
        return assignFromBands(merged)
    }

    /**
     * Replace this region's state with a freshly-merged band list,
     * recomputing [fBounds] / [fIsRectFastPath] / [bands] from
     * scratch. Returns `true` iff non-empty.
     */
    private fun assignFromBands(merged: List<Band>): Boolean {
        if (merged.isEmpty()) return setEmpty()
        bands = merged
        fIsRectFastPath = merged.size == 1 && merged[0].isSingleInterval()
        // Recompute bounds : top = first band's top, bottom = last
        // band's bottom, left = min of all bands' left-most X, right
        // = max of all bands' right-most X.
        var minL = Int.MAX_VALUE
        var maxR = Int.MIN_VALUE
        for (b in merged) {
            val l = b.xs[0]
            val r = b.xs[b.xs.size - 1]
            if (l < minL) minL = l
            if (r > maxR) maxR = r
        }
        fBounds = SkIRect(minL, merged[0].top, maxR, merged[merged.size - 1].bottom)
        return true
    }

    /**
     * Phase I3.1.c — rasterise [path] into integer-aligned bands and
     * intersect the result with [clip]. The [path]'s
     * [SkPathFillType] drives the inside / outside test :
     *  - `kWinding` / `kEvenOdd` produce the path's interior region ;
     *  - `kInverseWinding` / `kInverseEvenOdd` produce `clip - interior`.
     *
     * Algorithm :
     *  1. Walk [path]'s verb stream, flattening curves to line
     *     segments via adaptive De Casteljau (see [flattenQuadEdge] /
     *     [flattenCubicEdge]) — exclude horizontal segments (zero
     *     contribution to scanline crossings).
     *  2. For each integer scanline `y` in the path's Y range, sample
     *     at the pixel center `y + 0.5` :
     *     - find every edge it crosses ;
     *     - sort crossings by X ;
     *     - run an even-odd / non-zero winding sweep to produce a
     *       sequence of `(left, right)` X intervals ;
     *     - round to integer pixels via `floor(x + 0.5)` and merge
     *       any zero-width / abutting intervals.
     *  3. Group consecutive scanlines with identical X-interval
     *     arrays into a single band (canonical form).
     *  4. Intersect / difference the resulting bands with [clip] via
     *     [scanlineMerge].
     *
     * Returns `true` iff the resulting region is non-empty.
     */
    public fun setPath(path: SkPath, clip: SkRegion): Boolean {
        if (clip.isEmpty()) return setEmpty()
        if (!path.isFinite()) return setEmpty()
        val isInverse = path.fillType.isInverse()
        if (path.isEmpty()) {
            return if (isInverse) set(clip) else setEmpty()
        }

        val edges = flattenPathToEdges(path)
        if (edges.isEmpty()) {
            return if (isInverse) set(clip) else setEmpty()
        }

        // Y range from edges, intersected with the clip's Y range.
        var yMinF = Float.POSITIVE_INFINITY
        var yMaxF = Float.NEGATIVE_INFINITY
        for (e in edges) {
            val yLow = minOf(e.y0, e.y1)
            val yHigh = maxOf(e.y0, e.y1)
            if (yLow < yMinF) yMinF = yLow
            if (yHigh > yMaxF) yMaxF = yHigh
        }
        val cb = clip.getBounds()
        val yTop = maxOf(kotlin.math.floor(yMinF.toDouble()).toInt(), cb.top)
        val yBot = minOf(kotlin.math.ceil(yMaxF.toDouble()).toInt(), cb.bottom)
        if (yTop >= yBot) {
            return if (isInverse) set(clip) else setEmpty()
        }

        val isEvenOdd = path.fillType.isEvenOdd()

        // Build scanline-aggregated bands (consecutive Ys with the
        // same X intervals collapse into one band).
        val pathBands = ArrayList<Band>(yBot - yTop)
        var pendingXs: IntArray? = null
        var pendingTop = 0
        for (y in yTop until yBot) {
            val intervals = computeScanlineIntervals(edges, y + 0.5f, isEvenOdd)
            if (intervals.isEmpty()) {
                if (pendingXs != null) {
                    pathBands.add(Band(pendingTop, y, pendingXs))
                    pendingXs = null
                }
                continue
            }
            if (pendingXs == null) {
                pendingXs = intervals
                pendingTop = y
            } else if (!intervals.contentEquals(pendingXs)) {
                pathBands.add(Band(pendingTop, y, pendingXs))
                pendingXs = intervals
                pendingTop = y
            }
        }
        if (pendingXs != null) {
            pathBands.add(Band(pendingTop, yBot, pendingXs))
        }

        if (pathBands.isEmpty()) {
            return if (isInverse) set(clip) else setEmpty()
        }

        // Intersect (or difference, for inverse fills) with the clip's
        // bands via the existing scanlineMerge helper.
        val finalBands = if (isInverse) {
            // Inverse fill : `clip - pathInterior`.
            scanlineMerge(clip.bands, pathBands, Op.kDifference)
        } else {
            scanlineMerge(pathBands, clip.bands, Op.kIntersect)
        }
        return assignFromBands(finalBands)
    }

    /** Phase I3.1.c — line-segment edge for path rasterisation. */
    private class PathEdge(val x0: Float, val y0: Float, val x1: Float, val y1: Float)

    /**
     * Walk [path]'s verb stream, flattening Bézier curves into line
     * segments via adaptive De Casteljau. Horizontal segments are
     * dropped (they contribute zero to scanline crossings). Returns
     * the resulting edge list.
     */
    private fun flattenPathToEdges(path: SkPath): List<PathEdge> {
        val out = ArrayList<PathEdge>(path.verbs.size * 2)
        var px = 0f; var py = 0f
        var cx = 0f; var cy = 0f
        var hasContour = false
        var coordIdx = 0
        var weightIdx = 0
        val coords = path.coords
        val weights = path.conicWeights
        for (verb in path.verbs) {
            when (verb) {
                SkPath.Verb.kMove -> {
                    if (hasContour) addEdgeIfNonHorizontal(out, px, py, cx, cy)
                    px = coords[coordIdx++]
                    py = coords[coordIdx++]
                    cx = px; cy = py
                    hasContour = true
                }
                SkPath.Verb.kLine -> {
                    val nx = coords[coordIdx++]
                    val ny = coords[coordIdx++]
                    addEdgeIfNonHorizontal(out, px, py, nx, ny)
                    px = nx; py = ny
                }
                SkPath.Verb.kQuad -> {
                    val x1 = coords[coordIdx++]; val y1 = coords[coordIdx++]
                    val x2 = coords[coordIdx++]; val y2 = coords[coordIdx++]
                    flattenQuadEdge(out, px, py, x1, y1, x2, y2, depth = 0)
                    px = x2; py = y2
                }
                SkPath.Verb.kConic -> {
                    val x1 = coords[coordIdx++]; val y1 = coords[coordIdx++]
                    val x2 = coords[coordIdx++]; val y2 = coords[coordIdx++]
                    weightIdx++  // conic weight unused — flattened as a quad approximation
                    flattenQuadEdge(out, px, py, x1, y1, x2, y2, depth = 0)
                    px = x2; py = y2
                }
                SkPath.Verb.kCubic -> {
                    val x1 = coords[coordIdx++]; val y1 = coords[coordIdx++]
                    val x2 = coords[coordIdx++]; val y2 = coords[coordIdx++]
                    val x3 = coords[coordIdx++]; val y3 = coords[coordIdx++]
                    flattenCubicEdge(out, px, py, x1, y1, x2, y2, x3, y3, depth = 0)
                    px = x3; py = y3
                }
                SkPath.Verb.kClose -> {
                    if (hasContour) {
                        addEdgeIfNonHorizontal(out, px, py, cx, cy)
                        px = cx; py = cy
                        hasContour = false
                    }
                }
            }
        }
        if (hasContour) addEdgeIfNonHorizontal(out, px, py, cx, cy)
        return out
    }

    private fun addEdgeIfNonHorizontal(
        out: MutableList<PathEdge>, x0: Float, y0: Float, x1: Float, y1: Float,
    ) {
        if (y0 != y1) out.add(PathEdge(x0, y0, x1, y1))
    }

    /** Adaptive quad flattener — same algorithm as `SkBitmapDevice.flattenQuad`. */
    private fun flattenQuadEdge(
        out: MutableList<PathEdge>,
        x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float, depth: Int,
    ) {
        if (depth >= PATH_FLATTEN_MAX_DEPTH || quadIsFlat(x0, y0, x1, y1, x2, y2)) {
            addEdgeIfNonHorizontal(out, x0, y0, x2, y2)
            return
        }
        val m01x = (x0 + x1) * 0.5f; val m01y = (y0 + y1) * 0.5f
        val m12x = (x1 + x2) * 0.5f; val m12y = (y1 + y2) * 0.5f
        val mx = (m01x + m12x) * 0.5f; val my = (m01y + m12y) * 0.5f
        flattenQuadEdge(out, x0, y0, m01x, m01y, mx, my, depth + 1)
        flattenQuadEdge(out, mx, my, m12x, m12y, x2, y2, depth + 1)
    }

    private fun quadIsFlat(
        x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float,
    ): Boolean {
        val dx = x2 - x0
        val dy = y2 - y0
        val chord2 = dx * dx + dy * dy
        if (chord2 < 1e-12f) return true
        val cross = (x1 - x0) * dy - (y1 - y0) * dx
        return (cross * cross) <= PATH_FLATTEN_TOL_SQ * chord2
    }

    /** Adaptive cubic flattener — same algorithm as `SkBitmapDevice.flattenCubic`. */
    private fun flattenCubicEdge(
        out: MutableList<PathEdge>,
        x0: Float, y0: Float, x1: Float, y1: Float,
        x2: Float, y2: Float, x3: Float, y3: Float, depth: Int,
    ) {
        if (depth >= PATH_FLATTEN_MAX_DEPTH || cubicIsFlat(x0, y0, x1, y1, x2, y2, x3, y3)) {
            addEdgeIfNonHorizontal(out, x0, y0, x3, y3)
            return
        }
        val ax = (x0 + x1) * 0.5f; val ay = (y0 + y1) * 0.5f
        val bx = (x1 + x2) * 0.5f; val by = (y1 + y2) * 0.5f
        val cx = (x2 + x3) * 0.5f; val cy = (y2 + y3) * 0.5f
        val dx2 = (ax + bx) * 0.5f; val dy2 = (ay + by) * 0.5f
        val ex2 = (bx + cx) * 0.5f; val ey2 = (by + cy) * 0.5f
        val fx2 = (dx2 + ex2) * 0.5f; val fy2 = (dy2 + ey2) * 0.5f
        flattenCubicEdge(out, x0, y0, ax, ay, dx2, dy2, fx2, fy2, depth + 1)
        flattenCubicEdge(out, fx2, fy2, ex2, ey2, cx, cy, x3, y3, depth + 1)
    }

    private fun cubicIsFlat(
        x0: Float, y0: Float, x1: Float, y1: Float,
        x2: Float, y2: Float, x3: Float, y3: Float,
    ): Boolean {
        val dx = x3 - x0
        val dy = y3 - y0
        val chord2 = dx * dx + dy * dy
        if (chord2 < 1e-12f) return true
        val cross1 = (x1 - x0) * dy - (y1 - y0) * dx
        val cross2 = (x2 - x0) * dy - (y2 - y0) * dx
        val maxCross2 = maxOf(cross1 * cross1, cross2 * cross2)
        return maxCross2 <= PATH_FLATTEN_TOL_SQ * chord2
    }

    /**
     * Phase I3.1.c — compute the scanline X intervals for [edges] at
     * the given pixel-center [y]. Returns an even-length sorted
     * `IntArray` of `(left, right)` integer pixel pairs, or
     * [EMPTY_INT_ARRAY] when no part of the path covers the scanline.
     *
     * Crossings are paired under the requested winding rule
     * ([evenOdd] = `true` → even-odd ; `false` → non-zero). Boundary
     * X coordinates round to integer pixels via `floor(x + 0.5f)`,
     * matching the pixel-center inclusion semantics of upstream's
     * `SkRegion::setPath`.
     */
    private fun computeScanlineIntervals(
        edges: List<PathEdge>, y: Float, evenOdd: Boolean,
    ): IntArray {
        // Collect crossings with sign indicating winding direction.
        val xs = ArrayList<Float>()
        val dirs = ArrayList<Int>()
        for (e in edges) {
            val yLow = minOf(e.y0, e.y1)
            val yHigh = maxOf(e.y0, e.y1)
            // Half-open Y range [yLow, yHigh) prevents double-counting
            // at shared vertices.
            if (y < yLow || y >= yHigh) continue
            val t = (y - e.y0) / (e.y1 - e.y0)
            xs.add(e.x0 + t * (e.x1 - e.x0))
            dirs.add(if (e.y1 > e.y0) +1 else -1)
        }
        if (xs.isEmpty()) return EMPTY_INT_ARRAY
        // Sort by X (carry dirs along).
        val n = xs.size
        val idx = IntArray(n) { it }
        // Insertion sort — short scanlines (< ~50 crossings typical)
        // beat O(n log n) here.
        for (i in 1 until n) {
            val pi = idx[i]
            val px = xs[pi]
            var j = i - 1
            while (j >= 0 && xs[idx[j]] > px) {
                idx[j + 1] = idx[j]
                j--
            }
            idx[j + 1] = pi
        }
        val raw = ArrayList<Int>()
        var winding = 0
        var prevInside = false
        for (i in 0 until n) {
            val k = idx[i]
            winding += dirs[k]
            val inside = if (evenOdd) (winding and 1) != 0 else winding != 0
            if (inside != prevInside) {
                raw.add(kotlin.math.floor((xs[k] + 0.5f).toDouble()).toInt())
                prevInside = inside
            }
        }
        // Drop zero-width intervals + coalesce abutting ones.
        val cleaned = ArrayList<Int>(raw.size)
        var i = 0
        while (i < raw.size - 1) {
            val l = raw[i]
            val r = raw[i + 1]
            if (l < r) {
                if (cleaned.isNotEmpty() && cleaned[cleaned.size - 1] == l) {
                    cleaned[cleaned.size - 1] = r
                } else {
                    cleaned.add(l)
                    cleaned.add(r)
                }
            }
            i += 2
        }
        return if (cleaned.isEmpty()) EMPTY_INT_ARRAY else IntArray(cleaned.size) { cleaned[it] }
    }

    /**
     * Phase I3.1.b — band-major scanline merge driver. Walks both
     * [a] and [b] band lists in Y-order, partitioning the union
     * Y range into maximal sub-intervals where the active band
     * configuration (which of A/B is active, and which bands they
     * point to) is constant. For each sub-interval it merges the
     * active X intervals under [op] (see [mergeXIntervals]) and
     * appends a band when the result is non-empty.
     *
     * Output is **canonical** : adjacent bands with identical
     * `xs` arrays are coalesced into a single taller band on the fly
     * (see [appendBandCoalesce]).
     *
     * Time complexity : `O(|a| + |b|)` band steps × `O(|aXs| +
     * |bXs|)` X-merge per band = linear in total interval count.
     */
    private fun scanlineMerge(a: List<Band>, b: List<Band>, op: Op): List<Band> {
        val out = mutableListOf<Band>()
        var ai = 0
        var bi = 0
        var yCursor = minOf(a[0].top, b[0].top)

        while (ai < a.size || bi < b.size) {
            // Skip any bands whose bottom we've already passed (this
            // happens when one side has a Y-gap shorter than the
            // other's band).
            while (ai < a.size && yCursor >= a[ai].bottom) ai++
            while (bi < b.size && yCursor >= b[bi].bottom) bi++
            if (ai >= a.size && bi >= b.size) break

            val aBand = a.getOrNull(ai)
            val bBand = b.getOrNull(bi)

            // If yCursor lags behind the next active band (a gap in
            // both), jump to the earliest band start.
            val aActive = aBand != null && yCursor >= aBand.top
            val bActive = bBand != null && yCursor >= bBand.top
            if (!aActive && !bActive) {
                val aTop = aBand?.top ?: Int.MAX_VALUE
                val bTop = bBand?.top ?: Int.MAX_VALUE
                yCursor = minOf(aTop, bTop)
                continue
            }

            // Determine the next event (yBot) — the closest forthcoming
            // band entry / exit ≥ yCursor + 1. Candidates :
            //  - active band exits (its bottom) ;
            //  - inactive band entries (its top).
            var yBot = Int.MAX_VALUE
            if (aBand != null) yBot = minOf(yBot, if (aActive) aBand.bottom else aBand.top)
            if (bBand != null) yBot = minOf(yBot, if (bActive) bBand.bottom else bBand.top)

            // Smart-cast through `aActive` / `bActive` (both imply
            // their band is non-null) is unfortunately not seen by the
            // Kotlin compiler across the prior `if (aActive && yCursor < aBand.top)`
            // continue ; explicit null check satisfies the analysis.
            val aXs = aBand?.takeIf { aActive }?.xs ?: EMPTY_INT_ARRAY
            val bXs = bBand?.takeIf { bActive }?.xs ?: EMPTY_INT_ARRAY
            val merged = mergeXIntervals(aXs, bXs, op)

            if (merged.isNotEmpty()) {
                appendBandCoalesce(out, yCursor, yBot, merged)
            }
            yCursor = yBot
        }
        return out
    }

    /**
     * 1D X-axis interval merge under [op]. Walks both [aXs] and [bXs]
     * left-to-right tracking `(inA, inB)` flags. Whenever the
     * "inside output" predicate flips, an X coordinate is emitted to
     * the output ; the result is therefore even-length and disjoint.
     */
    private fun mergeXIntervals(aXs: IntArray, bXs: IntArray, op: Op): IntArray {
        if (aXs.isEmpty() && bXs.isEmpty()) return EMPTY_INT_ARRAY
        val out = ArrayList<Int>(aXs.size + bXs.size)
        var ai = 0
        var bi = 0
        var inA = false
        var inB = false
        var inOut = false
        while (ai < aXs.size || bi < bXs.size) {
            val nextA = if (ai < aXs.size) aXs[ai] else Int.MAX_VALUE
            val nextB = if (bi < bXs.size) bXs[bi] else Int.MAX_VALUE
            val x = minOf(nextA, nextB)
            while (ai < aXs.size && aXs[ai] == x) {
                inA = !inA
                ai++
            }
            while (bi < bXs.size && bXs[bi] == x) {
                inB = !inB
                bi++
            }
            val newInOut = combine(inA, inB, op)
            if (newInOut != inOut) {
                out.add(x)
                inOut = newInOut
            }
        }
        return if (out.isEmpty()) EMPTY_INT_ARRAY else IntArray(out.size) { out[it] }
    }

    /**
     * Boolean predicate driver for [mergeXIntervals] — given the
     * `inA` / `inB` flags after consuming all events at a given X,
     * report whether the output region is "inside" at this X.
     *
     * [Op.kReplace] is short-circuited at [op] entry so it shouldn't
     * land here ; we still handle it for completeness (returns `inB`).
     */
    private fun combine(inA: Boolean, inB: Boolean, op: Op): Boolean = when (op) {
        Op.kUnion -> inA || inB
        Op.kIntersect -> inA && inB
        Op.kDifference -> inA && !inB
        Op.kReverseDifference -> !inA && inB
        Op.kXOR -> inA != inB
        Op.kReplace -> inB
    }

    /**
     * Append a band `[top, bottom)` with X intervals [xs] to [out],
     * coalescing with the previous band when its `bottom == top` and
     * its `xs` is content-equal — preserves canonical form on the fly.
     *
     * `xs` is captured by reference (the caller must not mutate it
     * after this call).
     */
    private fun appendBandCoalesce(out: MutableList<Band>, top: Int, bottom: Int, xs: IntArray) {
        if (out.isNotEmpty()) {
            val prev = out[out.size - 1]
            if (prev.bottom == top && prev.xs.contentEquals(xs)) {
                out[out.size - 1] = Band(prev.top, bottom, prev.xs)
                return
            }
        }
        out.add(Band(top, bottom, xs))
    }

    /**
     * Helper for [Spanerator] — collect the X spans of [region]
     * at scanline `y` (half-open, `[top, bottom)`). Returns an
     * even-length sorted `IntArray` of `(left, right)` pairs, or
     * an empty array if no band covers `y`.
     */
    internal fun findScanlineSpans(y: Int): IntArray {
        if (isEmpty()) return IntArray(0)
        if (y < fBounds.top || y >= fBounds.bottom) return IntArray(0)
        for (band in bands) {
            if (y < band.top) return IntArray(0)
            if (y >= band.bottom) continue
            return band.xs.copyOf()
        }
        return IntArray(0)
    }

    private companion object {
        private val EMPTY_INT_ARRAY: IntArray = IntArray(0)

        /** Recursion guard for the De Casteljau flattener — matches Skia's `SkRasterClip`. */
        private const val PATH_FLATTEN_MAX_DEPTH: Int = 14

        /**
         * Squared chord-tolerance for [quadIsFlat] / [cubicIsFlat]. A
         * curve is considered flat when the perpendicular distance
         * from each control point to the chord is `≤ 0.5 px` —
         * sufficient to keep the integer-rounded scanline output
         * stable at typical region sizes.
         */
        private const val PATH_FLATTEN_TOL_SQ: Float = 0.25f
    }
}
