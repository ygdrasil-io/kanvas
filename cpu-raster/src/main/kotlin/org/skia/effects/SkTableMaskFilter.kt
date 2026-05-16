package org.skia.effects

import org.skia.foundation.SkMaskFilter
import kotlin.math.pow

/**
 * Mirrors Skia's
 * [`SkTableMaskFilter`](https://github.com/google/skia/blob/main/include/effects/SkTableMaskFilter.h)
 * — a [SkMaskFilter] that applies a 256-entry lookup table to every
 * alpha byte of the rasterised coverage mask.
 *
 * The filter is "table[α]" — pixels with input alpha `α` are mapped to
 * the byte at `table[α]`. The table is therefore a generic
 * sample-by-sample alpha remap, with helper builders that produce
 * common transforms :
 *
 *  - [MakeGammaTable] — `table[α] = pow(α / 255, gamma) * 255`. Used to
 *    apply a perceptual gamma to the rasteriser's coverage (e.g.
 *    `γ = 1/2.2` for sRGB-correct anti-aliasing on naive devices).
 *  - [MakeClipTable] — linear ramp clamped to `[min, max]` then
 *    rescaled into `[0, 255]`. Inputs below `min` map to `0`, inputs
 *    above `max` map to `255`, and inputs in `[min, max]` are mapped
 *    proportionally. Useful for "hard threshold + soft transition"
 *    coverage shaping.
 *
 * **Status** : upstream marks this filter as deprecated and slated for
 * removal, but the `:kanvas-skia` GM corpus still references the class
 * (most notably the `lookupMaskFilter` GM). The Kotlin port mirrors the
 * pre-deprecation public surface so those GMs keep compiling. The
 * implementation walks the mask in a single pass, allocating a fresh
 * buffer — the filter never widens the coverage area, so it reports
 * [margin] = 0.
 */
public object SkTableMaskFilter {

    /** Number of entries in a mask-remap table (`0..255` alpha indices). */
    public const val kTableSize: Int = 256

    /**
     * Fill [table] (must be size 256) with `pow(i / 255, gamma) * 255`.
     * Mirrors Skia's `SkTableMaskFilter::MakeGammaTable`.
     *
     * `gamma < 0` is treated as `0` (identity → constant `0`). `gamma = 1`
     * produces the identity ramp `table[i] = i`.
     */
    public fun MakeGammaTable(table: ByteArray, gamma: Float) {
        require(table.size == kTableSize) {
            "SkTableMaskFilter table must be $kTableSize entries, got ${table.size}"
        }
        val g = if (gamma < 0f || !gamma.isFinite()) 0f else gamma
        for (i in 0 until kTableSize) {
            val v = (i / 255f).toDouble().pow(g.toDouble()) * 255.0 + 0.5
            table[i] = v.toInt().coerceIn(0, 255).toByte()
        }
    }

    /**
     * Fill [table] (must be size 256) with a clipping ramp : inputs
     * `< min` map to `0`, inputs `> max` map to `255`, inputs in
     * `[min, max]` are remapped linearly. Mirrors Skia's
     * `SkTableMaskFilter::MakeClipTable`. If `min >= max`, every entry
     * `< min` collapses to `0` and every entry `>= min` to `255`.
     */
    public fun MakeClipTable(table: ByteArray, min: Int, max: Int) {
        require(table.size == kTableSize) {
            "SkTableMaskFilter table must be $kTableSize entries, got ${table.size}"
        }
        val lo = (min and 0xFF)
        val hi = (max and 0xFF)
        for (i in 0 until kTableSize) {
            val v: Int = when {
                i <= lo -> 0
                i >= hi -> 255
                else -> {
                    val span = (hi - lo).coerceAtLeast(1)
                    (((i - lo).toLong() * 255L + (span / 2)) / span).toInt().coerceIn(0, 255)
                }
            }
            table[i] = v.toByte()
        }
    }

    /**
     * Mirrors Skia's `SkTableMaskFilter::Create(const uint8_t table[256])`.
     * Returns a [SkMaskFilter] that maps each alpha byte through [table].
     *
     * @param table 256-entry lookup table. The array is copied — caller may
     *   mutate it freely after the call.
     * @throws IllegalArgumentException if `table.size != 256`.
     */
    public fun Create(table: ByteArray): SkMaskFilter {
        require(table.size == kTableSize) {
            "SkTableMaskFilter expects $kTableSize-entry table, got ${table.size}"
        }
        return TableImpl(table.copyOf())
    }

    /** Convenience : create a gamma filter directly. */
    public fun CreateGamma(gamma: Float): SkMaskFilter {
        val t = ByteArray(kTableSize)
        MakeGammaTable(t, gamma)
        return TableImpl(t)
    }

    /** Convenience : create a clip filter directly. */
    public fun CreateClip(min: Int, max: Int): SkMaskFilter {
        val t = ByteArray(kTableSize)
        MakeClipTable(t, min, max)
        return TableImpl(t)
    }

    private class TableImpl(private val table: ByteArray) : SkMaskFilter() {
        override fun margin(): Int = 0

        override fun filterMask(src: ByteArray, w: Int, h: Int): ByteArray {
            require(src.size == w * h) { "src.size (${src.size}) != $w × $h" }
            val out = ByteArray(src.size)
            for (i in src.indices) {
                val a = src[i].toInt() and 0xFF
                out[i] = table[a]
            }
            return out
        }
    }
}
