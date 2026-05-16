package org.skia.dm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.math.SK_ColorRED
import org.skia.math.SK_ColorWHITE
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkPaint
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.tests.GM

/**
 * D4.3 verification suite for [Runner] / [Report] / [RunRecord].
 *
 * Covers :
 *  - Runner iterates every (GM × sink) combination — N × M records
 *    in the output regardless of pass/fail mix.
 *  - Each `Sink.Result.Ok` produces a [RunRecord] with a non-empty
 *    PNG-MD5 ; deterministic GMs round-trip to the same MD5 across
 *    runs.
 *  - `Sink.Result.Error` (sink throws or returns Error) lands in
 *    [Report.failed] with a non-null [RunRecord.errorMessage].
 *  - [Report.toJson] emits valid JSON in the upstream `dm.json`
 *    shape — verified by structural property checks (top-level
 *    keys, nested record shape, escape handling).
 *  - The classifier names well-known colour spaces ("srgb",
 *    "rec2020") rather than degrading to `"custom"` for them.
 */
class RunnerTest {

    @Test
    fun `Runner produces one record per (GM, sink) combination`() {
        val gms = listOf(RedSquareGM(), WhiteSquareGM())
        val sinks = listOf<Sink>(RasterSink8888())
        val report = Runner(sinks, gms).run()
        assertEquals(2, report.all.size)
        assertEquals(2, report.passed.size)
        assertEquals(0, report.failed.size)
    }

    @Test
    fun `passed records carry a non-empty PNG md5`() {
        val report = Runner(listOf(RasterSink8888()), listOf(RedSquareGM())).run()
        val r = report.passed.single()
        assertEquals("RedSquareGM", r.gmName)
        assertEquals("8888", r.sinkTag)
        assertEquals("png", r.extension)
        assertEquals(32, r.md5.length, "MD5 must be 32 hex chars")
        assertTrue(r.md5.matches(Regex("[0-9a-f]{32}")), "lowercase hex expected, got ${r.md5}")
    }

    @Test
    fun `MD5 is deterministic for a deterministic GM`() {
        val a = Runner(listOf(RasterSink8888()), listOf(RedSquareGM())).run()
        val b = Runner(listOf(RasterSink8888()), listOf(RedSquareGM())).run()
        assertEquals(a.passed.single().md5, b.passed.single().md5)
    }

    @Test
    fun `different GMs produce different MD5s`() {
        val report = Runner(listOf(RasterSink8888()), listOf(RedSquareGM(), WhiteSquareGM())).run()
        val red = report.passed.single { it.gmName == "RedSquareGM" }
        val white = report.passed.single { it.gmName == "WhiteSquareGM" }
        assertNotEquals(red.md5, white.md5)
    }

    @Test
    fun `Sink Result Error lands in failed and carries the message`() {
        val report = Runner(listOf(ExplodingSink()), listOf(RedSquareGM())).run()
        assertEquals(0, report.passed.size)
        assertEquals(1, report.failed.size)
        val r = report.failed.single()
        assertFalse(r.passed)
        assertNotNull(r.errorMessage)
        assertTrue(r.errorMessage!!.contains("boom"), "carried-through message expected, got ${r.errorMessage}")
        assertEquals("", r.md5, "failed records have empty md5")
        assertEquals("", r.extension, "failed records have empty ext")
    }

    @Test
    fun `Report toJson emits valid JSON with the upstream-style top level shape`() {
        val report = Runner(
            sinks = listOf(RasterSink8888()),
            gms = listOf(RedSquareGM()),
            properties = mapOf("build_flavor" to "release"),
            key = mapOf("os" to "Mac", "compiler" to "Clang"),
        ).run()
        val json = report.toJson()
        // Top-level structural anchors.
        assertTrue(json.startsWith("{\n"))
        assertTrue(json.contains("\"build_flavor\": \"release\""))
        assertTrue(json.contains("\"key\": {"))
        assertTrue(json.contains("\"os\": \"Mac\""))
        assertTrue(json.contains("\"compiler\": \"Clang\""))
        assertTrue(json.contains("\"results\": ["))
        // Per-record shape.
        assertTrue(json.contains("\"name\": \"RedSquareGM\""))
        assertTrue(json.contains("\"config\": \"8888\""))
        assertTrue(json.contains("\"source_type\": \"gm\""))
        assertTrue(json.contains("\"ext\": \"png\""))
        assertTrue(json.contains("\"color_type\": \"rgba_8888\""))
        assertTrue(json.contains("\"alpha_type\": \"unpremul\""))
        // Reasonable JSON close.
        assertTrue(json.endsWith("}"))
    }

    @Test
    fun `Report toJson handles failure records with error prefix in md5`() {
        val report = Runner(listOf(ExplodingSink()), listOf(RedSquareGM())).run()
        val json = report.toJson()
        assertTrue(json.contains("\"md5\": \"error: "), "failure md5 must be prefixed")
    }

    @Test
    fun `classifier names rec2020 gamut and TF rather than degrading to custom`() {
        // Use the same Rec.2020 working space the GM test harness picks
        // (the RasterSinkF16 default). Confirms the classifier wires
        // SkNamedGamut.kRec2020 / SkNamedTransferFn.kRec2020 correctly.
        val sink = RasterSinkF16(
            colorSpace = SkColorSpace.makeRGB(
                org.skia.foundation.skcms.SkNamedTransferFn.kRec2020,
                org.skia.foundation.skcms.SkNamedGamut.kRec2020,
            )!!,
        )
        val report = Runner(listOf(sink), listOf(RedSquareGM())).run()
        val r = report.passed.single()
        assertEquals("rec2020", r.gamut)
        assertEquals("rec2020", r.transferFn)
        assertEquals("rgba_f16", r.colorType)
        assertEquals("premul", r.alphaType)
        assertEquals("f16", r.colorDepth)
    }

    @Test
    fun `summary reports pass and fail counts`() {
        val report = Runner(
            sinks = listOf(RasterSink8888(), ExplodingSink()),
            gms = listOf(RedSquareGM()),
        ).run()
        assertEquals(1, report.passed.size)
        assertEquals(1, report.failed.size)
        val s = report.summary()
        assertTrue(s.contains("1 passed"))
        assertTrue(s.contains("1 failed"))
        assertTrue(s.contains("total 2"))
    }

    @Test
    fun `JSON escape handles double quotes and backslashes in property values`() {
        val report = Runner(
            sinks = listOf(RasterSink8888()),
            gms = listOf(RedSquareGM()),
            properties = mapOf("note" to "has \"quotes\" and \\backslashes"),
        ).run()
        val json = report.toJson()
        // Both special chars must be escape-prefixed in the output.
        assertTrue(json.contains("\\\"quotes\\\""), "double quotes must be escaped, got: $json")
        assertTrue(json.contains("\\\\backslashes"), "backslashes must be escaped, got: $json")
    }

    // ─── Test fixtures ────────────────────────────────────────────────

    /** Minimal GM filling the canvas with red. Deterministic. */
    private class RedSquareGM : GM() {
        override fun getName(): String = "RedSquareGM"
        override fun getISize(): SkISize = SkISize.Make(8, 8)
        override fun onDraw(canvas: SkCanvas?) {
            canvas?.drawRect(SkRect.MakeWH(8f, 8f), SkPaint(SK_ColorRED))
        }
    }

    /** Minimal GM filling the canvas with white. Deterministic. */
    private class WhiteSquareGM : GM() {
        override fun getName(): String = "WhiteSquareGM"
        override fun getISize(): SkISize = SkISize.Make(8, 8)
        override fun onDraw(canvas: SkCanvas?) {
            canvas?.drawRect(SkRect.MakeWH(8f, 8f), SkPaint(SK_ColorWHITE))
        }
    }

    /**
     * Sink that always fails — exercises the [Sink.Result.Error]
     * branch of the Runner without depending on a real failure
     * mode in the raster pipeline.
     */
    private class ExplodingSink : Sink {
        override val tag: String = "explode"
        override fun draw(src: GM): Sink.Result =
            Sink.Result.Error("boom : sink configured to fail for ${src.name()}")
    }
}
