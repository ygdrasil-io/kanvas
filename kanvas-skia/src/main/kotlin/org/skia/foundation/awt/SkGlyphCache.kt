package org.skia.foundation.awt

import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorGetA
import org.skia.foundation.SkFont
import org.skia.foundation.SkPath
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Glyph **alpha-mask** cache (Phase I2.1).
 *
 * Mirrors Skia's `SkStrike` / `SkStrikeCache` partially : caches a
 * single 8-bit alpha mask per `(typefaceId, size, scaleX, skewX,
 * glyphId, edging)` key. Subsequent lookups are O(1) HashMap hits
 * and avoid both the AWT outline call **and** the per-pixel coverage
 * rasterisation.
 *
 * Differences from upstream :
 *  - Upstream caches **bitmap masks** at multiple sub-pixel phases
 *    (kBW / kAA / kLCD) and per-axis sub-pixel buckets ; we cache one
 *    AA mask per integer-aligned origin (Phase I2.3 will add sub-
 *    pixel buckets if any GM in scope demands it).
 *  - Upstream's `SkStrikeCache::PurgeAll` and the per-strike memory
 *    budget are not modelled — our LRU is bounded only by entry
 *    count (default 1024). For our test workload (≤ 200 unique
 *    glyphs at ≤ 5 sizes) this is well within budget.
 *
 * Thread-safe via a single coarse-grained `synchronized` block.
 *
 * Lifecycle :
 *  - Lookup [getOrRasterize] : returns the cached [GlyphMask] or
 *    rasterises a fresh one via the supplied `path` callback +
 *    internal alpha-bitmap path.
 *  - Stats : [hitCount] / [missCount] / [size] for telemetry +
 *    tests.
 */
internal object SkGlyphCache {

    /**
     * One cached glyph's alpha mask + position. The mask is
     * rasterised at glyph-local origin `(0, 0)` ; the [originX] /
     * [originY] fields record the path's `floor()` bbox so callers
     * can place the mask on-screen without re-querying bounds.
     *
     * @property width pixels along X.
     * @property height pixels along Y.
     * @property originX device-space X offset of `alpha[0]` relative
     *   to the glyph's baseline origin.
     * @property originY device-space Y offset of `alpha[0]`.
     * @property alpha row-major 8-bit alpha buffer, length
     *   `width × height`. `0` = fully transparent, `255` = fully
     *   covered.
     */
    internal data class GlyphMask(
        val width: Int,
        val height: Int,
        val originX: Int,
        val originY: Int,
        val alpha: ByteArray,
    ) {
        // data-class default equals/hashCode would be content-based on
        // ByteArray which is expensive ; identity-equals matches our
        // usage (cache returns the same instance for the same key).
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = System.identityHashCode(this)
    }

    /**
     * Cache key — exact-bit equality on float dimensions (size /
     * scaleX / skewX) since GM tests render at deterministic sizes.
     * `typefaceId` is the [System.identityHashCode] of the typeface
     * — valid identity for our use (typefaces are not value types).
     */
    private data class Key(
        val typefaceId: Int,
        val sizeBits: Int,
        val scaleXBits: Int,
        val skewXBits: Int,
        val glyphId: Int,
        val edgingOrdinal: Int,
    )

    private const val DEFAULT_MAX_ENTRIES: Int = 1024

    // LinkedHashMap with access-order = true gives us free LRU
    // semantics : `get` moves the entry to the tail, and we evict
    // the head when the cache is full.
    private val cache: LinkedHashMap<Key, GlyphMask> =
        object : LinkedHashMap<Key, GlyphMask>(64, 0.75f, /*accessOrder=*/ true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, GlyphMask>?): Boolean =
                size > maxEntries
        }

    @Volatile private var maxEntries: Int = DEFAULT_MAX_ENTRIES
    @Volatile private var _hits: Int = 0
    @Volatile private var _misses: Int = 0

    /** Number of cache hits since last [resetStats]. */
    internal val hitCount: Int get() = _hits

    /** Number of cache misses (= rasterisations performed). */
    internal val missCount: Int get() = _misses

    /** Number of currently-cached masks. */
    internal val size: Int get() = synchronized(cache) { cache.size }

    /**
     * Look up or rasterise the glyph mask for the supplied
     * `(font, glyphId)`. The `path` callback returns the glyph's
     * outline at glyph-local origin `(0, 0)` ; the cache rasterises
     * that path into an 8-bit alpha bitmap once, keyed by the full
     * tuple `(typefaceId, size, scaleX, skewX, glyphId, edging)`.
     *
     * Subsequent calls for the same key return the cached mask
     * without invoking `path`. The caller is responsible for
     * compositing the mask onto the destination bitmap (origin :
     * `(deviceX + mask.originX, deviceY + mask.originY)`).
     */
    internal fun getOrRasterize(
        font: SkFont,
        glyphId: Int,
        path: () -> SkPath?,
    ): GlyphMask? {
        val typefaceId = System.identityHashCode(font.typeface)
        val key = Key(
            typefaceId,
            font.size.toRawBits(),
            font.scaleX.toRawBits(),
            font.skewX.toRawBits(),
            glyphId,
            font.edging.ordinal,
        )

        synchronized(cache) {
            val cached = cache[key]
            if (cached != null) {
                _hits++
                return cached
            }
        }

        // Rasterise outside the lock to avoid blocking other glyph
        // lookups during the (potentially expensive) AWT outline +
        // bitmap-fill sequence.
        val outlinePath = path() ?: return null
        val mask = rasterisePathToMask(outlinePath, font.edging)

        return synchronized(cache) {
            val racingEntry = cache[key]
            if (racingEntry != null) {
                _hits++
                racingEntry
            } else {
                cache[key] = mask
                _misses++
                mask
            }
        }
    }

    /**
     * Internal : rasterise a glyph-local path into an 8-bit alpha
     * mask. The mask's bbox is the integer-rounded bounding box of
     * the path's tight bounds, padded by 1 px on every side for AA
     * edge spread.
     */
    private fun rasterisePathToMask(path: SkPath, edging: SkFont.Edging): GlyphMask {
        val b = path.computeBounds()
        // Empty path → 0×0 mask (caller will skip blit).
        if (b.right <= b.left || b.bottom <= b.top) {
            return GlyphMask(0, 0, 0, 0, ByteArray(0))
        }
        val pad = 1
        val l = floor(b.left.toDouble()).toInt() - pad
        val t = floor(b.top.toDouble()).toInt() - pad
        val r = ceil(b.right.toDouble()).toInt() + pad
        val bo = ceil(b.bottom.toDouble()).toInt() + pad
        val w = (r - l).coerceAtLeast(1)
        val h = (bo - t).coerceAtLeast(1)

        // Raster the path into a temp 8888 bitmap with WHITE+kSrc.
        // The bitmap's CTM is shifted so the path lands at (0, 0)
        // local to the mask buffer.
        val bm = SkBitmap(w, h).also { it.eraseColor(0) }
        val canvas = org.skia.core.SkCanvas(bm)
        canvas.translate(-l.toFloat(), -t.toFloat())
        val paint = org.skia.foundation.SkPaint().apply {
            color = org.skia.foundation.SK_ColorWHITE
            isAntiAlias = (edging != SkFont.Edging.kAlias)
            style = org.skia.foundation.SkPaint.Style.kFill_Style
        }
        canvas.drawPath(path, paint)

        // Extract the alpha channel into a flat ByteArray.
        val alpha = ByteArray(w * h)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                alpha[row + x] = SkColorGetA(bm.getPixel(x, y)).toByte()
            }
        }
        return GlyphMask(w, h, l, t, alpha)
    }

    /** Test hook : reset hit / miss counters. Cache contents preserved. */
    internal fun resetStats() {
        _hits = 0
        _misses = 0
    }

    /** Test hook : clear the cache and reset stats. */
    internal fun clear() {
        synchronized(cache) { cache.clear() }
        resetStats()
    }

    /**
     * Test hook : override the maximum number of cached entries.
     * Default is [DEFAULT_MAX_ENTRIES] ; lowering this exercises the
     * LRU eviction path. Existing entries beyond the new limit are
     * **not** evicted retroactively — the limit only applies to
     * future inserts.
     */
    internal fun setMaxEntries(limit: Int) {
        require(limit > 0) { "maxEntries must be > 0, got $limit" }
        maxEntries = limit
    }
}
