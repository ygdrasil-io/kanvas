package org.graphiks.kanvas.codec.jpeg

import org.graphiks.kanvas.codec.Codec
import org.graphiks.kanvas.codec.test.CodecTestFixtures
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType

/**
 * The source image is generated deterministically with [JpegEncoder], so it is
 * a project-owned fixture rather than an external codec oracle.  Expectations
 * are always derived from the source JPEG's decoded pixels, not the original
 * RGB bitmap: this proves coefficient-domain preservation independently of the
 * encoder's quantisation.
 */
class JpegTransformTest {

    @Test
    fun `identity preserves bytes and unknown APP payload`() {
        val source = withUnknownApp(encodedFixture(32, 32))
        val document = JpegDocument.open(source).document!!

        val result = document.transcode(JpegTransform.Identity)

        assertNull(result.diagnostic)
        assertArrayEquals(source, result.bytes)
        val transformed = JpegDocument.open(result.bytes!!).document!!
        assertArrayEquals(
            UNKNOWN_APP_PAYLOAD,
            transformed.copyPayload(transformed.segments.single { it.marker == UNKNOWN_APP_MARKER }),
        )
    }

    @Test
    fun `MCU aligned crop is a coefficient transform and preserves unknown APP`() {
        // 4:4:4 avoids a deliberately different chroma edge interpolation at
        // a newly cropped boundary; it lets this test assert literal decoded
        // pixels while the rotation test below still covers 4:2:0 sampling.
        val source = withUnknownApp(encodedFixture(32, 32, JpegEncoder.Downsample.k444))
        val expected = decodedPixels(source).crop(16, 0, 16, 16)

        val result = JpegDocument.open(source).document!!.transcode(JpegTransform.Crop(16, 0, 16, 16))

        assertNull(result.diagnostic)
        assertPixelsEqual(expected, decodedPixels(result.bytes!!))
        val transformed = JpegDocument.open(result.bytes).document!!
        assertArrayEquals(UNKNOWN_APP_PAYLOAD, transformed.copyPayload(transformed.segments.single { it.marker == UNKNOWN_APP_MARKER }))
    }

    @Test
    fun `flips and right angle rotations preserve exactly transformed decoded pixels`() {
        val source = encodedFixture(32, 32)
        val expected = decodedPixels(source)
        val cases = listOf(
            JpegTransform.FlipHorizontal to expected.flipHorizontal(),
            JpegTransform.FlipVertical to expected.flipVertical(),
            JpegTransform.Rotate90 to expected.rotate90(),
            JpegTransform.Rotate180 to expected.rotate180(),
            JpegTransform.Rotate270 to expected.rotate270(),
        )

        for ((transform, pixels) in cases) {
            val result = JpegDocument.open(source).document!!.transcode(transform)
            assertNull(result.diagnostic, transform.toString())
            assertPixelsEqual(pixels, decodedPixels(result.bytes!!), transform.toString())
        }
    }

    @Test
    fun `non MCU aligned crop refuses without pixel re-encoding`() {
        val source = encodedFixture(32, 32)

        val result = JpegDocument.open(source).document!!.transcode(JpegTransform.Crop(1, 0, 16, 16))

        assertEquals(null, result.bytes)
        assertEquals("jpeg.transform.not-mcu-aligned", result.diagnostic!!.code)
        assertEquals(Codec.Result.kInvalidParameters, result.diagnostic.result)
    }

    @Test
    fun `restart interval is regenerated in output MCU traversal`() {
        val source = restartGrayscaleFixture()
        val expected = decodedPixels(source).flipHorizontal()

        val result = JpegDocument.open(source).document!!.transcode(JpegTransform.FlipHorizontal)

        assertNull(result.diagnostic)
        assertPixelsEqual(expected, decodedPixels(result.bytes!!))
        val transformed = JpegDocument.open(result.bytes).document!!
        assertEquals(0xDD, transformed.segments.single { it.marker == 0xDD }.marker)
        assertEquals(0xD0, transformed.segments.single { it.marker == 0xD0 }.marker)
    }

    @Test
    fun `oversized declared coefficient grid is refused before entropy allocation`() {
        val source = CodecTestFixtures.simpleGrayscaleJpeg().copyOf()
        val document = JpegDocument.open(source).document!!
        val sof = document.segments.single { it.marker == 0xC0 }
        // 16_384² is still within JpegLimits.DEFAULT.maxPixels, but needs
        // 4_194_304 8x8 blocks and must not allocate them for a transform.
        source[sof.range.first + 1] = 0x40
        source[sof.range.first + 2] = 0
        source[sof.range.first + 3] = 0x40
        source[sof.range.first + 4] = 0

        val result = JpegDocument.open(source).document!!.transcode(JpegTransform.FlipHorizontal)

        assertEquals(null, result.bytes)
        assertEquals("jpeg.transform.limit.coefficients", result.diagnostic!!.code)
        assertEquals(Codec.Result.kOutOfMemory, result.diagnostic.result)
    }

    @Test
    fun `progressive and arithmetic frames have explicit transform refusals`() {
        val progressive = encodedFixture(32, 32).replaceSofMarker(0xC2)
        val arithmetic = encodedFixture(32, 32).replaceSofMarker(0xC9)

        assertEquals(
            "jpeg.transform.process.unsupported",
            JpegDocument.open(progressive).document!!.transcode(JpegTransform.FlipHorizontal).diagnostic!!.code,
        )
        assertEquals(
            "jpeg.transform.process.unsupported",
            JpegDocument.open(progressive).document!!.transcode(JpegTransform.Identity).diagnostic!!.code,
        )
        assertEquals(
            "jpeg.transform.process.unsupported",
            JpegDocument.open(arithmetic).document!!.transcode(JpegTransform.FlipHorizontal).diagnostic!!.code,
        )
    }

    private fun encodedFixture(
        width: Int,
        height: Int,
        downsample: JpegEncoder.Downsample = JpegEncoder.Downsample.k420,
    ): ByteArray {
        val bitmap = SkBitmap(width, height, SkColorSpace.makeSRGB(), SkColorType.kRGBA_8888)
        for (y in 0 until height) for (x in 0 until width) {
            val r = (x * 255 / (width - 1))
            val g = (y * 255 / (height - 1))
            val b = ((x * 19) xor (y * 37)) and 0xFF
            bitmap.pixels[y * width + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return JpegEncoder.encode(bitmap, JpegEncoder.Options(quality = 100, downsample = downsample))!!
    }

    private fun withUnknownApp(jpeg: ByteArray): ByteArray {
        val length = UNKNOWN_APP_PAYLOAD.size + 2
        val app = byteArrayOf(0xFF.toByte(), UNKNOWN_APP_MARKER.toByte(), (length ushr 8).toByte(), length.toByte()) + UNKNOWN_APP_PAYLOAD
        return jpeg.copyOfRange(0, 2) + app + jpeg.copyOfRange(2, jpeg.size)
    }

    /** Project-owned two-MCU zero-block fixture with DRI=1 and one RST0. */
    private fun restartGrayscaleFixture(): ByteArray {
        val original = CodecTestFixtures.simpleGrayscaleJpeg(width = 16, height = 8)
        val document = JpegDocument.open(original).document!!
        val sos = document.segments.single { it.marker == 0xDA }
        val eoi = document.segments.single { it.marker == 0xD9 }
        val sosOffset = sos.offset.toInt()
        val entropyStart = sos.range.last + 1
        val eoiOffset = eoi.offset.toInt()
        val dri = byteArrayOf(0xFF.toByte(), 0xDD.toByte(), 0, 4, 0, 1)
        val entropy = byteArrayOf(0x3F, 0xFF.toByte(), 0xD0.toByte(), 0x3F)
        return original.copyOfRange(0, sosOffset) + dri +
            original.copyOfRange(sosOffset, entropyStart) + entropy + original.copyOfRange(eoiOffset, original.size)
    }

    private fun ByteArray.replaceSofMarker(marker: Int): ByteArray = copyOf().also { bytes ->
        for (offset in 2 until bytes.size - 1) {
            if (bytes[offset] == 0xFF.toByte() && (bytes[offset + 1].toInt() and 0xFF) == 0xC0) {
                bytes[offset + 1] = marker.toByte()
                return@also
            }
        }
        error("SOF0 marker not found")
    }

    private fun decodedPixels(bytes: ByteArray): Pixels {
        val codec = Codec.MakeFromData(bytes)
        assertNotNull(codec)
        val (bitmap, result) = codec!!.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        return Pixels(bitmap!!.width, bitmap.height, bitmap.pixels.copyOf())
    }

    private fun assertPixelsEqual(expected: Pixels, actual: Pixels, label: String = "") {
        assertEquals(expected.width, actual.width, label)
        assertEquals(expected.height, actual.height, label)
        assertArrayEquals(expected.argb, actual.argb, label)
    }

    private data class Pixels(val width: Int, val height: Int, val argb: IntArray) {
        fun crop(x: Int, y: Int, cropWidth: Int, cropHeight: Int): Pixels = Pixels(cropWidth, cropHeight, IntArray(cropWidth * cropHeight) { index ->
            argb[(y + index / cropWidth) * width + x + index % cropWidth]
        })

        fun flipHorizontal(): Pixels = Pixels(width, height, IntArray(argb.size) { index ->
            val x = index % width
            val y = index / width
            argb[y * width + width - 1 - x]
        })

        fun flipVertical(): Pixels = Pixels(width, height, IntArray(argb.size) { index ->
            val x = index % width
            val y = index / width
            argb[(height - 1 - y) * width + x]
        })

        fun rotate90(): Pixels = Pixels(height, width, IntArray(argb.size) { index ->
            val x = index % height
            val y = index / height
            argb[(height - 1 - x) * width + y]
        })

        fun rotate180(): Pixels = flipHorizontal().flipVertical()

        fun rotate270(): Pixels = Pixels(height, width, IntArray(argb.size) { index ->
            val x = index % height
            val y = index / height
            argb[x * width + width - 1 - y]
        })
    }

    private companion object {
        const val UNKNOWN_APP_MARKER = 0xE3
        val UNKNOWN_APP_PAYLOAD = byteArrayOf(0x61, 0x72, 0x62, 0x69, 0x74, 0x72, 0x61, 0x72, 0x79)
    }
}
