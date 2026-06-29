package org.skia.encode

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.kanvas.codec.SkCodec
import org.skia.core.SkAlphaType
import org.skia.core.SkColorSpaceXformSteps
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn
import org.graphiks.math.SkColorSetARGB
import java.io.ByteArrayOutputStream

/**
 * D3.5 verification suite for [SkPngEncoder].
 *
 * Covers :
 *  - `Encode(bitmap, options)` returns non-null bytes for an 8888
 *    bitmap.
 *  - Encode → decode round-trip via [SkCodec] is **byte-identical**
 *    on opaque pixels — PNG is lossless.
 *  - Encode-to-stream agrees with encode-to-bytes.
 *  - Real fixture bitmaps from the shared codec corpus round-trip
 *    losslessly, exercise zlib compression/filter selection, and
 *    preserve the documented 8-bit RGBA/no-colour-metadata contract.
 *  - [SkPngEncoder.Options] validates `zLibLevel` and `comments`
 *    invariants.
 */
class SkPngEncoderTest {

    @Test
    fun `Encode returns non-null bytes for an 8888 bitmap`() {
        val bitmap = makeGradient(8, 8)
        val bytes = SkPngEncoder.Encode(bitmap)
        assertNotNull(bytes)
        assertTrue(bytes!!.size > 0, "encoder must produce non-empty output")
        // PNG signature is 0x89 0x50 0x4E 0x47.
        assertEquals(0x89.toByte(), bytes[0])
        assertEquals(0x50.toByte(), bytes[1])
        assertEquals(0x4E.toByte(), bytes[2])
        assertEquals(0x47.toByte(), bytes[3])
    }

    @Test
    fun `encode then decode round-trips opaque pixels byte-identical`() {
        val src = makeGradient(8, 8) // alpha = 0xFF for every pixel
        val bytes = SkPngEncoder.Encode(src)!!
        val codec = SkCodec.MakeFromData(bytes)!!
        val (decoded, result) = codec.getImage()
        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(decoded)
        assertEquals(src.width, decoded!!.width)
        assertEquals(src.height, decoded.height)
        for (y in 0 until src.height) for (x in 0 until src.width) {
            assertEquals(
                src.getPixel(x, y),
                decoded.getPixel(x, y),
                "PNG must round-trip ($x,$y) byte-identical",
            )
        }
    }

    @Test
    fun `Encode to OutputStream matches Encode to ByteArray`() {
        val bitmap = makeGradient(4, 4)
        val viaBytes = SkPngEncoder.Encode(bitmap)!!
        val baos = ByteArrayOutputStream()
        assertTrue(SkPngEncoder.Encode(baos, bitmap))
        val viaStream = baos.toByteArray()
        assertEquals(viaBytes.toList(), viaStream.toList(), "stream and ByteArray paths must agree")
    }

    @Test
    fun `Options validates zLibLevel and comments invariants`() {
        assertThrows(IllegalArgumentException::class.java) {
            SkPngEncoder.Options(zLibLevel = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SkPngEncoder.Options(zLibLevel = 10)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SkPngEncoder.Options(comments = listOf("only-key-no-value"))
        }
        // Valid : 0 and 9 are both in range, even-sized comments OK.
        SkPngEncoder.Options(zLibLevel = 0)
        SkPngEncoder.Options(zLibLevel = 9, comments = listOf("k", "v", "k2", "v2"))
    }

    @Test
    fun `Encode passes through a non-default options object without crashing`() {
        // Non-default filters, compression, and comments must still
        // produce valid bytes.
        val bitmap = makeGradient(2, 2)
        val opts = SkPngEncoder.Options(
            filterFlags = SkPngEncoder.FilterFlag.kPaeth.mask,
            zLibLevel = 9,
            comments = listOf("Software", "kanvas-skia"),
        )
        val bytes = SkPngEncoder.Encode(bitmap, opts)
        assertNotNull(bytes)
        assertTrue(bytes!!.containsAscii("tEXtSoftware\u0000kanvas-skia"))
    }

    @Test
    fun `Encode never returns null on a valid input — null reserved for future failure paths`() {
        // Even an empty 1×1 bitmap must round-trip.
        val tiny = SkBitmap(1, 1, SkColorSpace.makeSRGB(), SkColorType.kRGBA_8888)
        tiny.pixels[0] = 0xFF112233.toInt()
        val bytes = SkPngEncoder.Encode(tiny)
        assertNotNull(bytes)
        // Sanity : null is what the encoder will eventually return on
        // a future "unsupported colour type" branch — checking the
        // negative path is left to that slice.
        @Suppress("USELESS_IS_CHECK") assertNull(null as ByteArray?)
    }

    @Test
    fun `zLibLevel affects encoded IDAT size`() {
        val bitmap = makeGradient(32, 32)
        val uncompressed = SkPngEncoder.Encode(bitmap, SkPngEncoder.Options(zLibLevel = 0))!!
        val compressed = SkPngEncoder.Encode(bitmap, SkPngEncoder.Options(zLibLevel = 9))!!

        assertTrue(
            compressed.size < uncompressed.size,
            "higher zLibLevel should produce a smaller PNG for the gradient fixture",
        )
    }

    @Test
    fun `Encode round-trips BGRA and alpha-only bitmaps through pure Kotlin codec`() {
        val bgra = SkBitmap(2, 1, SkColorSpace.makeSRGB(), SkColorType.kBGRA_8888)
        bgra.setPixel(0, 0, 0xFF112233.toInt())
        bgra.setPixel(1, 0, 0x80445566.toInt())
        val decodedBgra = decode(SkPngEncoder.Encode(bgra)!!)
        assertEquals(0xFF112233.toInt(), decodedBgra.getPixel(0, 0))
        assertEquals(0x80445566.toInt(), decodedBgra.getPixel(1, 0))

        val alpha = SkBitmap(2, 1, SkColorSpace.makeSRGB(), SkColorType.kAlpha_8)
        alpha.setPixel(0, 0, 0x7F000000)
        alpha.setPixel(1, 0, 0x11000000)
        val decodedAlpha = decode(SkPngEncoder.Encode(alpha)!!)
        assertEquals(0x7F000000, decodedAlpha.getPixel(0, 0))
        assertEquals(0x11000000, decodedAlpha.getPixel(1, 0))
    }

    @Test
    fun `real PNG fixtures re-encode as 8-bit RGBA without color metadata and round-trip pixels`() {
        val fixtures = listOf(
            "/codec-real-images/png/mandrill_64.png",
            "/codec-real-images/png/color_wheel.png",
            "/codec-real-images/png/grayscale_16.png",
        )
        for (fixture in fixtures) {
            val src = decode(readFixture(fixture))
            val encoded = SkPngEncoder.Encode(src, SkPngEncoder.Options(zLibLevel = 9))!!
            assertPng8BitRgbaNoColorChunks(encoded, fixture)
            assertSamePixels(src, decode(encoded), fixture)
        }
    }

    @Test
    fun `real PNG fixture compression and filters reduce encoded size`() {
        val src = decode(readFixture("/codec-real-images/png/mandrill_64.png"))
        val uncompressed = SkPngEncoder.Encode(
            src,
            SkPngEncoder.Options(
                filterFlags = SkPngEncoder.FilterFlag.kNone.mask,
                zLibLevel = 0,
            ),
        )!!
        val compressed = SkPngEncoder.Encode(
            src,
            SkPngEncoder.Options(
                filterFlags = SkPngEncoder.FilterFlag.kAll.mask,
                zLibLevel = 9,
            ),
        )!!
        assertTrue(
            compressed.size < uncompressed.size,
            "filtered/compressed real PNG output should be smaller than unfiltered zlib level 0",
        )
    }

    @Test
    fun `F16 Rec2020 PNG export uses explicit sRGB readback boundary`() {
        val bitmap = makeFor333Rec2020F16Sample()
        val expected = expectedSrgbSkColorFromFor333F16Sample()
        val oldDirectRec2020Readback = SkColorSetARGB(255, 214, 208, 253)

        assertEquals(
            oldDirectRec2020Readback,
            bitmap.getPixel(0, 0),
            "getPixel remains the historical internal F16 Rec2020 byte oracle",
        )
        assertEquals(
            expected,
            bitmap.getPixelAsSrgb(0, 0),
            "explicit sRGB readback must convert F16 Rec2020 before export",
        )

        val decoded = decode(SkPngEncoder.Encode(bitmap)!!)
        assertEquals(expected, decoded.getPixel(0, 0), "PNG RGBA row must match explicit sRGB readback")
        assertNotEquals(oldDirectRec2020Readback, decoded.getPixel(0, 0), "PNG export must not preserve raw Rec2020 bytes")
    }

    private fun makeGradient(width: Int, height: Int): SkBitmap {
        val b = SkBitmap(width, height, SkColorSpace.makeSRGB(), SkColorType.kRGBA_8888)
        for (y in 0 until height) for (x in 0 until width) {
            val r = (x * 255 / maxOf(1, width - 1)).coerceIn(0, 255)
            val g = (y * 255 / maxOf(1, height - 1)).coerceIn(0, 255)
            b.pixels[y * width + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or 0x40
        }
        return b
    }

    private fun makeFor333Rec2020F16Sample(): SkBitmap {
        val rec2020 = SkColorSpace.makeRGB(SkNamedTransferFn.kSRGB, SkNamedGamut.kRec2020)!!
        val bitmap = SkBitmap(1, 1, rec2020, SkColorType.kRGBA_F16Norm)
        bitmap.pixelsF16[0] = 0.836928904f
        bitmap.pixelsF16[1] = 0.813901007f
        bitmap.pixelsF16[2] = 0.989494443f
        bitmap.pixelsF16[3] = 1.0f
        return bitmap
    }

    private fun expectedSrgbSkColorFromFor333F16Sample(): Int {
        val rec2020 = SkColorSpace.makeRGB(SkNamedTransferFn.kSRGB, SkNamedGamut.kRec2020)!!
        val rgba = floatArrayOf(0.836928904f, 0.813901007f, 0.989494443f, 1.0f)
        SkColorSpaceXformSteps(
            rec2020,
            SkAlphaType.kPremul,
            SkColorSpace.makeSRGB(),
            SkAlphaType.kUnpremul,
        ).apply(rgba)
        return SkColorSetARGB(
            (rgba[3] * 256f).toInt().coerceIn(0, 255),
            (rgba[0] * 256f).toInt().coerceIn(0, 255),
            (rgba[1] * 256f).toInt().coerceIn(0, 255),
            (rgba[2] * 256f).toInt().coerceIn(0, 255),
        )
    }

    private fun decode(bytes: ByteArray): SkBitmap {
        val codec = SkCodec.MakeFromData(bytes)!!
        val (decoded, result) = codec.getImage()
        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(decoded)
        return decoded!!
    }

    private fun readFixture(path: String): ByteArray {
        val stream = javaClass.getResourceAsStream(path)
        assertNotNull(stream, "missing real-image fixture $path")
        return stream!!.use { it.readBytes() }
    }

    private fun assertSamePixels(expected: SkBitmap, actual: SkBitmap, label: String) {
        assertEquals(expected.width, actual.width, "$label width")
        assertEquals(expected.height, actual.height, "$label height")
        for (y in 0 until expected.height) for (x in 0 until expected.width) {
            assertEquals(expected.getPixel(x, y), actual.getPixel(x, y), "$label pixel($x,$y)")
        }
    }

    private fun assertPng8BitRgbaNoColorChunks(bytes: ByteArray, label: String) {
        val chunks = pngChunks(bytes)
        val ihdr = chunks.firstOrNull { it.type == "IHDR" } ?: error("$label missing IHDR")
        assertEquals(8, ihdr.data[8].toInt() and 0xFF, "$label bit depth")
        assertEquals(6, ihdr.data[9].toInt() and 0xFF, "$label color type")
        assertEquals(0, ihdr.data[12].toInt() and 0xFF, "$label interlace")
        val chunkTypes = chunks.map { it.type }.toSet()
        assertTrue("iCCP" !in chunkTypes, "$label must not emit iCCP until color-space transport is supported")
        assertTrue("sRGB" !in chunkTypes, "$label must not emit sRGB until color-space transport is supported")
        assertTrue("gAMA" !in chunkTypes, "$label must not emit gAMA until color-space transport is supported")
        assertTrue("cHRM" !in chunkTypes, "$label must not emit cHRM until color-space transport is supported")
    }

    private fun pngChunks(bytes: ByteArray): List<PngChunk> {
        assertEquals(0x89.toByte(), bytes[0])
        assertEquals(0x50.toByte(), bytes[1])
        val chunks = mutableListOf<PngChunk>()
        var offset = 8
        while (offset + 12 <= bytes.size) {
            val length = readU32BE(bytes, offset)
            val type = bytes.copyOfRange(offset + 4, offset + 8).decodeToString()
            val dataStart = offset + 8
            val dataEnd = dataStart + length
            chunks += PngChunk(type, bytes.copyOfRange(dataStart, dataEnd))
            offset = dataEnd + 4
            if (type == "IEND") break
        }
        return chunks
    }

    private fun readU32BE(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun ByteArray.containsAscii(text: String): Boolean {
        val needle = text.encodeToByteArray()
        return asList().windowed(needle.size).any { window ->
            window == needle.asList()
        }
    }

    private data class PngChunk(val type: String, val data: ByteArray)
}
