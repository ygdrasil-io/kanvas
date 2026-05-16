package org.skia.foundation.awt

import org.skia.foundation.SkPath

/**
 * **NOTE D'IMPLÉMENTATION** — Ce fichier expose un **cache d'outlines
 * de glyphes** (`SkPath`) pour le pipeline texte de `:kanvas-skia`.
 * Le `build` callback fourni par les appelants (typiquement
 * [AwtTypeface]) résout les outlines via **`java.awt.Font` +
 * `GlyphVector.getOutline`**, pas via le moteur de fontes natif Skia.
 *
 * Conséquences identiques aux autres `Awt*.kt` :
 *  - Les outlines mémoïsés portent le drift métriques d'AWT (1-2 ulps
 *    sur AA edges vs FreeType).
 *  - `font.isSubpixel` n'est pas une dimension du cache : la
 *    sub-pixel positioning se fait **après** le lookup cache (sur
 *    `(x, y)` final). Le cache stocke les paths à origine `(0, 0)` ;
 *    seul l'`addPathOffset` du builder applique la translation finale.
 *
 * Si on remplace AWT par FreeType+JNI ou par un rasterizer custom,
 * **seul ce fichier (et ses pairs `Awt*.kt`) doit changer** —
 * l'API publique reste figée sur la signature Skia.
 *
 * Mirrors Skia's `SkStrikeCache` partiellement : Skia cache des
 * **bitmap masks** par `(strike-spec, glyphID)` ; on cache des `SkPath`
 * parce que notre pipeline est path-fill, pas mask-blit. La granularité
 * est plus grossière (un seul path par `(glyph, size, scaleX, skewX)`
 * indépendamment du sub-pixel offset, alors que Skia distingue jusqu'à
 * 4 phases sub-pixel par glyphe). C'est suffisant pour le scope de
 * correctness ; T5+ pourrait ajouter le sub-pixel bucketing si une
 * GM le réclame.
 */
internal class GlyphPathCache {

    /**
     * Cache key — exact-bit equality on the float dimensions
     * (size / scaleX / skewX) since GMs typically render at
     * deterministic sizes. Uses [Float.toRawBits] so `+0.0f` and
     * `-0.0f` hash differently (Skia's strike cache does the same;
     * the distinction matters for negative scales).
     */
    private data class Key(
        val glyphId: Int,
        val sizeBits: Int,
        val scaleXBits: Int,
        val skewXBits: Int,
    )

    private val cache: HashMap<Key, SkPath> = HashMap()

    @Volatile private var _hits: Int = 0
    @Volatile private var _misses: Int = 0

    /** Number of cache hits since process start (or last [resetStats]). */
    internal val hitCount: Int get() = _hits

    /** Number of cache misses (newly built entries) since process start. */
    internal val missCount: Int get() = _misses

    /** Number of distinct cached glyph outlines. */
    internal val size: Int get() = synchronized(cache) { cache.size }

    /**
     * Lookup or compute the [SkPath] outline for a single glyph at the
     * given size / scaleX / skewX. The path is in glyph-local coords
     * (origin `(0, 0)` — no baseline offset, no advance offset).
     *
     * `build` is invoked at most once per unique key. The result is
     * memoised; subsequent lookups are O(1) hash hits.
     *
     * Thread-safe via a coarse `synchronized` block. Contention is
     * unlikely in our usage (GM tests are single-threaded), so we
     * trade peak parallelism for simpler correctness.
     */
    internal fun getOrBuild(
        glyphId: Int,
        size: Float,
        scaleX: Float,
        skewX: Float,
        build: () -> SkPath,
    ): SkPath {
        val key = Key(glyphId, size.toRawBits(), scaleX.toRawBits(), skewX.toRawBits())
        synchronized(cache) {
            val cached = cache[key]
            if (cached != null) {
                _hits++
                return cached
            }
        }
        // Build outside the lock to avoid holding it through AWT calls
        // (createGlyphVector + getOutline can be expensive at large sizes).
        val built = build()
        return synchronized(cache) {
            // Re-check in case another thread populated between our two
            // critical sections. Use the new entry if absent; otherwise
            // discard `built` and return the racing entry (canonical).
            val existing = cache[key]
            if (existing != null) {
                _hits++
                existing
            } else {
                cache[key] = built
                _misses++
                built
            }
        }
    }

    /** Test-only: clear stats (cache contents preserved). */
    internal fun resetStats() {
        _hits = 0; _misses = 0
    }

    /** Test-only: clear the entire cache. */
    internal fun clear() {
        synchronized(cache) { cache.clear() }
        resetStats()
    }
}
