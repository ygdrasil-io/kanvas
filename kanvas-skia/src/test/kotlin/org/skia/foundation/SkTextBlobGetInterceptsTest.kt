package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.tools.ToolUtils

/**
 * S7-B sprint — covers [SkTextBlob.getIntercepts] (the underline-skip
 * helper previously missing from [SkTextBlob]).
 *
 * The tests construct single-run blobs via [SkTextBlobBuilder.allocRun]
 * (uniform x-spacing) and probe the intercept output across three
 * regimes :
 *
 *  - Bands far above / below the baseline → expect zero intercepts.
 *  - Bands crossing the glyph cap-height → expect one `[entry, exit]`
 *    pair per glyph.
 *  - Stroke-aware paint → expect a slightly wider pair than the fill case.
 *
 * The `MakeEmpty` typeface returns no glyph paths, so it must yield an
 * empty intercept array — that's the third case below.
 */
class SkTextBlobGetInterceptsTest {

    /**
     * Helper — builds a single-run "ABC" blob anchored at `(x, y)` with
     * the portable AWT typeface at [textSize]pt.
     */
    private fun makeBlob(text: String, textSize: Float, x: Float, y: Float): SkTextBlob {
        val font = ToolUtils.DefaultPortableFont(textSize)
        val glyphs = font.textToGlyphs(text)
        val builder = SkTextBlobBuilder()
        val rec = builder.allocRun(font, glyphs.size, x, y)
        for (i in glyphs.indices) rec.glyphs[i] = glyphs[i]
        return builder.make()!!
    }

    @Test
    fun `getIntercepts requires bounds of size 2 minimum`() {
        val blob = makeBlob("X", 24f, 0f, 0f)
        try {
            blob.getIntercepts(FloatArray(1))
            error("expected IllegalArgumentException for bounds.size=1")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `band entirely above baseline returns no intercepts`() {
        // Glyphs sit on the baseline at y=0 ; their bbox is roughly
        // [-cap-height .. 0]. A band at [+10..+20] (well below baseline)
        // never touches them.
        val blob = makeBlob("ABC", 24f, 0f, 0f)
        val intercepts = blob.getIntercepts(floatArrayOf(10f, 20f))
        assertEquals(0, intercepts.size, "below-baseline band must not intercept")
    }

    @Test
    fun `band entirely below cap-height returns no intercepts`() {
        // Glyphs sit on baseline at y=0 ; cap is around y=-15 (24pt).
        // A band at [-100..-50] is far above the cap.
        val blob = makeBlob("ABC", 24f, 0f, 0f)
        val intercepts = blob.getIntercepts(floatArrayOf(-100f, -50f))
        assertEquals(0, intercepts.size, "above-cap band must not intercept")
    }

    @Test
    fun `band crossing cap-height returns one entry-exit pair per glyph`() {
        // Band at [-10..-5] cuts across the glyph cap area for ALL three
        // ABC glyphs at 24pt. Expect 6 floats = 3 pairs.
        val blob = makeBlob("ABC", 24f, 0f, 0f)
        val intercepts = blob.getIntercepts(floatArrayOf(-10f, -5f))
        assertEquals(6, intercepts.size, "expected 3 [entry,exit] pairs, got ${intercepts.toList()}")
        // Each pair must be ordered (entry < exit) and the pairs must
        // march left-to-right (entry_n < entry_{n+1}).
        assertTrue(intercepts[0] < intercepts[1], "pair 0 entry/exit must be ordered")
        assertTrue(intercepts[2] < intercepts[3], "pair 1 entry/exit must be ordered")
        assertTrue(intercepts[4] < intercepts[5], "pair 2 entry/exit must be ordered")
        assertTrue(intercepts[0] < intercepts[2], "pair 0 must precede pair 1")
        assertTrue(intercepts[2] < intercepts[4], "pair 1 must precede pair 2")
    }

    @Test
    fun `stroked paint widens the intercept pair beyond the fill case`() {
        val blob = makeBlob("X", 24f, 0f, 0f)
        val band = floatArrayOf(-10f, -5f)
        val fillIntercepts = blob.getIntercepts(band, paint = null)
        val strokePaint = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 4f
        }
        val strokeIntercepts = blob.getIntercepts(band, paint = strokePaint)

        assertEquals(2, fillIntercepts.size)
        assertEquals(2, strokeIntercepts.size)
        // Stroke widens by half the stroke width on each side.
        assertEquals(fillIntercepts[0] - 2f, strokeIntercepts[0], 0.001f)
        assertEquals(fillIntercepts[1] + 2f, strokeIntercepts[1], 0.001f)
    }

    @Test
    fun `bounds order is irrelevant — descending pair sorted internally`() {
        val blob = makeBlob("X", 24f, 0f, 0f)
        val ascending = blob.getIntercepts(floatArrayOf(-10f, -5f))
        val descending = blob.getIntercepts(floatArrayOf(-5f, -10f))
        assertEquals(ascending.toList(), descending.toList())
    }

    @Test
    fun `MakeEmpty typeface yields no intercepts the empty-glyph fallback`() {
        // Empty typeface returns null from getPath() ⇒ skip every glyph.
        val font = SkFont(SkTypeface.MakeEmpty(), 24f)
        val builder = SkTextBlobBuilder()
        val rec = builder.allocRun(font, 3, 0f, 0f)
        rec.glyphs[0] = 1; rec.glyphs[1] = 2; rec.glyphs[2] = 3
        val blob = builder.make()!!
        val intercepts = blob.getIntercepts(floatArrayOf(-100f, 100f))
        assertEquals(0, intercepts.size, "empty typeface must contribute no intercepts")
    }

    @Test
    fun `band crossing all glyphs yields pairs covering each glyph extent`() {
        // Wide band [-100..+100] catches every glyph regardless of baseline.
        val blob = makeBlob("ABC", 24f, 0f, 0f)
        val intercepts = blob.getIntercepts(floatArrayOf(-100f, 100f))
        assertEquals(6, intercepts.size)
        // First glyph entry should be near 0 (or slightly negative for
        // glyphs with left side bearing); last glyph exit should be
        // positive and farther right than first glyph exit.
        assertTrue(intercepts[5] > intercepts[1], "C exit must be > A exit")
    }
}
