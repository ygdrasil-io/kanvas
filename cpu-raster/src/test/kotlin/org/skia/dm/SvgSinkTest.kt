package org.skia.dm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.tests.GM
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory

/**
 * B2.5 verification suite for [SvgSink] + the [Sink.Result.Bytes]
 * variant + the [Runner] / [DmCli] integration that ties them
 * together — closes the SVG mini plan.
 *
 * Covers :
 *  - `SvgSink.draw(gm)` returns [Sink.Result.Bytes] with a UTF-8
 *    SVG payload + the `image/svg+xml` MIME type ; the SVG parses
 *    cleanly with `javax.xml.parsers.DocumentBuilder`.
 *  - GM-level draw ops surface in the SVG (sanity check that the
 *    sink actually drives the canvas through to the buffer).
 *  - `bgColor()` ≠ white emits a full-viewport background rect ;
 *    default white skips it (output noise minimisation).
 *  - GM exceptions wrap into [Sink.Result.Error] — the message
 *    survives.
 *  - [Runner] handles the `Bytes` variant : record's `extension =
 *    "svg"` and `md5` matches `MessageDigest.getInstance("MD5")`
 *    over the raw bytes (no PNG re-encode).
 *  - [DmCli] resolves `--config svg` to a fresh [SvgSink].
 */
class SvgSinkTest {

    // ─── SvgSink primary contract ─────────────────────────────────────

    @Test
    fun `tag and fileExtension match upstream conventions`() {
        val sink = SvgSink()
        assertEquals("svg", sink.tag)
        assertEquals("svg", sink.fileExtension)
    }

    @Test
    fun `draw returns Bytes with image-svg+xml mime type and parseable XML`() {
        val sink = SvgSink()
        val result = sink.draw(SquaresGM())
        assertTrue(result is Sink.Result.Bytes, "expected Bytes, got: $result")
        val bytes = (result as Sink.Result.Bytes)
        assertEquals(SvgSink.MIME_TYPE, bytes.mimeType)
        assertEquals("image/svg+xml", bytes.mimeType)
        assertWellFormedXml(bytes.bytes)
    }

    @Test
    fun `draw output contains the GM's draw ops`() {
        val sink = SvgSink()
        val bytes = (sink.draw(SquaresGM()) as Sink.Result.Bytes).bytes
        val svg = bytes.toString(Charsets.UTF_8)
        assertTrue(svg.contains("<rect"))
        // SquaresGM draws two rects, fill colours red and blue.
        assertTrue(svg.contains("fill=\"#ff0000\""))
        assertTrue(svg.contains("fill=\"#0000ff\""))
    }

    @Test
    fun `bgColor white is silent (no full-viewport background rect)`() {
        val gm = SquaresGM() // default bgColor = white
        val bytes = (SvgSink().draw(gm) as Sink.Result.Bytes).bytes
        val svg = bytes.toString(Charsets.UTF_8)
        // Count <rect> elements ; with default white bg there should
        // be exactly two (the GM's own draws), no background rect.
        val rectCount = svg.split("<rect").size - 1
        assertEquals(2, rectCount, "expected 2 <rect>s, got $rectCount in: $svg")
    }

    @Test
    fun `non-default bgColor emits a full-viewport background rect`() {
        val gm = NonWhiteBgGM()
        val bytes = (SvgSink().draw(gm) as Sink.Result.Bytes).bytes
        val svg = bytes.toString(Charsets.UTF_8)
        // 1 background rect + 1 GM rect = 2 rects total.
        val rectCount = svg.split("<rect").size - 1
        assertEquals(2, rectCount, "expected 2 <rect>s (bg + content), got: $svg")
        // Background colour matches the GM's bgColor (red).
        assertTrue(svg.contains("fill=\"#ff0000\""))
    }

    @Test
    fun `draw wraps GM exception as Sink Result Error`() {
        val sink = SvgSink()
        val result = sink.draw(ExplodingGM())
        assertTrue(result is Sink.Result.Error)
        val msg = (result as Sink.Result.Error).message
        assertTrue(msg.contains("SvgSink"))
        assertTrue(msg.contains("ExplodingGM"))
        assertTrue(msg.contains("intentional explosion"))
    }

    // ─── Runner integration ───────────────────────────────────────────

    @Test
    fun `Runner with SvgSink produces a Bytes-shaped record`() {
        val report = Runner(
            sinks = listOf(SvgSink()),
            gms = listOf(SquaresGM()),
        ).run()
        assertEquals(1, report.passed.size)
        val r = report.passed.single()
        assertEquals("SquaresGM", r.gmName)
        assertEquals("svg", r.sinkTag)
        assertEquals("svg", r.extension)
        // Bitmap-side classification fields are empty — vector output
        // has no raster colour metadata.
        assertEquals("", r.colorType)
        assertEquals("", r.alphaType)
        assertEquals("", r.gamut)
        assertEquals("", r.transferFn)
        assertEquals("", r.colorDepth)
    }

    @Test
    fun `Runner MD5 over Bytes is the raw byte hash, not a PNG re-encode`() {
        val sink = SvgSink()
        val gm = SquaresGM()
        val report = Runner(sinks = listOf(sink), gms = listOf(gm)).run()
        val rawBytes = (sink.draw(gm) as Sink.Result.Bytes).bytes
        val expectedMd5 = md5Hex(rawBytes)
        assertEquals(expectedMd5, report.passed.single().md5)
    }

    @Test
    fun `Runner JSON output for SVG sink reflects the svg extension`() {
        val report = Runner(
            sinks = listOf(SvgSink()),
            gms = listOf(SquaresGM()),
        ).run()
        val json = report.toJson()
        assertTrue(json.contains("\"config\": \"svg\""))
        assertTrue(json.contains("\"ext\": \"svg\""))
    }

    // ─── DmCli dispatch ───────────────────────────────────────────────

    @Test
    fun `DmCli KNOWN_CONFIGS contains svg`() {
        assertTrue("svg" in DmCli.KNOWN_CONFIGS)
    }

    @Test
    fun `DmCli resolves --config svg to a fresh SvgSink`() {
        val cli = DmCli.parse(arrayOf("--config", "svg"))
        val sinks = cli.resolveSinks()
        assertEquals(1, sinks.size)
        val sink = sinks.single()
        assertNotNull(sink)
        assertTrue(sink is SvgSink)
    }

    @Test
    fun `DmCli resolves a mixed --config 8888 svg matrix`() {
        val cli = DmCli.parse(arrayOf("--config", "8888", "svg"))
        val sinks = cli.resolveSinks()
        assertEquals(2, sinks.size)
        assertTrue(sinks[0] is RasterSink8888)
        assertTrue(sinks[1] is SvgSink)
    }

    // ─── Sink.Result.Bytes equality (data-class contract) ────────────

    @Test
    fun `Sink Result Bytes equals compares by content not reference`() {
        val a = Sink.Result.Bytes(byteArrayOf(1, 2, 3), "image/svg+xml")
        val b = Sink.Result.Bytes(byteArrayOf(1, 2, 3), "image/svg+xml")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `Sink Result Bytes inequality on differing content or mime`() {
        val a = Sink.Result.Bytes(byteArrayOf(1, 2, 3), "image/svg+xml")
        val differentBytes = Sink.Result.Bytes(byteArrayOf(1, 2, 4), "image/svg+xml")
        val differentMime = Sink.Result.Bytes(byteArrayOf(1, 2, 3), "application/pdf")
        assertFalse(a == differentBytes)
        assertFalse(a == differentMime)
    }

    // ─── Test fixtures ────────────────────────────────────────────────

    /** Minimal GM drawing two solid-colour squares. Default bgColor (white). */
    private class SquaresGM : GM() {
        override fun getName(): String = "SquaresGM"
        override fun getISize(): SkISize = SkISize.Make(20, 10)
        override fun onDraw(canvas: SkCanvas?) {
            canvas?.drawRect(SkRect.MakeXYWH(0f, 0f, 10f, 10f), SkPaint(SK_ColorRED))
            canvas?.drawRect(SkRect.MakeXYWH(10f, 0f, 10f, 10f), SkPaint(SK_ColorBLUE))
        }
    }

    /** GM with bgColor() = red, used to assert the bg-rect emission. */
    private class NonWhiteBgGM : GM() {
        init { setBGColor(SK_ColorRED) }
        override fun getName(): String = "NonWhiteBgGM"
        override fun getISize(): SkISize = SkISize.Make(10, 10)
        override fun onDraw(canvas: SkCanvas?) {
            canvas?.drawRect(SkRect.MakeWH(5f, 5f), SkPaint(SK_ColorBLUE))
        }
    }

    /** GM whose onDraw throws — used to assert the Error wrap. */
    private class ExplodingGM : GM() {
        override fun getName(): String = "ExplodingGM"
        override fun getISize(): SkISize = SkISize.Make(10, 10)
        override fun onDraw(canvas: SkCanvas?) {
            throw IllegalStateException("intentional explosion")
        }
    }

    private fun assertWellFormedXml(bytes: ByteArray) {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isValidating = false
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        }
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        assertNotNull(doc.documentElement)
    }

    private fun md5Hex(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("MD5").digest(bytes)
        return md.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }
}
