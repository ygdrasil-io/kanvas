package org.skia.dm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.tests.GM

/**
 * Behaviour tests for the D4.2 [PictureSink] (record-via-`SkPicture`,
 * replay through a backing raster sink).
 *
 * Contract under test :
 *   1. `PictureSink` produces the same pixels as a direct render
 *      through the backing sink (identity round-trip on every op
 *      currently recordable by `SkRecordingCanvas`) ;
 *   2. tag is composed as `"pic-<backingTag>"` ;
 *   3. exceptions during recording or replay are wrapped as
 *      [Sink.Result.Error] with a diagnosable message ;
 *   4. `PictureSink()` defaults to wrapping a `RasterSinkF16`.
 */
class PictureSinkTest {

    /** Trivial GM exercising save / translate / drawRect ops. */
    private class SquaresGM : GM() {
        override fun getName(): String = "squares"
        override fun getISize(): SkISize = SkISize.Make(16, 16)
        override fun onDraw(canvas: SkCanvas?) {
            val c = canvas ?: return
            c.drawRect(SkRect.MakeXYWH(0f, 0f, 8f, 8f), SkPaint().apply { color = SK_ColorRED })
            c.save()
            c.translate(8f, 8f)
            c.drawRect(SkRect.MakeXYWH(0f, 0f, 8f, 8f), SkPaint().apply { color = SK_ColorBLUE })
            c.restore()
        }
    }

    /** GM that always throws — used to exercise the error branch. */
    private class FailingGM : GM() {
        override fun getName(): String = "failing_pic"
        override fun getISize(): SkISize = SkISize.Make(8, 8)
        override fun onDraw(canvas: SkCanvas?) {
            throw IllegalStateException("simulated picture failure")
        }
    }

    @Test
    fun `PictureSink default tag is pic-f16`() {
        assertEquals("pic-f16", PictureSink().tag)
    }

    @Test
    fun `PictureSink composes tag from backing sink tag`() {
        assertEquals("pic-8888", PictureSink(RasterSink8888()).tag)
        assertEquals("pic-f16", PictureSink(RasterSinkF16()).tag)
    }

    @Test
    fun `PictureSink-8888 produces the same pixels as RasterSink8888 directly`() {
        // Identity round-trip : record the GM, replay through 8888, must
        // match a direct 8888 render byte-for-byte (same backing sink,
        // same colour space, same bitmap shape).
        val sRgb = SkColorSpace.makeSRGB()
        val direct = (RasterSink8888(sRgb).draw(SquaresGM()) as Sink.Result.Ok).bitmap
        val viaPicture = (PictureSink(RasterSink8888(sRgb)).draw(SquaresGM()) as Sink.Result.Ok).bitmap

        assertEquals(direct.width, viaPicture.width)
        assertEquals(direct.height, viaPicture.height)
        assertEquals(SkColorType.kRGBA_8888, viaPicture.colorType)
        for (y in 0 until direct.height) {
            for (x in 0 until direct.width) {
                assertEquals(
                    direct.getPixel(x, y),
                    viaPicture.getPixel(x, y),
                    "pixel ($x, $y) differs after picture round-trip",
                )
            }
        }
    }

    @Test
    fun `PictureSink-f16 produces the same pixels as RasterSinkF16 directly`() {
        val sRgb = SkColorSpace.makeSRGB()
        val direct = (RasterSinkF16(sRgb).draw(SquaresGM()) as Sink.Result.Ok).bitmap
        val viaPicture = (PictureSink(RasterSinkF16(sRgb)).draw(SquaresGM()) as Sink.Result.Ok).bitmap

        assertEquals(SkColorType.kRGBA_F16Norm, viaPicture.colorType)
        for (y in 0 until direct.height) {
            for (x in 0 until direct.width) {
                assertEquals(
                    direct.getPixel(x, y),
                    viaPicture.getPixel(x, y),
                    "pixel ($x, $y) differs after F16 picture round-trip",
                )
            }
        }
    }

    @Test
    fun `PictureSink honours bg color from source GM`() {
        // Source GM has white bg, fills 1/4 with red — verify a pixel
        // outside the red square is still white after picture playback.
        val sRgb = SkColorSpace.makeSRGB()
        val ok = PictureSink(RasterSink8888(sRgb)).draw(SquaresGM()) as Sink.Result.Ok
        // Pixel (15, 0) is in the empty top-right corner — should match bg.
        assertEquals(SK_ColorWHITE, ok.bitmap.getPixel(15, 0))
        // Pixel (4, 4) is inside red square.
        assertEquals(SK_ColorRED, ok.bitmap.getPixel(4, 4))
    }

    @Test
    fun `PictureSink wraps exceptions thrown during recording`() {
        val sink = PictureSink(RasterSink8888())
        val result = sink.draw(FailingGM())
        val err = assertInstanceOf(Sink.Result.Error::class.java, result)
        // Recording-phase failure : message tagged "(recording)".
        assert(err.message.contains("PictureSink")) { "message: ${err.message}" }
        assert(err.message.contains("failing_pic")) { "message: ${err.message}" }
        assert(err.message.contains("recording")) { "message: ${err.message}" }
        assert(err.message.contains("simulated picture failure")) { "message: ${err.message}" }
    }

    @Test
    fun `PictureSink propagates errors from the backing sink`() {
        // Backing sink that always errors out — we should see its
        // message bubble up unchanged (PictureSink does not re-wrap).
        val errorSink = object : Sink {
            override val tag: String = "error"
            override fun draw(src: GM): Sink.Result =
                Sink.Result.Error("backing[${src.name()}]: simulated backing failure")
        }
        val result = PictureSink(errorSink).draw(SquaresGM())
        val err = assertInstanceOf(Sink.Result.Error::class.java, result)
        assert(err.message.contains("simulated backing failure")) { "message: ${err.message}" }
        // The PlaybackGM adapter forwards the source GM's name through.
        assert(err.message.contains("squares")) { "message: ${err.message}" }
    }

    @Test
    fun `PictureSink default constructor wraps RasterSinkF16`() {
        // Constructor with no argument = pic-f16. Just exercise it
        // to make sure the default value of `backingSink` is wired.
        val result = PictureSink().draw(SquaresGM())
        val ok = assertInstanceOf(Sink.Result.Ok::class.java, result)
        assertEquals(SkColorType.kRGBA_F16Norm, ok.bitmap.colorType)
    }

    @Test
    fun `PictureSink works recursively with nested PictureSink`() {
        // A picture replayed into a picture sink is still a valid src —
        // i.e. PictureSink should be composable. The double-recording
        // round-trip must still be pixel-identical to a direct render.
        val sRgb = SkColorSpace.makeSRGB()
        val direct = (RasterSink8888(sRgb).draw(SquaresGM()) as Sink.Result.Ok).bitmap
        val nested = (PictureSink(PictureSink(RasterSink8888(sRgb))).draw(SquaresGM()) as Sink.Result.Ok).bitmap
        assertEquals("pic-pic-8888", PictureSink(PictureSink(RasterSink8888(sRgb))).tag)
        for (y in 0 until direct.height) {
            for (x in 0 until direct.width) {
                assertEquals(
                    direct.getPixel(x, y),
                    nested.getPixel(x, y),
                    "pixel ($x, $y) differs after nested picture round-trip",
                )
            }
        }
    }
}
