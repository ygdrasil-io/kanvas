package org.skia.testing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.kanvas.codec.Codec
import org.skia.foundation.SkBitmap
import java.io.File

/**
 * Self-tests for the run-time tooling itself (`compareBitmapsDetailed`,
 * `DiffImage`, `TestReport`). These never touch the reference bundle so
 * they can't be coupled to colour-profile decisions made elsewhere.
 */
class TestToolingTest {

    @Test
    fun `compareBitmapsDetailed reports 100 percent on identical bitmaps`() {
        val a = SkBitmap(8, 8).apply { eraseColor(SK_ColorWHITE) }
        val b = SkBitmap(8, 8).apply { eraseColor(SK_ColorWHITE) }
        val c = TestUtils.compareBitmapsDetailed(a, b, tolerance = 0)
        assertEquals(100.0, c.similarity)
        assertEquals(64, c.totalPixels)
        assertEquals(64, c.matchingPixels)
        assertEquals(0, c.mismatchingPixels)
        assertEquals(0, c.maxChannelDiff.max())
    }

    @Test
    fun `compareBitmapsDetailed measures max and mean diff on mismatching pixels`() {
        val a = SkBitmap(2, 1).apply {
            setPixel(0, 0, SK_ColorWHITE)   // (255,255,255,255)
            setPixel(1, 0, SK_ColorWHITE)
        }
        val b = SkBitmap(2, 1).apply {
            setPixel(0, 0, SK_ColorBLACK)   // diff (0,255,255,255)
            setPixel(1, 0, SK_ColorRED)     // diff (0,0,255,255)
        }
        val c = TestUtils.compareBitmapsDetailed(a, b, tolerance = 0)
        assertEquals(0.0, c.similarity)
        assertEquals(2, c.totalPixels)
        assertEquals(0, c.matchingPixels)
        assertEquals(255, c.maxChannelDiff.max())
        assertEquals(0, c.maxChannelDiff.a)        // both fully opaque
        assertEquals(255, c.maxChannelDiff.r)
        assertEquals(255, c.maxChannelDiff.g)
        assertEquals(255, c.maxChannelDiff.b)
        // Mean across the 2 mismatching pixels:
        // R: (255 + 0) / 2 = 127, G: (255 + 255) / 2 = 255, B: same.
        assertEquals(0, c.meanMismatchDiff.a)
        assertEquals(127, c.meanMismatchDiff.r)
        assertEquals(255, c.meanMismatchDiff.g)
        assertEquals(255, c.meanMismatchDiff.b)
    }

    @Test
    fun `compareBitmapsDetailed honours tolerance per channel`() {
        val a = SkBitmap(1, 1).apply { setPixel(0, 0, 0xFF646464.toInt()) }   // R=G=B=100
        val b = SkBitmap(1, 1).apply { setPixel(0, 0, 0xFF6E6E6E.toInt()) }   // R=G=B=110
        // Diff = 10 per RGB channel. tolerance=10 should match; 9 should miss.
        assertEquals(100.0, TestUtils.compareBitmapsDetailed(a, b, tolerance = 10).similarity)
        assertEquals(0.0, TestUtils.compareBitmapsDetailed(a, b, tolerance = 9).similarity)
    }

    @Test
    fun `compareBitmapsDetailed returns 0 percent when sizes mismatch`() {
        val a = SkBitmap(4, 4).apply { eraseColor(SK_ColorWHITE) }
        val b = SkBitmap(8, 8).apply { eraseColor(SK_ColorWHITE) }
        val c = TestUtils.compareBitmapsDetailed(a, b, tolerance = 255)
        assertEquals(0.0, c.similarity)
        assertEquals(0, c.totalPixels)
    }

    @Test
    fun `saveComparisonImage produces a triptych of the expected dimensions`() {
        val w = 16; val h = 8
        val rendered = SkBitmap(w, h).apply { eraseColor(SK_ColorRED) }
        val reference = SkBitmap(w, h).apply { eraseColor(SK_ColorWHITE) }
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference, tolerance = 0)
        val name = "tooling-self-test"
        TestUtils.saveComparisonImage(rendered, reference, comparison, name)
        val out = File("build/debug-images/$name-comparison.png")
        assertTrue(out.exists(), "Triptych PNG was not written at ${out.absolutePath}")
        val img = Codec.MakeFromData(out.readBytes())?.getImage()?.first
        assertNotNull(img)
        // Layout: 3 panels of width `w` separated by 4-pixel gutters,
        // plus a 16-pixel label band on top.
        assertEquals(3 * w + 2 * 4, img!!.width)
        assertEquals(h + 16, img.height)
    }

    @Test
    fun `TestReport flush writes a markdown table covering all recorded entries`() {
        try {
            TestReport.recordScore("__TestToolingFixtureAlpha", 87.5, previous = 86.2)
            TestReport.recordDetailed(
                "__TestToolingFixtureBeta",
                BitmapComparison(
                    similarity = 99.42,
                    totalPixels = 10_000,
                    matchingPixels = 9_942,
                    tolerance = 32,
                    maxChannelDiff = ChannelDiff(0, 12, 5, 7),
                    meanMismatchDiff = ChannelDiff(0, 4, 2, 3),
                ),
            )
            TestReport.recordScore("__TestToolingFixtureBeta", 99.42, previous = 99.30)
            TestReport.flush()

            val report = File("test-similarity-report.md")
            assertTrue(report.exists(), "Report was not written at ${report.absolutePath}")
            val text = report.readText()
            assertTrue(text.contains("__TestToolingFixtureAlpha"))
            assertTrue(text.contains("__TestToolingFixtureBeta"))
            assertTrue(text.contains("87.50%"))
            assertTrue(text.contains("+1.30%"))            // Alpha Δ
            assertTrue(text.contains("99.42%"))
            assertTrue(text.contains("9,942 / 10,000"))    // Beta detail
            assertTrue(text.contains("0, 12, 5, 7"))       // Beta max diff
        } finally {
            // Clean up so fixtures never leak into the run-final report
            // (TestReport state is shared across the JVM lifetime).
            TestReport.remove("__TestToolingFixtureAlpha")
            TestReport.remove("__TestToolingFixtureBeta")
        }
    }
}
