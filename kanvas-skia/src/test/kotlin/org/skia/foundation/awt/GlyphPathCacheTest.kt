package org.skia.foundation.awt

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkPaint
import org.skia.tools.ToolUtils

/**
 * T5 — verifies the glyph-path cache memoizes outline lookups across
 * repeated `drawString` / `SkFont.getPath` calls, and that the
 * sub-pixel positioning toggle (`SkFont.isSubpixel`) changes glyph
 * placement at fractional source coords.
 *
 * Cache observability is internal-only — these tests live in the
 * `org.skia.foundation.awt` package to access [GlyphPathCache.hitCount]
 * / [missCount] / [size]. Public API contracts unchanged.
 */
class GlyphPathCacheTest {

    private fun freshTypeface(family: String = "sans-serif"): AwtTypeface {
        // Each test gets a fresh typeface for clean stats. We re-route
        // through LiberationFontMgr so the typeface is the canonical
        // Liberation Sans Regular — but the cache lives on the
        // typeface instance, so an isolated instance is what we want.
        // Cast is safe because LiberationFontMgr returns AwtTypeface.
        val tf = LiberationFontMgr.matchFamilyStyle(family, SkFontStyle.Normal()) as AwtTypeface
        // Other tests may have populated the singleton's cache. Reset
        // stats so this test sees only its own activity. We can't clear
        // the entries (other tests may run concurrently in some
        // configs), so we just reset the counters and accept that the
        // first lookup of a given glyph might be a hit (already cached
        // from a prior test's run).
        tf.glyphPathCache.resetStats()
        return tf
    }

    // ---------- cache mechanics --------------------------------------------

    @Test
    fun `cache populates on first miss and reuses on second lookup`() {
        val cache = GlyphPathCache()
        var buildCount = 0
        val build = {
            buildCount++
            org.skia.foundation.SkPathBuilder().moveTo(0f, 0f).lineTo(10f, 10f).detach()
        }
        val a = cache.getOrBuild(42, 12f, 1f, 0f, build)
        val b = cache.getOrBuild(42, 12f, 1f, 0f, build)
        assertSame(a, b, "second lookup must return the cached instance")
        assertEquals(1, buildCount, "build callback must run exactly once")
        assertEquals(1, cache.hitCount)
        assertEquals(1, cache.missCount)
        assertEquals(1, cache.size)
    }

    @Test
    fun `cache distinguishes by glyphId`() {
        val cache = GlyphPathCache()
        val build = { org.skia.foundation.SkPathBuilder().detach() }
        cache.getOrBuild(1, 12f, 1f, 0f, build)
        cache.getOrBuild(2, 12f, 1f, 0f, build)
        assertEquals(2, cache.size)
        assertEquals(2, cache.missCount)
        assertEquals(0, cache.hitCount)
    }

    @Test
    fun `cache distinguishes by size scaleX skewX`() {
        val cache = GlyphPathCache()
        val build = { org.skia.foundation.SkPathBuilder().detach() }
        cache.getOrBuild(1, 12f, 1f, 0f, build)
        cache.getOrBuild(1, 24f, 1f, 0f, build)
        cache.getOrBuild(1, 12f, 0.5f, 0f, build)
        cache.getOrBuild(1, 12f, 1f, 0.25f, build)
        assertEquals(4, cache.size)
        assertEquals(4, cache.missCount)
    }

    // ---------- AwtTypeface integration ------------------------------------

    @Test
    fun `repeated drawString of same text increments cache hits not misses`() {
        val tf = freshTypeface()
        val font = SkFont(tf, 24f)
        val paint = SkPaint(0xFF000000.toInt()).apply { isAntiAlias = true }
        val bm = SkBitmap(120, 50).apply { eraseColor(0xFFFFFFFF.toInt()) }
        val canvas = SkCanvas(bm)

        canvas.drawString("Hello", 4f, 32f, font, paint)
        val missesAfterFirst = tf.glyphPathCache.missCount
        val hitsAfterFirst = tf.glyphPathCache.hitCount

        canvas.drawString("Hello", 4f, 32f, font, paint)
        val missesAfterSecond = tf.glyphPathCache.missCount
        val hitsAfterSecond = tf.glyphPathCache.hitCount

        // Second call: 5 glyphs requested ('H', 'e', 'l', 'l', 'o') —
        // all cached after the first call, so 5 hits, 0 new misses.
        assertEquals(missesAfterFirst, missesAfterSecond, "no new misses on repeat")
        assertEquals(hitsAfterFirst + 5, hitsAfterSecond, "5 cache hits on repeat 'Hello'")
    }

    @Test
    fun `unique glyph count drives miss count first call`() {
        val tf = freshTypeface()
        val font = SkFont(tf, 20f)
        val paint = SkPaint(0xFF000000.toInt())
        val bm = SkBitmap(200, 50).apply { eraseColor(0xFFFFFFFF.toInt()) }
        val canvas = SkCanvas(bm)

        canvas.drawString("Mississippi", 4f, 32f, font, paint)
        // 11 chars; unique glyphs: {M, i, s, p} = 4. So at most 4 misses.
        // Some chars may share glyphs across (e.g. 's' appears 4×, 'i' 4×, 'p' 2×).
        // Hits = 11 - misses (since each char triggers exactly one
        // cache lookup).
        assertEquals(
            11,
            tf.glyphPathCache.hitCount + tf.glyphPathCache.missCount,
            "11 lookups for 11-char Mississippi",
        )
        assertTrue(
            tf.glyphPathCache.missCount in 1..5,
            "misses should be ~4 unique glyphs (got ${tf.glyphPathCache.missCount})",
        )
    }

    @Test
    fun `cache reuse across drawString calls of different but overlapping strings`() {
        val tf = freshTypeface()
        val font = SkFont(tf, 18f)
        val paint = SkPaint(0xFF000000.toInt())
        val bm = SkBitmap(200, 50).apply { eraseColor(0xFFFFFFFF.toInt()) }
        val canvas = SkCanvas(bm)

        canvas.drawString("foo", 4f, 32f, font, paint)
        val missesAfter1 = tf.glyphPathCache.missCount

        canvas.drawString("bar", 4f, 32f, font, paint)  // disjoint
        val missesAfter2 = tf.glyphPathCache.missCount

        canvas.drawString("foo", 4f, 32f, font, paint)  // reuses 1st
        val missesAfter3 = tf.glyphPathCache.missCount

        canvas.drawString("foob", 4f, 32f, font, paint)  // 'b' from 2nd; 'f','o' from 1st
        val missesAfter4 = tf.glyphPathCache.missCount

        assertTrue(missesAfter2 > missesAfter1, "disjoint string adds misses")
        assertEquals(missesAfter2, missesAfter3, "repeated 'foo' adds zero misses")
        assertEquals(missesAfter2, missesAfter4, "'foob' shares all glyphs with prior runs")
    }

    @Test
    fun `cache hit produces same SkPath instance across drawString and getPath`() {
        val tf = freshTypeface()
        // Render via drawString to populate.
        val font = SkFont(tf, 30f)
        val bm = SkBitmap(50, 50).apply { eraseColor(0xFFFFFFFF.toInt()) }
        SkCanvas(bm).drawString("X", 4f, 38f, font, SkPaint(0xFF000000.toInt()))

        // Look up via SkFont.getPath — should hit the same cache.
        val glyphs = ShortArray(1)
        font.unicharsToGlyphs(intArrayOf('X'.code), 1, glyphs)
        val pathA = font.getPath(glyphs[0].toInt() and 0xFFFF)
        val pathB = font.getPath(glyphs[0].toInt() and 0xFFFF)
        assertSame(pathA, pathB, "two getPath lookups return the same cached SkPath")
    }

    // ---------- subpixel positioning ---------------------------------------

    @Test
    fun `isSubpixel false snaps fractional origins to integer producing different pixels than true`() {
        val typeface = freshTypeface()
        val paint = SkPaint(0xFF000000.toInt()).apply { isAntiAlias = true }

        fun render(subpixel: Boolean): IntArray {
            val bm = SkBitmap(80, 50).apply { eraseColor(0xFFFFFFFF.toInt()) }
            val font = SkFont(typeface, 24f).apply { isSubpixel = subpixel }
            // Fractional origin so the snap is observable: 4.4 → snap-> 4
            // when subpixel=false; preserved at 4.4 when true. AA
            // coverage at glyph edges should differ as a result.
            SkCanvas(bm).drawString("M", 4.4f, 35.7f, font, paint)
            return bm.pixels.copyOf()
        }
        val onPixels = render(true)
        val offPixels = render(false)

        var diffs = 0
        for (i in onPixels.indices) if (onPixels[i] != offPixels[i]) diffs++
        // The two renders should differ on at least a handful of AA
        // edge pixels. We don't pin an exact count because AWT's AA
        // policy is platform-influenced; a small non-zero diff is the
        // proof point.
        assertTrue(diffs > 0, "subpixel on vs off must produce different pixel patterns at fractional origin")
    }

    @Test
    fun `isSubpixel false at integer origin produces same pixels as isSubpixel true`() {
        // When the origin is already integer-aligned, the snap is a
        // no-op — the two configurations must emit identical pixels.
        val typeface = freshTypeface()
        val paint = SkPaint(0xFF000000.toInt()).apply { isAntiAlias = true }

        fun render(subpixel: Boolean): IntArray {
            val bm = SkBitmap(80, 50).apply { eraseColor(0xFFFFFFFF.toInt()) }
            val font = SkFont(typeface, 24f).apply { isSubpixel = subpixel }
            SkCanvas(bm).drawString("M", 5f, 35f, font, paint)  // integer origin
            return bm.pixels.copyOf()
        }
        assertEquals(render(true).toList(), render(false).toList())
    }
}
