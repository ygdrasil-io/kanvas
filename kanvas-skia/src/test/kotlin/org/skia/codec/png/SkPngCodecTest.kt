package org.skia.codec.png

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.codec.SkCodec
import org.skia.codec.SkEncodedImageFormat
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImageInfo
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * D3.1 verification suite for [SkPngCodec].
 *
 * Covers :
 *  - [SkCodec.MakeFromData] / [SkCodec.MakeFromStream] dispatch.
 *  - PNG-signature sniffing (positive + negative).
 *  - High-bit-depth (16-bpc) decode → [SkColorType.kRGBA_F16Norm] +
 *    Rec.2020 colour space recovered from the embedded `iCCP` chunk
 *    (matches the DM reference profile used by the GM tests).
 *  - 8-bit decode → [SkColorType.kRGBA_8888] + sRGB fallback.
 *  - Determinism : decoding the same bytes twice yields pixel-identical
 *    bitmaps (a precondition for using the codec as the reference loader
 *    for similarity ratchets).
 *  - Geometry validation : [SkPngCodec.getPixels] rejects mismatched
 *    `info` / `dst` arguments.
 */
class SkPngCodecTest {

    // ─── Dispatch + signature ─────────────────────────────────────────

    @Test
    fun `MakeFromData returns null for non-PNG bytes`() {
        assertNull(SkCodec.MakeFromData(ByteArray(0)))
        assertNull(SkCodec.MakeFromData(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
        assertNull(SkCodec.MakeFromData("not a png".toByteArray()))
    }

    @Test
    fun `MakeFromData accepts an 8-bit synthetic PNG`() {
        val bytes = synth8888Png(width = 4, height = 3)
        val codec = SkCodec.MakeFromData(bytes)
        assertNotNull(codec)
        assertEquals(SkEncodedImageFormat.kPNG, codec!!.getEncodedFormat())
        assertEquals(4, codec.dimensions().width)
        assertEquals(3, codec.dimensions().height)
    }

    @Test
    fun `MakeFromStream is equivalent to MakeFromData`() {
        val bytes = synth8888Png(width = 2, height = 2)
        val viaData = SkCodec.MakeFromData(bytes)!!
        val viaStream = SkCodec.MakeFromStream(ByteArrayInputStream(bytes))!!
        assertEquals(viaData.dimensions(), viaStream.dimensions())
        assertEquals(viaData.getEncodedFormat(), viaStream.getEncodedFormat())
    }

    // ─── 8-bit decode ─────────────────────────────────────────────────

    @Test
    fun `8-bit synthetic PNG decodes into RGBA_8888 bitmap`() {
        val bytes = synth8888Png(width = 2, height = 2)
        val codec = SkCodec.MakeFromData(bytes)!!

        val info = codec.getInfo()
        assertEquals(SkColorType.kRGBA_8888, info.colorType)
        assertEquals(SkAlphaType.kUnpremul, info.alphaType)
        assertTrue(info.colorSpace.isSRGB(), "synthetic PNG has no iCCP — codec falls back to sRGB")

        val (bitmap, result) = codec.getImage()
        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(SkColorType.kRGBA_8888, bitmap!!.colorType)
        assertEquals(2, bitmap.width)
        assertEquals(2, bitmap.height)
    }

    @Test
    fun `8-bit decode preserves pixel values (round-trip via ImageIO encoder)`() {
        // Build an 8-bit ARGB image with four distinct pixels, encode it,
        // decode it back through the codec, and verify the pixels round-tripped.
        val src = BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)
        src.setRGB(0, 0, 0xFF112233.toInt())
        src.setRGB(1, 0, 0xFF445566.toInt())
        src.setRGB(0, 1, 0x80778899.toInt())
        src.setRGB(1, 1, 0xFFAABBCC.toInt())
        val bytes = ByteArrayOutputStream().also { ImageIO.write(src, "png", it) }.toByteArray()

        val codec = SkCodec.MakeFromData(bytes)!!
        val (bitmap, result) = codec.getImage()
        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(bitmap)
        // Pixel values are stored as 0xAARRGGBB Int, identical to BufferedImage's TYPE_INT_ARGB.
        assertEquals(0xFF112233.toInt(), bitmap!!.getPixel(0, 0))
        assertEquals(0xFF445566.toInt(), bitmap.getPixel(1, 0))
        assertEquals(0x80778899.toInt(), bitmap.getPixel(0, 1))
        assertEquals(0xFFAABBCC.toInt(), bitmap.getPixel(1, 1))
    }

    // ─── 16-bpc DM reference decode ───────────────────────────────────

    @Test
    fun `16-bpc DM reference decodes into F16 bitmap with embedded ICC`() {
        val bytes = loadFixtureBytes("bigrect")
        val codec = SkCodec.MakeFromData(bytes)
        assertNotNull(codec, "bigrect.png must be decodable through the codec facade")

        val info = codec!!.getInfo()
        assertEquals(SkColorType.kRGBA_F16Norm, info.colorType, "DM refs are 16-bpc — must land on F16")
        assertEquals(SkAlphaType.kPremul, info.alphaType, "F16 storage is premul")

        // The DM reference profile is "DM unified Rec.2020". The codec
        // surfaces it via `getICCProfile`, and `getInfo()` builds a
        // colour space from it.
        val profile = codec.getICCProfile()
        assertNotNull(profile, "DM ref carries an iCCP chunk")
        assertTrue(!info.colorSpace.isSRGB(), "Rec.2020 profile must NOT decode to sRGB")
    }

    @Test
    fun `getImage on a DM reference fills the F16 bitmap`() {
        val bytes = loadFixtureBytes("bigrect")
        val codec = SkCodec.MakeFromData(bytes)!!
        val (bitmap, result) = codec.getImage()
        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(SkColorType.kRGBA_F16Norm, bitmap!!.colorType)
        // bigrect.png matches BigRectGM.getISize() (325 × 125).
        assertEquals(325, bitmap.width)
        assertEquals(125, bitmap.height)
        // F16 is premul ; alpha samples are in [0, 1].
        val out = FloatArray(4)
        bitmap.getPixelF16(50, 50, out)
        assertTrue(out[3] in 0f..1f, "alpha must be in [0, 1]")
    }

    // ─── Determinism ──────────────────────────────────────────────────

    @Test
    fun `decoding the same bytes twice yields pixel-identical bitmaps`() {
        val bytes = loadFixtureBytes("bigrect")
        val first = SkCodec.MakeFromData(bytes)!!.getImage().first!!
        val second = SkCodec.MakeFromData(bytes)!!.getImage().first!!
        assertEquals(first.width, second.width)
        assertEquals(first.height, second.height)
        assertEquals(first.colorType, second.colorType)
        // assertArrayEquals on FloatArray: 0f delta = bit-identical.
        assertArrayEquals(first.pixelsF16, second.pixelsF16, 0f, "F16 pixels must be deterministic")
    }

    // ─── Geometry validation ──────────────────────────────────────────

    @Test
    fun `getPixels rejects mismatched destination geometry`() {
        val bytes = synth8888Png(width = 4, height = 4)
        val codec = SkCodec.MakeFromData(bytes)!!
        val info = codec.getInfo()
        val tooSmall = SkBitmap(2, 2, info.colorSpace, SkColorType.kRGBA_8888)
        assertEquals(SkCodec.Result.kInvalidParameters, codec.getPixels(info, tooSmall))
        val wrongType = SkBitmap(4, 4, info.colorSpace, SkColorType.kRGBA_F16Norm)
        // info still claims 8888 → mismatch with F16 dst.
        assertEquals(SkCodec.Result.kInvalidParameters, codec.getPixels(info, wrongType))
    }

    @Test
    fun `getPixels rejects an info that disagrees with dst color type`() {
        val bytes = synth8888Png(width = 2, height = 2)
        val codec = SkCodec.MakeFromData(bytes)!!
        // Build an info that asks for F16 and a matching F16 dst — but the
        // PNG's natural colorType is 8888, so the codec returns
        // kInvalidConversion (it does not synthesise a colour-type
        // conversion in D3.1).
        val info = SkImageInfo.Make(
            width = 2,
            height = 2,
            colorType = SkColorType.kRGBA_F16Norm,
            alphaType = SkAlphaType.kPremul,
            colorSpace = SkColorSpace.makeSRGB(),
        )
        val dst = SkBitmap(2, 2, info.colorSpace, SkColorType.kRGBA_F16Norm)
        assertEquals(SkCodec.Result.kInvalidConversion, codec.getPixels(info, dst))
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    private fun loadFixtureBytes(name: String): ByteArray {
        val path = "original-888/$name.png"
        return SkPngCodecTest::class.java.classLoader.getResourceAsStream(path)
            ?.use { it.readBytes() }
            ?: error("missing test fixture: $path")
    }

    /**
     * Produce a synthetic 8-bit ARGB PNG of the given dimensions, with a
     * deterministic gradient so callers can assert pixel content if they
     * want. Goes through ImageIO — its output is a valid PNG without an
     * iCCP chunk, which is exactly the "no embedded profile, codec
     * falls back to sRGB" scenario we want to exercise.
     */
    private fun synth8888Png(width: Int, height: Int): ByteArray {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val r = (x * 255 / maxOf(1, width - 1)).coerceIn(0, 255)
                val g = (y * 255 / maxOf(1, height - 1)).coerceIn(0, 255)
                img.setRGB(x, y, (0xFF shl 24) or (r shl 16) or (g shl 8) or 0x40)
            }
        }
        return ByteArrayOutputStream().also { ImageIO.write(img, "png", it) }.toByteArray()
    }
}
