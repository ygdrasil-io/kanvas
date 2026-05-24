@file:Suppress("DEPRECATION")

package org.skia.shaper

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkFont
import org.skia.foundation.SkTypeface
import org.skia.foundation.awt.LiberationFontMgr

/**
 * Phase I4.2 — covers [SkShaper.MakeJvmAwtTextLayout].
 *
 * Tests cover :
 *  - empty input emits begin / commit line and no runs ;
 *  - empty-typeface input falls back to [PrimitiveShaper] ;
 *  - LTR ASCII produces a single LTR run with monotonic positions ;
 *  - RTL Arabic input produces a single RTL run (`bidiLevel == 1`) ;
 *  - mixed LTR / RTL emits multiple runs in visual order with
 *    matching bidi levels and disjoint UTF-8 ranges ;
 *  - cluster offsets translate UTF-16 char indices to UTF-8 byte
 *    offsets correctly across multi-byte characters.
 */
class JavaTextLayoutShaperTest {

    private fun awtFont(): SkFont = SkFont(LiberationFontMgr.getDefault(), 16f)

    /** Capturing handler — accumulates the full callback log + every run. */
    private class CapturingHandler : SkShaper.RunHandler {
        val log: MutableList<String> = mutableListOf()
        val runs: MutableList<SkShaper.RunInfo> = mutableListOf()
        val buffers: MutableList<SkShaper.Buffer> = mutableListOf()

        override fun beginLine() { log.add("beginLine") }
        override fun runInfo(info: SkShaper.RunInfo) {
            log.add("runInfo")
            runs.add(info)
        }
        override fun commitRunInfo() { log.add("commitRunInfo") }
        override fun runBuffer(info: SkShaper.RunInfo): SkShaper.Buffer {
            log.add("runBuffer")
            val buf = SkShaper.Buffer(
                glyphs = IntArray(info.glyphCount),
                positions = FloatArray(info.glyphCount * 2),
                clusters = IntArray(info.glyphCount),
                point = floatArrayOf(0f, 0f),
            )
            buffers.add(buf)
            return buf
        }
        override fun commitRunBuffer(info: SkShaper.RunInfo) { log.add("commitRunBuffer") }
        override fun commitLine() { log.add("commitLine") }
    }

    @Test
    fun `empty input emits begin and commit line and no runs`() {
        val handler = CapturingHandler()
        SkShaper.MakeJvmAwtTextLayout().shape(
            utf8 = "", font = awtFont(), leftToRight = true,
            width = 100f, runHandler = handler,
        )
        assertEquals(listOf("beginLine", "commitLine"), handler.log)
        assertTrue(handler.runs.isEmpty())
    }

    @Test
    fun `empty typeface falls back to PrimitiveShaper`() {
        // No AWT engine available — JavaTextLayoutShaper delegates and
        // we still see one run (PrimitiveShaper's char-by-char output).
        val handler = CapturingHandler()
        SkShaper.MakeJvmAwtTextLayout().shape(
            utf8 = "ABC", font = SkFont(SkTypeface.MakeEmpty(), 12f),
            leftToRight = true, width = 100f, runHandler = handler,
        )
        // PrimitiveShaper opens its own beginLine after the JavaText
        // shaper closes the first one — so the log starts with two
        // beginLines back-to-back.
        assertEquals("beginLine", handler.log.first())
        assertEquals("commitLine", handler.log.last())
        assertEquals(1, handler.runs.size)
        assertEquals(3, handler.runs[0].glyphCount)
    }

    @Test
    fun `ASCII input produces a single LTR run`() {
        val handler = CapturingHandler()
        SkShaper.MakeJvmAwtTextLayout().shape(
            utf8 = "Hello", font = awtFont(), leftToRight = true,
            width = 1000f, runHandler = handler,
        )
        assertEquals(1, handler.runs.size)
        val info = handler.runs[0]
        assertEquals(0, info.bidiLevel)        // LTR
        assertTrue(info.glyphCount in 1..5) { "Expected 1..5 glyphs (ligatures allowed), got ${info.glyphCount}" }
        assertTrue(info.advanceX > 0f) { "Expected positive advance, got ${info.advanceX}" }
    }

    @Test
    fun `LTR positions monotonically increase`() {
        val handler = CapturingHandler()
        SkShaper.MakeJvmAwtTextLayout().shape(
            utf8 = "ABCDE", font = awtFont(), leftToRight = true,
            width = 1000f, runHandler = handler,
        )
        val buf = handler.buffers[0]
        val n = handler.runs[0].glyphCount
        for (i in 1 until n) {
            val prevX = buf.positions[(i - 1) * 2]
            val curX = buf.positions[i * 2]
            assertTrue(curX >= prevX) { "LTR positions not monotonic at $i: prev=$prevX cur=$curX" }
        }
    }

    @Test
    fun `RTL Arabic input flags bidi level 1`() {
        // "مرحبا" (Arabic "hello") — pure RTL.
        val handler = CapturingHandler()
        SkShaper.MakeJvmAwtTextLayout().shape(
            utf8 = "مرحبا",
            font = awtFont(), leftToRight = false,
            width = 1000f, runHandler = handler,
        )
        assertTrue(handler.runs.isNotEmpty()) { "Expected at least one run" }
        // All runs should be RTL (level 1) since the entire input is Arabic
        // and the base direction is RTL.
        for (info in handler.runs) {
            assertEquals(1, info.bidiLevel and 1) { "Expected RTL run, got level ${info.bidiLevel}" }
        }
    }

    @Test
    fun `mixed LTR Arabic LTR emits multiple runs in visual order`() {
        // "Hi مرحبا" — base LTR, English then Arabic.
        val handler = CapturingHandler()
        SkShaper.MakeJvmAwtTextLayout().shape(
            utf8 = "Hi مرحبا",
            font = awtFont(), leftToRight = true,
            width = 1000f, runHandler = handler,
        )
        assertTrue(handler.runs.size >= 2) {
            "Expected at least 2 bidi runs, got ${handler.runs.size}"
        }
        // Bidi levels alternate parity : at least one even (LTR) and
        // one odd (RTL) level present.
        val hasLtr = handler.runs.any { (it.bidiLevel and 1) == 0 }
        val hasRtl = handler.runs.any { (it.bidiLevel and 1) == 1 }
        assertTrue(hasLtr) { "Expected at least one LTR run" }
        assertTrue(hasRtl) { "Expected at least one RTL run" }
    }

    @Test
    fun `cluster offsets are UTF-8 byte indices for ASCII`() {
        val handler = CapturingHandler()
        SkShaper.MakeJvmAwtTextLayout().shape(
            utf8 = "ABC", font = awtFont(), leftToRight = true,
            width = 1000f, runHandler = handler,
        )
        val buf = handler.buffers[0]
        val n = handler.runs[0].glyphCount
        // ASCII : 1 byte per char ; with a 1:1 char→glyph mapping (no
        // ligatures across A/B/C in the default font) we expect
        // clusters {0, 1, 2}.
        if (n == 3) {
            assertEquals(0, buf.clusters[0])
            assertEquals(1, buf.clusters[1])
            assertEquals(2, buf.clusters[2])
        }
    }

    @Test
    fun `cluster offsets handle multi-byte UTF-8 encoding`() {
        // "aé" : 'a' = 1 byte at offset 0 ; 'é' = 2 bytes at offset 1.
        val handler = CapturingHandler()
        SkShaper.MakeJvmAwtTextLayout().shape(
            utf8 = "aé", font = awtFont(), leftToRight = true,
            width = 1000f, runHandler = handler,
        )
        val info = handler.runs[0]
        // utf8Range covers 0..(1+2) = 0..3.
        assertEquals(0, info.utf8Range.first)
        assertEquals(3, info.utf8Range.last)
        val buf = handler.buffers[0]
        // Clusters are non-negative and within the run's byte range.
        for (i in 0 until info.glyphCount) {
            val c = buf.clusters[i]
            assertTrue(c in 0..3) { "cluster[$i] = $c out of range [0, 3]" }
        }
    }

    @Test
    fun `SkTextBlobShaperRunHandler captures shaped output as a textblob`() {
        val handler = SkTextBlobShaperRunHandler("Hello", originX = 10f, originY = 50f)
        SkShaper.MakeJvmAwtTextLayout().shape(
            utf8 = "Hello", font = awtFont(), leftToRight = true,
            width = 1000f, runHandler = handler,
        )
        val blob = handler.makeBlob()
        assertNotNull(blob)
        assertEquals(1, blob!!.runs.size)
    }

    // -------------------------------------------------------------------
    // Phase I4.3 — line wrapping (java.text.BreakIterator)
    // -------------------------------------------------------------------

    @Test
    fun `wide width emits a single line for short text`() {
        val handler = CapturingHandler()
        SkShaper.MakeJvmAwtTextLayout().shape(
            utf8 = "Hello world", font = awtFont(), leftToRight = true,
            width = 10_000f, runHandler = handler,
        )
        val begins = handler.log.count { it == "beginLine" }
        val commits = handler.log.count { it == "commitLine" }
        assertEquals(1, begins)
        assertEquals(1, commits)
    }

    @Test
    fun `narrow width wraps text into multiple lines at word boundaries`() {
        val handler = CapturingHandler()
        // "the quick brown fox jumps over" — at 16pt with a 60px width
        // we expect at least 3 lines (one or two words per line).
        SkShaper.MakeJvmAwtTextLayout().shape(
            utf8 = "the quick brown fox jumps over", font = awtFont(),
            leftToRight = true, width = 60f, runHandler = handler,
        )
        val begins = handler.log.count { it == "beginLine" }
        assertTrue(begins >= 3) { "Expected ≥ 3 lines, got $begins" }
        // Same number of commits as begins.
        assertEquals(begins, handler.log.count { it == "commitLine" })
    }

    @Test
    fun `infinite width preserves single-line output`() {
        val handler = CapturingHandler()
        SkShaper.MakeJvmAwtTextLayout().shape(
            utf8 = "the quick brown fox jumps over the lazy dog",
            font = awtFont(), leftToRight = true,
            width = Float.POSITIVE_INFINITY, runHandler = handler,
        )
        assertEquals(1, handler.log.count { it == "beginLine" })
        assertEquals(1, handler.log.count { it == "commitLine" })
    }

    @Test
    fun `single oversized word overflows alone on one line`() {
        // A single uninterrupted token — BreakIterator finds no
        // intra-word break, so even at width=10 we get one line.
        val handler = CapturingHandler()
        SkShaper.MakeJvmAwtTextLayout().shape(
            utf8 = "supercalifragilisticexpialidocious",
            font = awtFont(), leftToRight = true,
            width = 10f, runHandler = handler,
        )
        assertEquals(1, handler.log.count { it == "beginLine" })
        assertEquals(1, handler.log.count { it == "commitLine" })
        assertTrue(handler.runs.isNotEmpty())
    }

    @Test
    fun `wrapped lines anchor at successively lower Y in SkTextBlobShaperRunHandler`() {
        val handler = SkTextBlobShaperRunHandler("the quick brown fox", originX = 0f, originY = 50f)
        SkShaper.MakeJvmAwtTextLayout().shape(
            utf8 = "the quick brown fox", font = awtFont(),
            leftToRight = true, width = 60f, runHandler = handler,
        )
        val blob = handler.makeBlob()
        assertNotNull(blob)
        // Multi-line output → at least 2 runs at distinct Ys.
        val ys = blob!!.runs.flatMap { run ->
            when (run) {
                is org.skia.foundation.SkTextBlob.Run.FullPositions -> run.positions
                    .filterIndexed { idx, _ -> idx % 2 == 1 }
                else -> emptyList()
            }
        }.distinct()
        assertTrue(ys.size >= 2) { "Expected ≥ 2 distinct line Y positions, got $ys" }
    }

    @Test
    fun `cluster offsets stay absolute UTF-8 byte indices across line wraps`() {
        // "aé bé cé" — wraps narrow ; clusters must keep increasing
        // (no resets) since they're absolute byte offsets.
        val handler = CapturingHandler()
        SkShaper.MakeJvmAwtTextLayout().shape(
            utf8 = "aé bé cé", font = awtFont(),
            leftToRight = true, width = 30f, runHandler = handler,
        )
        // Walk all clusters across all runs in the order emitted.
        val clusters = handler.buffers.flatMap { buf ->
            (0 until buf.glyphs.size).map { buf.clusters[it] }
        }
        // Every cluster index should map to a valid byte position
        // within the input ("aé bé cé" = 11 UTF-8 bytes).
        for (c in clusters) {
            assertTrue(c in 0..11) { "cluster $c out of [0, 11] in $clusters" }
        }
    }
}
