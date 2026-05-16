package org.skia.shaper

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkFont
import org.skia.foundation.SkTextBlob
import org.skia.foundation.SkTypeface

/**
 * Unit tests for [SkShaper.MakePrimitive] (Phase I4.1).
 *
 * Coverage :
 *  - Empty input emits begin/commitLine but no runs.
 *  - ASCII input emits a single run with the right glyph count
 *    and per-glyph cluster indices = byte offsets.
 *  - LTR layout : glyph X positions monotonically increase by the
 *    font's advance widths, anchored at the run's origin point.
 *  - RTL layout : the first glyph in the buffer ends up rightmost.
 *  - The convenience `SkTextBlobShaperRunHandler` wraps the output
 *    into an [SkTextBlob] with one `FullPositions` run carrying
 *    the shaped glyphs.
 *  - `RunHandler` callback ordering matches the documented
 *    sequence (`beginLine → runInfo → commitRunInfo → runBuffer →
 *    commitRunBuffer → commitLine`).
 *  - Multi-byte UTF-8 input (e.g. `é` = 2 bytes) reports correct
 *    cluster byte offsets.
 */
class SkShaperTest {

    private fun makeFont(): SkFont = SkFont(SkTypeface.MakeEmpty(), 12f)

    /**
     * Capturing run handler — records the ordered sequence of
     * callback names and the most recent `RunInfo` for inspection.
     */
    private class CapturingHandler : SkShaper.RunHandler {
        val log: MutableList<String> = mutableListOf()
        var lastInfo: SkShaper.RunInfo? = null
        var capturedBuffer: SkShaper.Buffer? = null

        override fun beginLine() { log.add("beginLine") }
        override fun runInfo(info: SkShaper.RunInfo) {
            log.add("runInfo")
            lastInfo = info
        }
        override fun commitRunInfo() { log.add("commitRunInfo") }
        override fun runBuffer(info: SkShaper.RunInfo): SkShaper.Buffer {
            log.add("runBuffer")
            val buf = SkShaper.Buffer(
                glyphs = IntArray(info.glyphCount),
                positions = FloatArray(info.glyphCount * 2),
                clusters = IntArray(info.glyphCount),
                point = floatArrayOf(0f, 50f),
            )
            capturedBuffer = buf
            return buf
        }
        override fun commitRunBuffer(info: SkShaper.RunInfo) { log.add("commitRunBuffer") }
        override fun commitLine() { log.add("commitLine") }
    }

    @Test
    fun `empty input emits begin and commit line but no runs`() {
        val handler = CapturingHandler()
        SkShaper.MakePrimitive().shape(
            utf8 = "", font = makeFont(), leftToRight = true,
            width = 100f, runHandler = handler,
        )
        assertEquals(listOf("beginLine", "commitLine"), handler.log)
        assertNull(handler.lastInfo)
    }

    @Test
    fun `ASCII input emits a single LTR run with correct glyph count`() {
        val handler = CapturingHandler()
        SkShaper.MakePrimitive().shape(
            utf8 = "ABC", font = makeFont(), leftToRight = true,
            width = 100f, runHandler = handler,
        )
        assertEquals(
            listOf("beginLine", "runInfo", "commitRunInfo", "runBuffer", "commitRunBuffer", "commitLine"),
            handler.log,
        )
        val info = handler.lastInfo!!
        assertEquals(3, info.glyphCount)
        assertEquals(0, info.bidiLevel)  // LTR
    }

    @Test
    fun `LTR positions monotonically increase from origin`() {
        val handler = CapturingHandler()
        SkShaper.MakePrimitive().shape(
            utf8 = "AB", font = makeFont(), leftToRight = true,
            width = 100f, runHandler = handler,
        )
        val buf = handler.capturedBuffer!!
        // Origin (0, 50). Both glyphs at y=50.
        assertEquals(50f, buf.positions[1])
        assertEquals(50f, buf.positions[3])
        assertTrue(buf.positions[0] <= buf.positions[2]) {
            "LTR x[0]=${buf.positions[0]} should be ≤ x[1]=${buf.positions[2]}"
        }
    }

    @Test
    fun `RTL positions place first glyph rightmost`() {
        val handler = CapturingHandler()
        SkShaper.MakePrimitive().shape(
            utf8 = "AB", font = makeFont(), leftToRight = false,
            width = 100f, runHandler = handler,
        )
        val buf = handler.capturedBuffer!!
        // RTL : glyph 0 (which is "A") ends up rightmost.
        assertTrue(buf.positions[0] >= buf.positions[2]) {
            "RTL x[0]=${buf.positions[0]} should be ≥ x[1]=${buf.positions[2]}"
        }
        assertEquals(1, handler.lastInfo!!.bidiLevel)
    }

    @Test
    fun `cluster offsets match utf8 byte offsets for ASCII`() {
        val handler = CapturingHandler()
        SkShaper.MakePrimitive().shape(
            utf8 = "ABC", font = makeFont(), leftToRight = true,
            width = 100f, runHandler = handler,
        )
        val buf = handler.capturedBuffer!!
        // ASCII : 1 byte per code point.
        assertEquals(0, buf.clusters[0])
        assertEquals(1, buf.clusters[1])
        assertEquals(2, buf.clusters[2])
    }

    @Test
    fun `cluster offsets handle multi-byte UTF-8`() {
        // "aé" : 'a' = 1 byte at offset 0 ; 'é' = 2 bytes at offset 1.
        val handler = CapturingHandler()
        SkShaper.MakePrimitive().shape(
            utf8 = "aé", font = makeFont(), leftToRight = true,
            width = 100f, runHandler = handler,
        )
        val buf = handler.capturedBuffer!!
        assertEquals(0, buf.clusters[0])
        assertEquals(1, buf.clusters[1])
    }

    @Test
    fun `SkTextBlobShaperRunHandler produces a blob with FullPositions run`() {
        val handler = SkTextBlobShaperRunHandler("ABC", originX = 10f, originY = 50f)
        SkShaper.MakePrimitive().shape(
            utf8 = "ABC", font = makeFont(), leftToRight = true,
            width = 100f, runHandler = handler,
        )
        val blob = handler.makeBlob()
        assertNotNull(blob)
        assertEquals(1, blob!!.runs.size)
        val run = blob.runs[0] as SkTextBlob.Run.FullPositions
        assertEquals(3, run.glyphIds.size)
        assertEquals(6, run.positions.size)
    }

    @Test
    fun `SkTextBlobShaperRunHandler bounds extend across the run width`() {
        val handler = SkTextBlobShaperRunHandler("AB", originX = 10f, originY = 50f)
        SkShaper.MakePrimitive().shape(
            utf8 = "AB", font = makeFont(), leftToRight = true,
            width = 100f, runHandler = handler,
        )
        handler.makeBlob()
        val b = handler.bounds()
        // Width is non-zero (origin pad + advance pad) ; height
        // depends on the typeface's metrics — empty typeface returns
        // 0 metrics so we only assert the X extent here.
        assertTrue(b.width() > 0f) { "bounds=$b expected non-empty width" }
    }
}
