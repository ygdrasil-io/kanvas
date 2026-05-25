package org.skia.shaper

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkFont
import org.skia.foundation.SkTextBlob
import org.skia.foundation.SkTypeface
import org.skia.foundation.SkPathBuilder
import org.skia.utils.SkCustomTypefaceBuilder
import org.graphiks.math.SkRect

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
    private fun unitGlyph() = SkPathBuilder().addRect(SkRect.MakeLTRB(0f, -1f, 1f, 0f)).detach()

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
    fun `cluster offsets are monotonic for LTR visual order`() {
        val handler = CapturingHandler()
        SkShaper.MakePortable().shape(
            utf8 = "abé",
            font = makeFont(),
            leftToRight = true,
            width = 100f,
            runHandler = handler,
        )
        val clusters = handler.capturedBuffer!!.clusters
        for (i in 1 until clusters.size) {
            assertTrue(clusters[i] >= clusters[i - 1]) {
                "clusters must be monotonic in LTR visual order: ${clusters.toList()}"
            }
        }
    }

    @Test
    fun `cluster offsets are monotonic for RTL visual order`() {
        val handler = CapturingHandler()
        SkShaper.MakePortable().shape(
            utf8 = "abé",
            font = makeFont(),
            leftToRight = false,
            width = 100f,
            runHandler = handler,
        )
        val clusters = handler.capturedBuffer!!.clusters
        for (i in 1 until clusters.size) {
            assertTrue(clusters[i] >= clusters[i - 1]) {
                "clusters must be monotonic in visual run order: ${clusters.toList()}"
            }
        }
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

    @Test
    fun `MakePortable matches primitive shaping output`() {
        val primitive = CapturingHandler()
        val portable = CapturingHandler()
        val font = makeFont()
        val text = "aéB"

        SkShaper.MakePrimitive().shape(text, font, leftToRight = true, width = 200f, runHandler = primitive)
        SkShaper.MakePortable().shape(text, font, leftToRight = true, width = 200f, runHandler = portable)

        assertNotNull(primitive.lastInfo)
        assertNotNull(portable.lastInfo)
        assertEquals(primitive.lastInfo!!.glyphCount, portable.lastInfo!!.glyphCount)
        assertEquals(primitive.lastInfo!!.bidiLevel, portable.lastInfo!!.bidiLevel)
        assertEquals(primitive.lastInfo!!.advanceX, portable.lastInfo!!.advanceX)
        assertEquals(primitive.lastInfo!!.advanceY, portable.lastInfo!!.advanceY)
        assertEquals(primitive.lastInfo!!.utf8Range, portable.lastInfo!!.utf8Range)
        assertNotNull(primitive.capturedBuffer)
        assertNotNull(portable.capturedBuffer)
        assertTrue(primitive.capturedBuffer!!.glyphs.contentEquals(portable.capturedBuffer!!.glyphs))
        assertTrue(primitive.capturedBuffer!!.positions.contentEquals(portable.capturedBuffer!!.positions))
        assertTrue(primitive.capturedBuffer!!.clusters.contentEquals(portable.capturedBuffer!!.clusters))
    }

    @Test
    fun `MakeOpenType is an alias of portable factory behavior`() {
        val openType = CapturingHandler()
        val portable = CapturingHandler()
        val font = makeFont()
        val text = "ffi"

        SkShaper.MakeOpenType().shape(text, font, leftToRight = true, width = 200f, runHandler = openType)
        SkShaper.MakePortable().shape(text, font, leftToRight = true, width = 200f, runHandler = portable)

        assertNotNull(openType.lastInfo)
        assertNotNull(portable.lastInfo)
        assertEquals(openType.lastInfo!!.glyphCount, portable.lastInfo!!.glyphCount)
        assertEquals(openType.lastInfo!!.bidiLevel, portable.lastInfo!!.bidiLevel)
        assertEquals(openType.lastInfo!!.advanceX, portable.lastInfo!!.advanceX)
        assertEquals(openType.lastInfo!!.advanceY, portable.lastInfo!!.advanceY)
        assertEquals(openType.lastInfo!!.utf8Range, portable.lastInfo!!.utf8Range)
        assertNotNull(openType.capturedBuffer)
        assertNotNull(portable.capturedBuffer)
        assertTrue(openType.capturedBuffer!!.glyphs.contentEquals(portable.capturedBuffer!!.glyphs))
        assertTrue(openType.capturedBuffer!!.positions.contentEquals(portable.capturedBuffer!!.positions))
        assertTrue(openType.capturedBuffer!!.clusters.contentEquals(portable.capturedBuffer!!.clusters))
    }

    @Test
    fun `standard ligature toggle shapes fi into single glyph with source cluster`() {
        val tf = SkCustomTypefaceBuilder()
            .setGlyph('f'.code, 1f, unitGlyph())
            .setGlyph('i'.code, 1f, unitGlyph())
            .setGlyph(0xFB01, 1.5f, unitGlyph())
            .detach()
        val font = SkFont(tf, 12f)

        val noLiga = CapturingHandler()
        SkShaper.MakePortable().shape("fi", font, leftToRight = true, width = 100f, runHandler = noLiga)
        assertEquals(2, noLiga.lastInfo!!.glyphCount)
        assertEquals(listOf(0, 1), noLiga.capturedBuffer!!.clusters.toList())

        val liga = CapturingHandler()
        SkShaper.MakePortable(
            SkShaper.Features(standardLigatures = true, discretionaryLigatures = false),
        ).shape("fi", font, leftToRight = true, width = 100f, runHandler = liga)
        assertEquals(1, liga.lastInfo!!.glyphCount)
        assertEquals(listOf(0), liga.capturedBuffer!!.clusters.toList())
    }
}
