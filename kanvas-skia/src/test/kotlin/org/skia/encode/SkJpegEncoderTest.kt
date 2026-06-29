package org.skia.encode

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.kanvas.codec.Codec
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType

/**
 * D3.5 verification suite for [SkJpegEncoder].
 *
 * Covers :
 *  - `Encode(bitmap, options)` returns non-null bytes with the JPEG
 *    SOI signature.
 *  - Encode → decode round-trip via [Codec] preserves opaque
 *    pixels within JPEG quantisation tolerance (lossy ; relaxed
 *    delta).
 *  - Lower [SkJpegEncoder.Options.quality] produces a smaller file
 *    than the default — confirms quality plumbing reaches the
 *    underlying writer.
 *  - The emitted stream is baseline SOF0, not progressive SOF2, and
 *    does not advertise restart intervals.
 *  - Real fixture bitmaps from the shared codec corpus encode to
 *    baseline JPEG, honour quality sizing, and decode back within a
 *    bounded lossy tolerance.
 *  - [SkJpegEncoder.Options] rejects out-of-range quality.
 *  - JPEG output is always opaque even when the source bitmap had
 *    alpha < 255 — alpha is dropped per the
 *    [SkJpegEncoder.AlphaOption.kIgnore] default.
 */
class SkJpegEncoderTest {

    @Test
    fun `Encode produces JPEG bytes with the SOI signature`() {
        val bitmap = makeFlat(16, 16, 0xFF808080.toInt())
        val bytes = SkJpegEncoder.Encode(bitmap)
        assertNotNull(bytes)
        assertTrue(bytes!!.size > 0)
        assertEquals(0xFF.toByte(), bytes[0])
        assertEquals(0xD8.toByte(), bytes[1])
        assertEquals(0xFF.toByte(), bytes[2])
    }

    @Test
    fun `flat-colour JPEG round-trips within quantisation tolerance`() {
        val src = makeFlat(16, 16, 0xFF808080.toInt())
        val bytes = SkJpegEncoder.Encode(src, SkJpegEncoder.Options(quality = 100))!!
        val codec = Codec.MakeFromData(bytes)!!
        val (decoded, result) = codec.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(decoded)
        // JPEG is lossy ; even a flat mid-grey can drift a few ulps
        // on the chroma channels through the subsampling pipeline.
        for (y in 0 until 16) for (x in 0 until 16) {
            val px = decoded!!.getPixel(x, y)
            val r = (px ushr 16) and 0xFF
            val g = (px ushr 8) and 0xFF
            val b = px and 0xFF
            assertTrue(kotlin.math.abs(r - 0x80) <= 4, "R drift at ($x,$y) : $r")
            assertTrue(kotlin.math.abs(g - 0x80) <= 4, "G drift at ($x,$y) : $g")
            assertTrue(kotlin.math.abs(b - 0x80) <= 4, "B drift at ($x,$y) : $b")
        }
    }

    @Test
    fun `lower quality produces fewer bytes than maximum quality`() {
        // A non-flat image is needed — flat colours hit the same DC
        // bins regardless of quality, so the file size barely moves.
        val bitmap = makeGradient(64, 64)
        val highQ = SkJpegEncoder.Encode(bitmap, SkJpegEncoder.Options(quality = 100))!!
        val lowQ = SkJpegEncoder.Encode(bitmap, SkJpegEncoder.Options(quality = 25))!!
        assertTrue(
            lowQ.size < highQ.size,
            "low-quality JPEG must be smaller : q=25 → ${lowQ.size}, q=100 → ${highQ.size}",
        )
    }

    @Test
    fun `Options rejects quality outside 0 to 100`() {
        assertThrows(IllegalArgumentException::class.java) {
            SkJpegEncoder.Options(quality = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SkJpegEncoder.Options(quality = 101)
        }
        SkJpegEncoder.Options(quality = 0)
        SkJpegEncoder.Options(quality = 100)
    }

    @Test
    fun `JPEG output drops alpha — every decoded pixel is fully opaque`() {
        // Source bitmap has alpha = 0x80 ; encoder must still produce
        // an opaque JPEG (alpha is dropped per kIgnore default).
        val src = makeFlat(8, 8, 0x80FFFFFF.toInt())
        val bytes = SkJpegEncoder.Encode(src)!!
        val (decoded, _) = Codec.MakeFromData(bytes)!!.getImage()
        assertNotNull(decoded)
        for (y in 0 until 8) for (x in 0 until 8) {
            val a = (decoded!!.getPixel(x, y) ushr 24) and 0xFF
            assertEquals(0xFF, a, "JPEG pixel ($x,$y) must be fully opaque after encode")
        }
    }

    @Test
    fun `BlendOnBlack composites transparent pixels before JPEG encode`() {
        val ignored = SkJpegEncoder.Encode(
            makeFlat(16, 16, 0x00FF0000.toInt()),
            SkJpegEncoder.Options(quality = 100, alphaOption = SkJpegEncoder.AlphaOption.kIgnore),
        )!!
        val blended = SkJpegEncoder.Encode(
            makeFlat(16, 16, 0x00FF0000.toInt()),
            SkJpegEncoder.Options(quality = 100, alphaOption = SkJpegEncoder.AlphaOption.kBlendOnBlack),
        )!!

        val ignoredPixel = decode(ignored).getPixel(0, 0)
        val blendedPixel = decode(blended).getPixel(0, 0)
        assertTrue(((ignoredPixel ushr 16) and 0xFF) > 220, "kIgnore should preserve red RGB")
        assertTrue(((blendedPixel ushr 16) and 0xFF) < 8, "kBlendOnBlack should encode transparent red as black")
        assertTrue(((blendedPixel ushr 8) and 0xFF) < 8, "kBlendOnBlack green drift")
        assertTrue((blendedPixel and 0xFF) < 8, "kBlendOnBlack blue drift")
    }

    @Test
    fun `Downsample option is written into SOF0 sampling factors`() {
        val bitmap = makeGradient(16, 16)
        assertEquals(0x22, sof0YSampling(SkJpegEncoder.Encode(bitmap, SkJpegEncoder.Options(downsample = SkJpegEncoder.Downsample.k420))!!))
        assertEquals(0x21, sof0YSampling(SkJpegEncoder.Encode(bitmap, SkJpegEncoder.Options(downsample = SkJpegEncoder.Downsample.k422))!!))
        assertEquals(0x11, sof0YSampling(SkJpegEncoder.Encode(bitmap, SkJpegEncoder.Options(downsample = SkJpegEncoder.Downsample.k444))!!))
    }

    @Test
    fun `encoder emits baseline JPEG without progressive or restart markers`() {
        val markers = jpegMarkersBeforeScan(SkJpegEncoder.Encode(makeGradient(16, 16))!!)
        assertTrue(0xC0 in markers, "baseline SOF0 marker must be present")
        assertTrue(0xC2 !in markers, "progressive SOF2 marker is intentionally out of scope")
        assertTrue(0xDD !in markers, "DRI restart interval marker is not emitted by the current encoder")
    }

    @Test
    fun `real fixtures encode as baseline JPEG and decode within quality tolerance`() {
        val fixtures = listOf(
            "/codec-real-images/png/mandrill_64.png",
            "/codec-real-images/png/grayscale_8.png",
            "/codec-real-images/jpeg/dog.jpg",
        )
        for (fixture in fixtures) {
            val src = decode(readFixture(fixture))
            val high = SkJpegEncoder.Encode(src, SkJpegEncoder.Options(quality = 95))!!
            val low = SkJpegEncoder.Encode(src, SkJpegEncoder.Options(quality = 35))!!
            assertTrue(low.size < high.size, "$fixture low-quality JPEG should be smaller")

            val markers = jpegMarkersBeforeScan(high)
            assertTrue(0xC0 in markers, "$fixture must encode as baseline SOF0")
            assertTrue(0xC2 !in markers, "$fixture must not encode as progressive SOF2")

            val roundTrip = decode(high)
            assertEquals(src.width, roundTrip.width, "$fixture width")
            assertEquals(src.height, roundTrip.height, "$fixture height")
            assertSampledSimilarity(src, roundTrip, fixture)
        }
    }

    private fun makeFlat(width: Int, height: Int, color: Int): SkBitmap {
        val b = SkBitmap(width, height, SkColorSpace.makeSRGB(), SkColorType.kRGBA_8888)
        for (y in 0 until height) for (x in 0 until width) {
            b.pixels[y * width + x] = color
        }
        return b
    }

    private fun makeGradient(width: Int, height: Int): SkBitmap {
        val b = SkBitmap(width, height, SkColorSpace.makeSRGB(), SkColorType.kRGBA_8888)
        for (y in 0 until height) for (x in 0 until width) {
            val r = (x * 255 / maxOf(1, width - 1)).coerceIn(0, 255)
            val g = (y * 255 / maxOf(1, height - 1)).coerceIn(0, 255)
            // Add some high-frequency noise so the JPEG quantizer has
            // something non-trivial to compress at low quality.
            val b2 = ((x xor y) * 17) and 0xFF
            b.pixels[y * width + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b2
        }
        return b
    }

    private fun decode(bytes: ByteArray): SkBitmap {
        val (decoded, result) = Codec.MakeFromData(bytes)!!.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(decoded)
        return decoded!!
    }

    private fun readFixture(path: String): ByteArray {
        val stream = javaClass.getResourceAsStream(path)
        assertNotNull(stream, "missing real-image fixture $path")
        return stream!!.use { it.readBytes() }
    }

    private fun assertSampledSimilarity(expected: SkBitmap, actual: SkBitmap, label: String) {
        var totalDelta = 0
        var samples = 0
        var maxDelta = 0
        val xs = sampledPositions(expected.width)
        val ys = sampledPositions(expected.height)
        for (y in ys) for (x in xs) {
            val expectedPixel = expected.getPixel(x, y)
            val actualPixel = actual.getPixel(x, y)
            for (shift in intArrayOf(16, 8, 0)) {
                val delta = kotlin.math.abs(((expectedPixel ushr shift) and 0xFF) - ((actualPixel ushr shift) and 0xFF))
                totalDelta += delta
                maxDelta = maxOf(maxDelta, delta)
                samples++
            }
        }
        val averageDelta = totalDelta.toDouble() / samples
        assertTrue(averageDelta <= 24.0, "$label average sampled RGB delta $averageDelta exceeds JPEG tolerance")
        assertTrue(maxDelta <= 96, "$label max sampled RGB delta $maxDelta exceeds JPEG tolerance")
    }

    private fun sampledPositions(size: Int): List<Int> =
        (0 until minOf(size, 8)).map { index ->
            if (size == 1) 0 else (index * (size - 1) / maxOf(1, minOf(size, 8) - 1))
        }.distinct()

    private fun sof0YSampling(bytes: ByteArray): Int {
        var offset = 2
        while (offset + 4 <= bytes.size) {
            require(bytes[offset] == 0xFF.toByte()) { "expected marker at $offset" }
            val marker = bytes[offset + 1].toInt() and 0xFF
            val length = ((bytes[offset + 2].toInt() and 0xFF) shl 8) or (bytes[offset + 3].toInt() and 0xFF)
            if (marker == 0xC0) {
                val componentCountOffset = offset + 4 + 5
                assertEquals(3, bytes[componentCountOffset].toInt() and 0xFF)
                return bytes[componentCountOffset + 2].toInt() and 0xFF
            }
            offset += 2 + length
        }
        error("SOF0 marker not found")
    }

    private fun jpegMarkersBeforeScan(bytes: ByteArray): Set<Int> {
        assertEquals(0xFF.toByte(), bytes[0])
        assertEquals(0xD8.toByte(), bytes[1])
        val markers = linkedSetOf<Int>()
        var offset = 2
        while (offset + 1 < bytes.size) {
            require(bytes[offset] == 0xFF.toByte()) { "expected marker at $offset" }
            val marker = bytes[offset + 1].toInt() and 0xFF
            markers += marker
            if (marker == 0xDA || marker == 0xD9) break
            val length = ((bytes[offset + 2].toInt() and 0xFF) shl 8) or (bytes[offset + 3].toInt() and 0xFF)
            offset += 2 + length
        }
        return markers
    }
}
