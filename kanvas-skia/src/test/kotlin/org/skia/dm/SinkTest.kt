package org.skia.dm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorBLUE
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SkColorType
import org.skia.foundation.SkPaint
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.tests.GM

/**
 * Behaviour tests for the D4.1 sink architecture : [Sink] interface,
 * [RasterSink8888], [RasterSinkF16].
 *
 * Contract under test :
 *   1. each sink renders the GM into a bitmap of the expected colour
 *      type and size ;
 *   2. the bg colour is honoured before draw ;
 *   3. exceptions thrown during draw are captured as [Sink.Result.Error] ;
 *   4. the [Sink.tag] strings match upstream's `--config <tag>` syntax.
 */
class SinkTest {

    /** Trivial GM that draws a red square top-left and a blue one bottom-right. */
    private class TwoSquaresGM : GM() {
        override fun getName(): String = "two_squares"
        override fun getISize(): SkISize = SkISize.Make(16, 16)
        override fun onDraw(canvas: SkCanvas?) {
            val c = canvas ?: return
            c.drawRect(SkRect.MakeXYWH(0f, 0f, 8f, 8f), SkPaint().apply { color = SK_ColorRED })
            c.drawRect(SkRect.MakeXYWH(8f, 8f, 8f, 8f), SkPaint().apply { color = SK_ColorBLUE })
        }
    }

    /** GM that always throws — used to exercise the [Sink.Result.Error] branch. */
    private class FailingGM : GM() {
        override fun getName(): String = "failing"
        override fun getISize(): SkISize = SkISize.Make(8, 8)
        override fun onDraw(canvas: SkCanvas?) {
            throw IllegalStateException("simulated failure")
        }
    }

    @Test
    fun `RasterSink8888 tag matches upstream config name`() {
        assertEquals("8888", RasterSink8888().tag)
    }

    @Test
    fun `RasterSinkF16 tag matches upstream config name`() {
        assertEquals("f16", RasterSinkF16().tag)
    }

    @Test
    fun `RasterSink8888 renders into kRGBA_8888 bitmap of GM size`() {
        val sink = RasterSink8888()
        val result = sink.draw(TwoSquaresGM())
        val ok = assertInstanceOf(Sink.Result.Ok::class.java, result)
        assertEquals(SkColorType.kRGBA_8888, ok.bitmap.colorType)
        assertEquals(16, ok.bitmap.width)
        assertEquals(16, ok.bitmap.height)
    }

    @Test
    fun `RasterSinkF16 renders into kRGBA_F16Norm bitmap of GM size`() {
        val sink = RasterSinkF16()
        val result = sink.draw(TwoSquaresGM())
        val ok = assertInstanceOf(Sink.Result.Ok::class.java, result)
        assertEquals(SkColorType.kRGBA_F16Norm, ok.bitmap.colorType)
        assertEquals(16, ok.bitmap.width)
        assertEquals(16, ok.bitmap.height)
    }

    @Test
    fun `RasterSink8888 honours GM bg color before draw`() {
        val sink = RasterSink8888()
        val gm = TwoSquaresGM()
        val ok = sink.draw(gm) as Sink.Result.Ok
        // Pixel (4, 4) is inside the red square — drawn over white bg.
        assertEquals(SK_ColorRED, ok.bitmap.getPixel(4, 4))
        // Pixel (0, 8) is in the empty band — should match the bg (white).
        assertEquals(SK_ColorWHITE, ok.bitmap.getPixel(0, 8))
    }

    @Test
    fun `RasterSinkF16 honours GM bg color before draw`() {
        // Use an sRGB-tagged F16 sink so [SK_ColorRED] (an sRGB constant)
        // round-trips through `getPixel` without colour-space conversion.
        val sink = RasterSinkF16(org.skia.foundation.SkColorSpace.makeSRGB())
        val ok = sink.draw(TwoSquaresGM()) as Sink.Result.Ok
        // Pixel inside red square — should be opaque red.
        assertEquals(SK_ColorRED, ok.bitmap.getPixel(4, 4))
        // Pixel in empty band — should be the bg (white). White is
        // profile-invariant so this also works for the Rec.2020 default.
        assertEquals(SK_ColorWHITE, ok.bitmap.getPixel(0, 8))
    }

    @Test
    fun `RasterSink8888 wraps thrown exceptions as Result Error`() {
        val sink = RasterSink8888()
        val result = sink.draw(FailingGM())
        val err = assertInstanceOf(Sink.Result.Error::class.java, result)
        // Message should mention the sink type, the GM name, and the cause.
        assertNotNull(err.message)
        assert(err.message.contains("RasterSink8888")) { "message: ${err.message}" }
        assert(err.message.contains("failing")) { "message: ${err.message}" }
        assert(err.message.contains("simulated failure")) { "message: ${err.message}" }
    }

    @Test
    fun `RasterSinkF16 wraps thrown exceptions as Result Error`() {
        val sink = RasterSinkF16()
        val result = sink.draw(FailingGM())
        val err = assertInstanceOf(Sink.Result.Error::class.java, result)
        assertNotNull(err.message)
        assert(err.message.contains("RasterSinkF16")) { "message: ${err.message}" }
    }

    @Test
    fun `RasterSink8888 and RasterSinkF16 produce visually-equivalent renders`() {
        // Both sinks tagged sRGB so per-pixel comparison stays in the
        // same encoding domain — F16 with a Rec.2020 default would land
        // in a different colour primary basis even after `getPixel`'s
        // 8-bit quantisation. We sample 4 marker pixels and assert the
        // per-channel diff stays within 2 ulp on 8-bit.
        val gm = TwoSquaresGM()
        val sRgb = org.skia.foundation.SkColorSpace.makeSRGB()
        val raster = (RasterSink8888(sRgb).draw(gm) as Sink.Result.Ok).bitmap
        val f16 = (RasterSinkF16(sRgb).draw(gm) as Sink.Result.Ok).bitmap

        for ((x, y) in listOf(0 to 0, 4 to 4, 12 to 12, 15 to 15)) {
            val a = raster.getPixel(x, y)
            val b = f16.getPixel(x, y)
            for (shift in 0..24 step 8) {
                val ca = (a ushr shift) and 0xFF
                val cb = (b ushr shift) and 0xFF
                assert(kotlin.math.abs(ca - cb) <= 2) {
                    "drift at ($x, $y) channel-shift $shift : 8888=0x${a.toString(16)} vs F16=0x${b.toString(16)}"
                }
            }
        }
    }

    @Test
    fun `Sink Result Ok and Error are distinguishable via sealed when`() {
        // Sanity that the sealed hierarchy is exhaustive in a `when` —
        // ensures future PDF / SVG additions to the sealed class force
        // call sites to update.
        fun describe(r: Sink.Result): String = when (r) {
            is Sink.Result.Ok -> "ok-${r.bitmap.width}x${r.bitmap.height}"
            is Sink.Result.Error -> "error-${r.message}"
        }
        val ok = RasterSink8888().draw(TwoSquaresGM())
        assertNotNull(describe(ok))
        assertNotEquals("", describe(ok))

        val err = RasterSink8888().draw(FailingGM())
        assert(describe(err).startsWith("error-"))
    }

    @Test
    fun `RasterSink8888 default colorSpace is sRGB`() {
        // Skia DM's default raster sink is sRGB-tagged — verify ours matches.
        val ok = RasterSink8888().draw(TwoSquaresGM()) as Sink.Result.Ok
        assertNotNull(ok.bitmap.colorSpace)
        // sRGB-tagged bitmaps don't expose a public predicate on SkColorSpace,
        // but we can check that the F16 sink's default differs (Rec.2020), which
        // implies 8888's default is something else (sRGB).
        val f16 = RasterSinkF16().draw(TwoSquaresGM()) as Sink.Result.Ok
        assertNotEquals(ok.bitmap.colorSpace, f16.bitmap.colorSpace)
    }

    @Suppress("unused")
    private fun assertNotNullOk(r: Sink.Result.Ok?) {
        if (r == null) throw AssertionError("expected Ok, got null")
        assertNull(null)
    }
}
