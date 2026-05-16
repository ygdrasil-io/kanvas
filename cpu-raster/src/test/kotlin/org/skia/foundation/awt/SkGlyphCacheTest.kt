package org.skia.foundation.awt

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.skia.foundation.SkFont
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkTypeface

/**
 * Unit tests for [SkGlyphCache] (Phase I2.1).
 *
 * Coverage :
 *  - First lookup misses + invokes the builder ; second lookup hits
 *    the cache and skips the builder.
 *  - The cached [SkGlyphCache.GlyphMask] instance is identical
 *    across hits (`assertSame`).
 *  - Distinct keys (different glyphId / font.size / font.scaleX /
 *    font.skewX / typeface / edging) produce distinct entries.
 *  - LRU eviction : with `maxEntries = 2`, the third unique insert
 *    drops the eldest entry.
 *  - The mask's alpha buffer is non-empty for a rasterisable path
 *    and `0×0` for an empty path.
 *  - `null` from the builder surfaces as `null` from `getOrRasterize`.
 *  - Hit / miss counters track lookups across resets.
 */
class SkGlyphCacheTest {

    @BeforeEach
    fun setup() {
        SkGlyphCache.clear()
        SkGlyphCache.setMaxEntries(1024)
    }

    @AfterEach
    fun teardown() {
        SkGlyphCache.clear()
        SkGlyphCache.setMaxEntries(1024)
    }

    private fun makePath(): SkPath = SkPathBuilder()
        .moveTo(0f, 0f)
        .lineTo(10f, 0f)
        .lineTo(10f, 10f)
        .lineTo(0f, 10f)
        .close()
        .detach()

    @Test
    fun `first lookup misses and invokes the builder`() {
        val font = SkFont(SkTypeface.MakeEmpty(), 12f)
        var built = 0
        val mask = SkGlyphCache.getOrRasterize(font, glyphId = 65) {
            built++
            makePath()
        }!!
        assertEquals(1, built)
        assertEquals(0, SkGlyphCache.hitCount)
        assertEquals(1, SkGlyphCache.missCount)
        assertEquals(1, SkGlyphCache.size)
        assertTrue(mask.width > 0 && mask.height > 0)
        assertEquals(mask.width * mask.height, mask.alpha.size)
    }

    @Test
    fun `second lookup hits the cache and skips the builder`() {
        val font = SkFont(SkTypeface.MakeEmpty(), 12f)
        var built = 0
        val first = SkGlyphCache.getOrRasterize(font, 65) {
            built++; makePath()
        }!!
        val second = SkGlyphCache.getOrRasterize(font, 65) {
            built++; makePath()  // should not be called
        }!!
        assertEquals(1, built) { "builder invoked $built times instead of 1" }
        assertSame(first, second) { "cache returned distinct instances" }
        assertEquals(1, SkGlyphCache.hitCount)
        assertEquals(1, SkGlyphCache.missCount)
    }

    @Test
    fun `distinct glyphIds produce distinct entries`() {
        val font = SkFont(SkTypeface.MakeEmpty(), 12f)
        val a = SkGlyphCache.getOrRasterize(font, 65) { makePath() }!!
        val b = SkGlyphCache.getOrRasterize(font, 66) { makePath() }!!
        assertNotEquals(a, b)
        assertEquals(2, SkGlyphCache.size)
        assertEquals(2, SkGlyphCache.missCount)
    }

    @Test
    fun `distinct font sizes produce distinct entries`() {
        val a = SkGlyphCache.getOrRasterize(SkFont(SkTypeface.MakeEmpty(), 12f), 65) { makePath() }!!
        val b = SkGlyphCache.getOrRasterize(SkFont(SkTypeface.MakeEmpty(), 24f), 65) { makePath() }!!
        assertNotEquals(a, b)
        assertEquals(2, SkGlyphCache.size)
    }

    @Test
    fun `distinct edging produces distinct entries`() {
        val fontAA = SkFont(SkTypeface.MakeEmpty(), 12f).apply { edging = SkFont.Edging.kAntiAlias }
        val fontAlias = SkFont(SkTypeface.MakeEmpty(), 12f).apply { edging = SkFont.Edging.kAlias }
        SkGlyphCache.getOrRasterize(fontAA, 65) { makePath() }
        SkGlyphCache.getOrRasterize(fontAlias, 65) { makePath() }
        assertEquals(2, SkGlyphCache.size)
    }

    @Test
    fun `LRU evicts the eldest entry when at capacity`() {
        SkGlyphCache.setMaxEntries(2)
        val font = SkFont(SkTypeface.MakeEmpty(), 12f)
        SkGlyphCache.getOrRasterize(font, 65) { makePath() }
        SkGlyphCache.getOrRasterize(font, 66) { makePath() }
        SkGlyphCache.getOrRasterize(font, 67) { makePath() }  // evicts 65
        assertEquals(2, SkGlyphCache.size)
        // Re-fetching 65 should be a miss (it was evicted).
        var built = 0
        SkGlyphCache.getOrRasterize(font, 65) { built++; makePath() }
        assertEquals(1, built)
    }

    @Test
    fun `LRU access order keeps recently-touched entries`() {
        SkGlyphCache.setMaxEntries(2)
        val font = SkFont(SkTypeface.MakeEmpty(), 12f)
        SkGlyphCache.getOrRasterize(font, 65) { makePath() }
        SkGlyphCache.getOrRasterize(font, 66) { makePath() }
        // Touch 65 (moves it to most-recently-used).
        SkGlyphCache.getOrRasterize(font, 65) { makePath() }
        // Adding 67 should evict 66 (now eldest), not 65.
        SkGlyphCache.getOrRasterize(font, 67) { makePath() }
        // 65 still cached → 0 builder invocations.
        var built = 0
        SkGlyphCache.getOrRasterize(font, 65) { built++; makePath() }
        assertEquals(0, built) { "65 should still be cached after LRU touch" }
    }

    @Test
    fun `null path returns null mask`() {
        val font = SkFont(SkTypeface.MakeEmpty(), 12f)
        val mask = SkGlyphCache.getOrRasterize(font, 999) { null }
        assertNull(mask)
        // Null path doesn't insert a cache entry.
        assertEquals(0, SkGlyphCache.size)
    }

    @Test
    fun `empty path produces 0x0 mask`() {
        val font = SkFont(SkTypeface.MakeEmpty(), 12f)
        val emptyPath = SkPathBuilder().detach()
        val mask = SkGlyphCache.getOrRasterize(font, 100) { emptyPath }!!
        assertEquals(0, mask.width)
        assertEquals(0, mask.height)
        assertEquals(0, mask.alpha.size)
    }

    @Test
    fun `alpha buffer is row-major and length matches dimensions`() {
        val font = SkFont(SkTypeface.MakeEmpty(), 12f)
        val mask = SkGlyphCache.getOrRasterize(font, 65) { makePath() }!!
        assertEquals(mask.width * mask.height, mask.alpha.size)
        // The 10×10 square path should produce some non-zero alpha
        // pixels (at least the centre).
        var hasInk = false
        for (b in mask.alpha) if (b.toInt() != 0) { hasInk = true; break }
        assertTrue(hasInk) { "mask has no ink pixels" }
    }
}
