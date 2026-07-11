package org.graphiks.kanvas.codec.jpeg

import java.io.ByteArrayOutputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImageInfo
import kotlin.math.roundToInt

class JpegSequentialDecodeTest {

    @Test
    fun `decodes SOF0 grayscale and YCbCr 440`() {
        assertPixel(
            sequentialJpeg(
                marker = SOF0,
                precision = 8,
                components = listOf(FixtureComponent(1, 0x11, 128)),
            ),
            0xFF808080.toInt(),
        )
        assertPixel(
            sequentialJpeg(
                marker = SOF0,
                precision = 8,
                components = listOf(
                    FixtureComponent(1, 0x12, 160),
                    FixtureComponent(2, 0x11, 96),
                    FixtureComponent(3, 0x11, 192),
                ),
            ),
            yCbCrToArgb(160, 96, 192),
        )
    }

    @Test
    fun `decodes SOF1 YCbCr 411 and every sampling factor through four`() {
        for (h in 1..4) {
            for (v in 1..4) {
                val jpeg = sequentialJpeg(
                    marker = SOF1,
                    precision = 8,
                    components = listOf(
                        FixtureComponent(1, (h shl 4) or v, 160),
                        FixtureComponent(2, 0x11, 96),
                        FixtureComponent(3, 0x11, 192),
                    ),
                )
                assertPixel(jpeg, yCbCrToArgb(160, 96, 192), "h=$h v=$v")
            }
        }
    }

    @Test
    fun `decodes SOF0 and SOF1 RGB CMYK and YCCK`() {
        for (marker in listOf(SOF0, SOF1)) {
            assertPixel(
                sequentialJpeg(
                    marker = marker,
                    precision = 8,
                    adobeTransform = 0,
                    components = listOf(
                        FixtureComponent('R'.code, 0x11, 96),
                        FixtureComponent('G'.code, 0x11, 144),
                        FixtureComponent('B'.code, 0x11, 192),
                    ),
                ),
                0xFF6090C0.toInt(),
                "RGB marker=$marker",
            )
            assertPixel(
                sequentialJpeg(
                    marker = marker,
                    precision = 8,
                    adobeTransform = 0,
                    components = listOf(
                        FixtureComponent(1, 0x11, 128),
                        FixtureComponent(2, 0x11, 128),
                        FixtureComponent(3, 0x11, 128),
                        FixtureComponent(4, 0x11, 128),
                    ),
                ),
                0xFF404040.toInt(),
                "CMYK marker=$marker",
            )
            val ycck = yCbCrToArgb(160, 96, 192)
            assertPixel(
                sequentialJpeg(
                    marker = marker,
                    precision = 8,
                    adobeTransform = 2,
                    components = listOf(
                        FixtureComponent(1, 0x11, 160),
                        FixtureComponent(2, 0x11, 96),
                        FixtureComponent(3, 0x11, 192),
                        FixtureComponent(4, 0x11, 128),
                    ),
                ),
                scaleRgb(ycck, 128),
                "YCCK marker=$marker",
            )
        }
    }

    @Test
    fun `preserves 12 bit sequential samples before normalized output`() {
        val data = sequentialJpeg(
            marker = SOF1,
            precision = 12,
            components = listOf(FixtureComponent(1, 0x11, 2_049)),
        )
        val document = JpegDocument.open(data).document!!
        val frame = parseJpeg(data, document.metadata)!!
        val samples = decodeSequentialDct(frame, frame.scans.single())

        assertEquals(12, samples.precision)
        assertEquals(2_049, samples.planes.single()[0])
        assertPixel(data, 0xFF808080.toInt())
    }

    @Test
    fun `normalizes SOF1 12 bit grayscale F16 from source precision`() {
        val codec = JpegCodec.Decoder.make(
            sequentialJpeg(
                marker = SOF1,
                precision = 12,
                components = listOf(FixtureComponent(1, 0x11, 2_049)),
            ),
        )!!
        val info = SkImageInfo.Make(
            width = 8,
            height = 8,
            colorType = SkColorType.kRGBA_F16Norm,
            alphaType = SkAlphaType.kPremul,
            colorSpace = codec.getInfo().colorSpace,
        )
        val bitmap = SkBitmap(8, 8, info.colorSpace, info.colorType)

        assertEquals(org.graphiks.kanvas.codec.Codec.Result.kSuccess, codec.getPixels(info, bitmap))
        val pixel = FloatArray(4)
        bitmap.getPixelF16(0, 0, pixel)
        assertEquals(2_049f / 4_095f, pixel[0], 0.00025f)
        assertTrue(kotlin.math.abs(pixel[0] - 128f / 255f) > 0.001f)
    }

    @Test
    fun `keeps SOF1 12 bit YCbCr F16 neutral at the source midpoint`() {
        val codec = JpegCodec.Decoder.make(
            sequentialJpeg(
                marker = SOF1,
                precision = 12,
                components = listOf(
                    FixtureComponent(1, 0x11, 2_048),
                    FixtureComponent(2, 0x11, 2_048),
                    FixtureComponent(3, 0x11, 2_048),
                ),
            ),
        )!!
        val info = SkImageInfo.Make(
            width = 8,
            height = 8,
            colorType = SkColorType.kRGBA_F16Norm,
            alphaType = SkAlphaType.kPremul,
            colorSpace = codec.getInfo().colorSpace,
        )
        val bitmap = SkBitmap(8, 8, info.colorSpace, info.colorType)

        assertEquals(org.graphiks.kanvas.codec.Codec.Result.kSuccess, codec.getPixels(info, bitmap))
        val pixel = FloatArray(4)
        bitmap.getPixelF16(0, 0, pixel)
        val expected = 2_048f / 4_095f
        assertEquals(expected, pixel[0], 0.00025f)
        assertEquals(expected, pixel[1], 0.00025f)
        assertEquals(expected, pixel[2], 0.00025f)
    }

    @Test
    fun `rejects sequential AC ZRL that runs past the block`() {
        val codec = JpegCodec.Decoder.make(malformedZrlJpeg())!!

        val (_, result) = codec.getImage()

        assertEquals(org.graphiks.kanvas.codec.Codec.Result.kErrorInInput, result)
    }

    @Test
    fun `rejects sequential AC magnitude larger than SOF0 permits`() {
        val codec = JpegCodec.Decoder.make(malformedAcMagnitudeJpeg())!!

        val (_, result) = codec.getImage()

        assertEquals(org.graphiks.kanvas.codec.Codec.Result.kErrorInInput, result)
    }

    private fun assertPixel(data: ByteArray, expected: Int, label: String = "") {
        val codec = JpegCodec.Decoder.make(data)
        assertNotNull(codec, label)
        val (bitmap, result) = codec!!.getImage()
        assertEquals(org.graphiks.kanvas.codec.Codec.Result.kSuccess, result, label)
        assertEquals(expected, bitmap!!.getPixel(0, 0), label)
    }

    private fun sequentialJpeg(
        marker: Int,
        precision: Int,
        components: List<FixtureComponent>,
        adobeTransform: Int? = null,
    ): ByteArray {
        val maxH = components.maxOf { it.sampling ushr 4 }
        val maxV = components.maxOf { it.sampling and 0x0F }
        val out = ByteArrayOutputStream()
        out.writeMarker(0xD8)
        if (adobeTransform != null) {
            out.writeSegment(0xEE) {
                write(byteArrayOf(0x41, 0x64, 0x6F, 0x62, 0x65))
                writeU16BE(100)
                writeU16BE(0)
                writeU16BE(0)
                write(adobeTransform)
            }
        }
        out.writeSegment(0xDB) {
            write(0)
            repeat(64) { write(1) }
        }
        out.writeSegment(marker) {
            write(precision)
            writeU16BE(maxV * 8)
            writeU16BE(maxH * 8)
            write(components.size)
            for (component in components) {
                write(component.id)
                write(component.sampling)
                write(0)
            }
        }
        out.writeSegment(0xC4) {
            write(0x00)
            repeat(3) { write(0) }
            write(16)
            repeat(12) { write(0) }
            for (symbol in 0..15) write(symbol)
        }
        out.writeSegment(0xC4) {
            write(0x10)
            write(1)
            repeat(15) { write(0) }
            write(0)
        }
        out.writeSegment(0xDA) {
            write(components.size)
            for (component in components) {
                write(component.id)
                write(0)
            }
            write(0)
            write(63)
            write(0)
        }
        val previousDc = IntArray(components.size)
        val bits = buildString {
            for ((index, component) in components.withIndex()) {
                val desiredDc = (component.sample - (1 shl (precision - 1))) * 8
                repeat((component.sampling ushr 4) * (component.sampling and 0x0F)) {
                    val difference = desiredDc - previousDc[index]
                    previousDc[index] = desiredDc
                    val category = category(difference)
                    append(category.toString(2).padStart(4, '0'))
                    append(amplitude(difference, category))
                    append('0')
                }
            }
        }
        out.write(entropyBits(bits))
        out.writeMarker(0xD9)
        return out.toByteArray()
    }

    private fun malformedZrlJpeg(): ByteArray {
        val out = ByteArrayOutputStream()
        out.writeMarker(0xD8)
        out.writeSegment(0xDB) {
            write(0)
            repeat(64) { write(1) }
        }
        out.writeSegment(SOF0) {
            write(8)
            writeU16BE(8)
            writeU16BE(8)
            write(1)
            write(1)
            write(0x11)
            write(0)
        }
        out.writeSegment(0xC4) {
            write(0x00)
            write(1)
            repeat(15) { write(0) }
            write(0)
        }
        out.writeSegment(0xC4) {
            write(0x10)
            write(1)
            repeat(15) { write(0) }
            write(0xF0)
        }
        out.writeSegment(0xDA) {
            write(1)
            write(1)
            write(0)
            write(0)
            write(63)
            write(0)
        }
        out.write(entropyBits("00000"))
        out.writeMarker(0xD9)
        return out.toByteArray()
    }

    private fun malformedAcMagnitudeJpeg(): ByteArray {
        val out = ByteArrayOutputStream()
        out.writeMarker(0xD8)
        out.writeSegment(0xDB) {
            write(0)
            repeat(64) { write(1) }
        }
        out.writeSegment(SOF0) {
            write(8)
            writeU16BE(8)
            writeU16BE(8)
            write(1)
            write(1)
            write(0x11)
            write(0)
        }
        out.writeSegment(0xC4) {
            write(0x00)
            write(1)
            repeat(15) { write(0) }
            write(0)
        }
        out.writeSegment(0xC4) {
            write(0x10)
            write(2)
            repeat(15) { write(0) }
            write(0x0F)
            write(0)
        }
        out.writeSegment(0xDA) {
            write(1)
            write(1)
            write(0)
            write(0)
            write(63)
            write(0)
        }
        out.write(entropyBits("00${"1".repeat(16)}"))
        out.writeMarker(0xD9)
        return out.toByteArray()
    }

    private fun category(value: Int): Int =
        if (value == 0) 0 else 32 - Integer.numberOfLeadingZeros(kotlin.math.abs(value))

    private fun amplitude(value: Int, category: Int): String {
        if (category == 0) return ""
        val encoded = if (value >= 0) value else value + (1 shl category) - 1
        return encoded.toString(2).padStart(category, '0')
    }

    private fun entropyBits(bits: String): ByteArray {
        val out = ByteArray((bits.length + 7) / 8)
        for (index in bits.indices) {
            if (bits[index] == '1') out[index / 8] = (out[index / 8].toInt() or (1 shl (7 - (index and 7)))).toByte()
        }
        for (index in bits.length until out.size * 8) {
            out[index / 8] = (out[index / 8].toInt() or (1 shl (7 - (index and 7)))).toByte()
        }
        return ByteArrayOutputStream().apply {
            for (value in out) {
                write(value.toInt() and 0xFF)
                if (value == 0xFF.toByte()) write(0)
            }
        }.toByteArray()
    }

    private fun yCbCrToArgb(y: Int, cb: Int, cr: Int): Int {
        val r = (y + 1.402 * (cr - 128)).roundToInt().coerceIn(0, 255)
        val g = (y - 0.344136 * (cb - 128) - 0.714136 * (cr - 128)).roundToInt().coerceIn(0, 255)
        val b = (y + 1.772 * (cb - 128)).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun scaleRgb(pixel: Int, k: Int): Int =
        (0xFF shl 24) or
            ((((pixel ushr 16) and 0xFF) * k + 127) / 255 shl 16) or
            ((((pixel ushr 8) and 0xFF) * k + 127) / 255 shl 8) or
            (((pixel and 0xFF) * k + 127) / 255)

    private fun ByteArrayOutputStream.writeMarker(marker: Int) {
        write(0xFF)
        write(marker)
    }

    private fun ByteArrayOutputStream.writeSegment(marker: Int, writePayload: ByteArrayOutputStream.() -> Unit) {
        val payload = ByteArrayOutputStream().apply(writePayload).toByteArray()
        writeMarker(marker)
        writeU16BE(payload.size + 2)
        write(payload)
    }

    private fun ByteArrayOutputStream.writeU16BE(value: Int) {
        write(value ushr 8)
        write(value and 0xFF)
    }

    private data class FixtureComponent(val id: Int, val sampling: Int, val sample: Int)

    private companion object {
        const val SOF0 = 0xC0
        const val SOF1 = 0xC1
    }
}
