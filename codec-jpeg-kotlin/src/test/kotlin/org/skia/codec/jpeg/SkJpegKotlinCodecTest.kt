package org.skia.codec.jpeg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.codec.CodecDecoderProvider
import org.skia.codec.SkCodec
import org.skia.codec.test.CodecNegativeFixtures
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkColorType
import org.skia.foundation.SkEncodedImageFormat
import org.skia.foundation.SkEncodedOrigin
import org.skia.foundation.SkICC
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn
import java.io.ByteArrayOutputStream
import java.util.ServiceLoader
import kotlin.math.roundToInt

class SkJpegKotlinCodecTest {

    @Test
    fun `rejects invalid signature and mismatched component scan`() {
        val cases = listOf(
            CodecNegativeFixtures.invalidMagic("empty JPEG input", ByteArray(0)),
            CodecNegativeFixtures.invalidMagic("ASCII non-JPEG payload", "not-a-jpeg"),
        )
        for (case in cases) {
            assertFalse(SkJpegKotlinCodec.Decoder.matches(case.data), case.name)
            assertNull(SkJpegKotlinCodec.Decoder.make(case.data), case.name)
        }

        assertNull(SkJpegKotlinCodec.Decoder.make(grayscaleJpeg(width = 8, height = 8, componentCount = 3)))
    }

    @Test
    fun `rejects shared truncated JPEG fixtures`() {
        val valid = grayscaleJpeg(width = 8, height = 8)
        val cases = listOf(
            CodecNegativeFixtures.truncated("SOI only", valid, size = 2),
            CodecNegativeFixtures.truncatedTail("missing EOI", valid, droppedBytes = 2),
        )

        for (case in cases) {
            assertNull(SkJpegKotlinCodec.Decoder.make(case.data), case.name)
        }
    }

    @Test
    fun `is registered through ServiceLoader`() {
        val decoders = ServiceLoader.load(CodecDecoderProvider::class.java)
            .flatMap { it.decoders() }
        assertTrue(decoders.any { it.name == "jpeg" })
    }

    @Test
    fun `decodes baseline grayscale 8x8 jpeg`() {
        val codec = SkJpegKotlinCodec.Decoder.make(grayscaleJpeg(width = 8, height = 8))

        assertNotNull(codec)
        assertTrue(codec is SkJpegKotlinCodec)
        assertEquals(SkEncodedImageFormat.kJPEG, codec!!.getEncodedFormat())
        assertEquals(8, codec.getInfo().width)
        assertEquals(8, codec.getInfo().height)
        assertEquals(SkColorType.kRGBA_8888, codec.getInfo().colorType)
        assertEquals(SkAlphaType.kUnpremul, codec.getInfo().alphaType)
        assertTrue(codec.getInfo().colorSpace.isSRGB())

        val (bitmap, result) = codec.getImage()
        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(bitmap)
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                assertEquals(0xFF808080.toInt(), bitmap!!.getPixel(x, y), "x=$x y=$y")
            }
        }
    }

    @Test
    fun `decodes partial edge blocks`() {
        val codec = SkJpegKotlinCodec.Decoder.make(grayscaleJpeg(width = 13, height = 9))!!
        val (bitmap, result) = codec.getImage()
        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(13, bitmap!!.width)
        assertEquals(9, bitmap.height)
        assertEquals(0xFF808080.toInt(), bitmap.getPixel(12, 8))
    }

    @Test
    fun `decodes baseline color 444 8x8 jpeg`() {
        val codec = SkJpegKotlinCodec.Decoder.make(color444Jpeg())!!
        val (bitmap, result) = codec.getImage()

        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(bitmap)
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                assertEquals(0xFFF16937.toInt(), bitmap!!.getPixel(x, y), "x=$x y=$y")
            }
        }
    }

    @Test
    fun `decodes baseline color 422 16x8 jpeg`() {
        val codec = SkJpegKotlinCodec.Decoder.make(colorJpeg(width = 16, height = 8, ySampling = 0x21))!!
        val (bitmap, result) = codec.getImage()

        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(bitmap)
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                assertEquals(yCbCrToArgb(140, 80, 200), bitmap!!.getPixel(x, y), "x=$x y=$y")
            }
            for (x in 8 until 16) {
                assertEquals(yCbCrToArgb(152, 80, 200), bitmap!!.getPixel(x, y), "x=$x y=$y")
            }
        }
    }

    @Test
    fun `decodes baseline color 420 16x16 jpeg`() {
        val codec = SkJpegKotlinCodec.Decoder.make(colorJpeg(width = 16, height = 16, ySampling = 0x22))!!
        val (bitmap, result) = codec.getImage()

        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(bitmap)
        val expected = intArrayOf(
            yCbCrToArgb(140, 80, 200),
            yCbCrToArgb(152, 80, 200),
            yCbCrToArgb(164, 80, 200),
            yCbCrToArgb(176, 80, 200),
        )
        for (y in 0 until 16) {
            for (x in 0 until 16) {
                val quadrant = (if (y < 8) 0 else 2) + if (x < 8) 0 else 1
                assertEquals(expected[quadrant], bitmap!!.getPixel(x, y), "x=$x y=$y")
            }
        }
    }

    @Test
    fun `rejects exotic color sampling`() {
        assertNull(SkJpegKotlinCodec.Decoder.make(colorJpeg(width = 8, height = 16, ySampling = 0x12)))
        assertNull(SkJpegKotlinCodec.Decoder.make(colorJpeg(width = 16, height = 8, ySampling = 0x21, cbSampling = 0x21)))
    }

    @Test
    fun `parses EXIF orientation from big endian APP1`() {
        val codec = SkJpegKotlinCodec.Decoder.make(
            withAppSegments(
                grayscaleJpeg(width = 8, height = 6),
                exifOrientationSegment(orientation = 6, littleEndian = false),
            ),
        )

        assertNotNull(codec)
        assertEquals(SkEncodedOrigin.kRightTop, codec!!.getOrigin())
        assertEquals(6, codec.getInfo().width)
        assertEquals(8, codec.getInfo().height)
    }

    @Test
    fun `parses EXIF orientation from little endian APP1`() {
        val codec = SkJpegKotlinCodec.Decoder.make(
            withAppSegments(
                grayscaleJpeg(width = 9, height = 5),
                exifOrientationSegment(orientation = 8, littleEndian = true),
            ),
        )

        assertNotNull(codec)
        assertEquals(SkEncodedOrigin.kLeftBottom, codec!!.getOrigin())
        assertEquals(5, codec.getInfo().width)
        assertEquals(9, codec.getInfo().height)
    }

    @Test
    fun `applies all EXIF orientations to decoded pixels`() {
        val width = 16
        val height = 16
        val cases = listOf(
            1 to SkEncodedOrigin.kTopLeft,
            2 to SkEncodedOrigin.kTopRight,
            3 to SkEncodedOrigin.kBottomRight,
            4 to SkEncodedOrigin.kBottomLeft,
            5 to SkEncodedOrigin.kLeftTop,
            6 to SkEncodedOrigin.kRightTop,
            7 to SkEncodedOrigin.kRightBottom,
            8 to SkEncodedOrigin.kLeftBottom,
        )

        for ((exifValue, origin) in cases) {
            val codec = SkJpegKotlinCodec.Decoder.make(
                withAppSegments(
                    colorJpeg(width = width, height = height, ySampling = 0x22),
                    exifOrientationSegment(orientation = exifValue, littleEndian = exifValue % 2 == 0),
                ),
            )

            assertNotNull(codec, "origin=$origin")
            assertEquals(origin, codec!!.getOrigin(), "origin=$origin")
            val (bitmap, result) = codec.getImage()
            assertEquals(SkCodec.Result.kSuccess, result, "origin=$origin")
            assertNotNull(bitmap, "origin=$origin")
            assertEquals(width, bitmap!!.width, "origin=$origin")
            assertEquals(height, bitmap.height, "origin=$origin")

            for (dy in 0 until height) {
                for (dx in 0 until width) {
                    val (sx, sy) = sourcePixelForOrientedDestination(origin, dx, dy, width, height)
                    assertEquals(
                        expectedColor420(sx, sy),
                        bitmap.getPixel(dx, dy),
                        "origin=$origin dx=$dx dy=$dy sx=$sx sy=$sy",
                    )
                }
            }
        }
    }

    @Test
    fun `out of order ICC APP2 chunks do not crash`() {
        val codec = SkJpegKotlinCodec.Decoder.make(
            withAppSegments(
                grayscaleJpeg(width = 8, height = 8),
                iccSegment(index = 2, count = 2, payload = byteArrayOf(4, 5, 6)),
                iccSegment(index = 1, count = 2, payload = byteArrayOf(1, 2, 3)),
            ),
        )

        assertNotNull(codec)
        assertNull(codec!!.getICCProfile())
        val (_, result) = codec.getImage()
        assertEquals(SkCodec.Result.kSuccess, result)
    }

    @Test
    fun `APP2 ICC chunks are reassembled and parsed`() {
        val iccBytes = SkICC.WriteToICC(SkNamedTransferFn.kSRGB, SkNamedGamut.kSRGB)
        val splitAt = iccBytes.size / 2
        val codec = SkJpegKotlinCodec.Decoder.make(
            withAppSegments(
                grayscaleJpeg(width = 8, height = 8),
                iccSegment(index = 1, count = 2, payload = iccBytes.copyOfRange(0, splitAt)),
                iccSegment(index = 2, count = 2, payload = iccBytes.copyOfRange(splitAt, iccBytes.size)),
            ),
        )

        assertNotNull(codec)
        val profile = codec!!.getICCProfile()
        assertNotNull(profile)
        assertEquals(iccBytes.size, profile!!.size)
        assertTrue(profile.hasTrc)
        assertTrue(profile.hasToXYZD50)
        val (_, result) = codec.getImage()
        assertEquals(SkCodec.Result.kSuccess, result)
    }

    @Test
    fun `decodes grayscale restart marker interval`() {
        val codec = SkJpegKotlinCodec.Decoder.make(grayscaleRestartJpeg())!!
        val (bitmap, result) = codec.getImage()

        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(16, bitmap!!.width)
        assertEquals(8, bitmap.height)
        assertEquals(0xFF808080.toInt(), bitmap.getPixel(0, 0))
        assertEquals(0xFF808080.toInt(), bitmap.getPixel(15, 7))
    }

    @Test
    fun `rejects restart marker outside entropy scan`() {
        assertNull(
            SkJpegKotlinCodec.Decoder.make(
                insertAfterSoi(grayscaleJpeg(width = 8, height = 8), byteArrayOf(0xFF.toByte(), 0xD0.toByte())),
            ),
        )
    }

    @Test
    fun `rejects entropy restart marker without DRI`() {
        assertNull(SkJpegKotlinCodec.Decoder.make(grayscaleRestartJpeg(includeDri = false)))
    }

    @Test
    fun `decodes progressive grayscale metadata with empty ac scan`() {
        val codec = SkJpegKotlinCodec.Decoder.make(progressiveGrayscaleJpeg(width = 11, height = 7, includeAcScan = true))

        assertNotNull(codec)
        assertEquals(SkEncodedImageFormat.kJPEG, codec!!.getEncodedFormat())
        assertEquals(11, codec.getInfo().width)
        assertEquals(7, codec.getInfo().height)
        assertEquals(SkColorType.kRGBA_8888, codec.getInfo().colorType)

        val (bitmap, result) = codec.getImage()
        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(0xFF808080.toInt(), bitmap!!.getPixel(10, 6))
    }

    @Test
    fun `decodes progressive grayscale dc first scan`() {
        val codec = SkJpegKotlinCodec.Decoder.make(progressiveGrayscaleJpeg(width = 13, height = 9))

        assertNotNull(codec)
        assertEquals(13, codec!!.getInfo().width)
        assertEquals(9, codec.getInfo().height)

        val (bitmap, result) = codec.getImage()
        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(0xFF808080.toInt(), bitmap!!.getPixel(0, 0))
        assertEquals(0xFF808080.toInt(), bitmap.getPixel(12, 8))
    }

    @Test
    fun `decodes progressive grayscale dc and ac scans`() {
        val codec = SkJpegKotlinCodec.Decoder.make(
            progressiveGrayscaleJpeg(width = 8, height = 8, includeAcScan = true, acScanHasCoefficient = true),
        )

        assertNotNull(codec)
        val (bitmap, result) = codec!!.getImage()
        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertTrue(bitmap!!.getPixel(0, 0) != 0xFF808080.toInt())
        assertTrue(bitmap.getPixel(0, 0) > bitmap.getPixel(7, 0))
    }

    @Test
    fun `rejects invalid progressive scan parameters`() {
        assertNull(
            SkJpegKotlinCodec.Decoder.make(
                progressiveGrayscaleJpeg(width = 8, height = 8, spectralStart = 1, spectralEnd = 0),
            ),
        )
        assertNull(
            SkJpegKotlinCodec.Decoder.make(
                progressiveGrayscaleJpeg(width = 8, height = 8, successiveApprox = 0xE0),
            ),
        )
    }

    private fun grayscaleJpeg(width: Int, height: Int, componentCount: Int = 1): ByteArray {
        val out = ByteArrayOutputStream()
        out.writeMarker(0xD8)
        out.writeSegment(0xDB) {
            write(0)
            repeat(64) { write(1) }
        }
        out.writeSegment(0xC0) {
            write(8)
            writeU16BE(height)
            writeU16BE(width)
            write(componentCount)
            for (id in 1..componentCount) {
                write(id)
                write(0x11)
                write(0)
            }
        }
        out.writeSegment(0xC4) {
            write(0x00)
            write(1)
            repeat(15) { write(0) }
            write(0x00)
        }
        out.writeSegment(0xC4) {
            write(0x10)
            write(1)
            repeat(15) { write(0) }
            write(0x00)
        }
        out.writeSegment(0xDA) {
            write(1)
            write(1)
            write(0x00)
            write(0)
            write(63)
            write(0)
        }
        out.write(entropyForZeroBlocks(((width + 7) / 8) * ((height + 7) / 8)))
        out.writeMarker(0xD9)
        return out.toByteArray()
    }

    private fun progressiveGrayscaleJpeg(
        width: Int,
        height: Int,
        spectralStart: Int = 0,
        spectralEnd: Int = 0,
        successiveApprox: Int = 0,
        includeAcScan: Boolean = false,
        acScanHasCoefficient: Boolean = false,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.writeMarker(0xD8)
        out.writeSegment(0xDB) {
            write(0)
            repeat(64) { write(1) }
        }
        out.writeSegment(0xC2) {
            write(8)
            writeU16BE(height)
            writeU16BE(width)
            write(1)
            write(1)
            write(0x11)
            write(0)
        }
        out.writeSegment(0xC4) {
            write(0x00)
            write(1)
            repeat(15) { write(0) }
            write(0x00)
        }
        out.writeSegment(0xC4) {
            write(0x10)
            if (acScanHasCoefficient) {
                write(1)
                write(1)
                repeat(14) { write(0) }
            } else {
                write(1)
                repeat(15) { write(0) }
            }
            write(0x00)
            if (acScanHasCoefficient) write(0x08)
        }
        out.writeSegment(0xDA) {
            write(1)
            write(1)
            write(0x00)
            write(spectralStart)
            write(spectralEnd)
            write(successiveApprox)
        }
        out.write(entropyForZeroBlocks(((width + 7) / 8) * ((height + 7) / 8)))
        if (includeAcScan) {
            val blockCount = ((width + 7) / 8) * ((height + 7) / 8)
            out.writeSegment(0xDA) {
                write(1)
                write(1)
                write(0x00)
                write(1)
                write(63)
                write(0)
            }
            val bits = if (acScanHasCoefficient) {
                buildString {
                    repeat(blockCount) {
                        append("10")
                        append("11111111")
                        append("0")
                    }
                }
            } else {
                "0".repeat(blockCount)
            }
            out.write(entropyBits(bits))
        }
        out.writeMarker(0xD9)
        return out.toByteArray()
    }

    private fun grayscaleRestartJpeg(includeDri: Boolean = true): ByteArray {
        val out = ByteArrayOutputStream()
        out.writeMarker(0xD8)
        out.writeSegment(0xDB) {
            write(0)
            repeat(64) { write(1) }
        }
        out.writeSegment(0xC0) {
            write(8)
            writeU16BE(8)
            writeU16BE(16)
            write(1)
            write(1)
            write(0x11)
            write(0)
        }
        out.writeSegment(0xC4) {
            write(0x00)
            write(1)
            repeat(15) { write(0) }
            write(0x00)
        }
        out.writeSegment(0xC4) {
            write(0x10)
            write(1)
            repeat(15) { write(0) }
            write(0x00)
        }
        if (includeDri) {
            out.writeSegment(0xDD) {
                writeU16BE(1)
            }
        }
        out.writeSegment(0xDA) {
            write(1)
            write(1)
            write(0x00)
            write(0)
            write(63)
            write(0)
        }
        out.write(entropyBits("00"))
        out.writeMarker(0xD0)
        out.write(entropyBits("00"))
        out.writeMarker(0xD9)
        return out.toByteArray()
    }

    private fun color444Jpeg(ySampling: Int = 0x11): ByteArray {
        return colorJpeg(width = 8, height = 8, ySampling = ySampling)
    }

    private fun colorJpeg(
        width: Int,
        height: Int,
        ySampling: Int,
        cbSampling: Int = 0x11,
        crSampling: Int = 0x11,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.writeMarker(0xD8)
        out.writeSegment(0xDB) {
            write(0)
            repeat(64) { write(1) }
        }
        out.writeSegment(0xC0) {
            write(8)
            writeU16BE(height)
            writeU16BE(width)
            write(3)
            write(1)
            write(ySampling)
            write(0)
            write(2)
            write(cbSampling)
            write(0)
            write(3)
            write(crSampling)
            write(0)
        }
        out.writeSegment(0xC4) {
            write(0x00)
            write(0)
            write(3)
            repeat(14) { write(0) }
            write(0x07)
            write(0x09)
            write(0x0A)
        }
        out.writeSegment(0xC4) {
            write(0x10)
            write(1)
            repeat(15) { write(0) }
            write(0x00)
        }
        out.writeSegment(0xDA) {
            write(3)
            write(1)
            write(0x00)
            write(2)
            write(0x00)
            write(3)
            write(0x00)
            write(0)
            write(63)
            write(0)
        }
        val yBlocksPerMcu = (ySampling ushr 4) * (ySampling and 0x0F)
        val mcuWidth = (ySampling ushr 4) * 8
        val mcuHeight = (ySampling and 0x0F) * 8
        val mcuCount = ((width + mcuWidth - 1) / mcuWidth) * ((height + mcuHeight - 1) / mcuHeight)
        val bits = buildString {
            repeat(mcuCount) {
                repeat(yBlocksPerMcu) {
                    append("00")
                    append("1100000")
                    append("0")
                }
                append("01")
                append("001111111")
                append("0")
                append("10")
                append("1001000000")
                append("0")
            }
        }
        out.write(
            entropyBits(bits),
        )
        out.writeMarker(0xD9)
        return out.toByteArray()
    }

    private fun yCbCrToArgb(y: Int, cb: Int, cr: Int): Int {
        val cbShifted = cb - 128
        val crShifted = cr - 128
        val r = (y + 1.402 * crShifted).roundToInt().coerceIn(0, 255)
        val g = (y - 0.344136 * cbShifted - 0.714136 * crShifted).roundToInt().coerceIn(0, 255)
        val b = (y + 1.772 * cbShifted).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun expectedColor420(x: Int, y: Int): Int {
        val yValues = intArrayOf(140, 152, 164, 176)
        val quadrant = (if (y < 8) 0 else 2) + if (x < 8) 0 else 1
        return yCbCrToArgb(yValues[quadrant], 80, 200)
    }

    private fun sourcePixelForOrientedDestination(
        origin: SkEncodedOrigin,
        dx: Int,
        dy: Int,
        width: Int,
        height: Int,
    ): Pair<Int, Int> = when (origin) {
        SkEncodedOrigin.kTopLeft -> dx to dy
        SkEncodedOrigin.kTopRight -> width - 1 - dx to dy
        SkEncodedOrigin.kBottomRight -> width - 1 - dx to height - 1 - dy
        SkEncodedOrigin.kBottomLeft -> dx to height - 1 - dy
        SkEncodedOrigin.kLeftTop -> dy to dx
        SkEncodedOrigin.kRightTop -> dy to height - 1 - dx
        SkEncodedOrigin.kRightBottom -> width - 1 - dy to height - 1 - dx
        SkEncodedOrigin.kLeftBottom -> width - 1 - dy to dx
    }

    private fun entropyForZeroBlocks(blockCount: Int): ByteArray {
        val bitCount = blockCount * 2
        val byteCount = (bitCount + 7) / 8
        val out = ByteArray(byteCount)
        for (bit in 0 until bitCount) {
            val byte = bit / 8
            val shift = 7 - (bit and 7)
            out[byte] = (out[byte].toInt() and (1 shl shift).inv()).toByte()
        }
        for (bit in bitCount until byteCount * 8) {
            val byte = bit / 8
            val shift = 7 - (bit and 7)
            out[byte] = (out[byte].toInt() or (1 shl shift)).toByte()
        }
        return out
    }

    private fun entropyBits(bits: String): ByteArray {
        val byteCount = (bits.length + 7) / 8
        val out = ByteArray(byteCount)
        for (bit in bits.indices) {
            if (bits[bit] == '1') {
                val byte = bit / 8
                val shift = 7 - (bit and 7)
                out[byte] = (out[byte].toInt() or (1 shl shift)).toByte()
            }
        }
        for (bit in bits.length until byteCount * 8) {
            val byte = bit / 8
            val shift = 7 - (bit and 7)
            out[byte] = (out[byte].toInt() or (1 shl shift)).toByte()
        }
        return out
    }

    private fun withAppSegments(jpeg: ByteArray, vararg segments: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(jpeg, 0, 2)
        for (segment in segments) out.write(segment)
        out.write(jpeg, 2, jpeg.size - 2)
        return out.toByteArray()
    }

    private fun insertAfterSoi(jpeg: ByteArray, bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(jpeg, 0, 2)
        out.write(bytes)
        out.write(jpeg, 2, jpeg.size - 2)
        return out.toByteArray()
    }

    private fun exifOrientationSegment(orientation: Int, littleEndian: Boolean): ByteArray {
        return segmentBytes(0xE1) {
            write(byteArrayOf(0x45, 0x78, 0x69, 0x66, 0x00, 0x00))
            if (littleEndian) {
                write('I'.code)
                write('I'.code)
                writeU16LE(0x002A)
                writeU32LE(8)
                writeU16LE(1)
                writeU16LE(0x0112)
                writeU16LE(3)
                writeU32LE(1)
                writeU16LE(orientation)
                writeU16LE(0)
                writeU32LE(0)
            } else {
                write('M'.code)
                write('M'.code)
                writeU16BE(0x002A)
                writeU32BE(8)
                writeU16BE(1)
                writeU16BE(0x0112)
                writeU16BE(3)
                writeU32BE(1)
                writeU16BE(orientation)
                writeU16BE(0)
                writeU32BE(0)
            }
        }
    }

    private fun iccSegment(index: Int, count: Int, payload: ByteArray): ByteArray {
        return segmentBytes(0xE2) {
            write(byteArrayOf(0x49, 0x43, 0x43, 0x5F, 0x50, 0x52, 0x4F, 0x46, 0x49, 0x4C, 0x45, 0x00))
            write(index)
            write(count)
            write(payload)
        }
    }

    private fun segmentBytes(marker: Int, writePayload: ByteArrayOutputStream.() -> Unit): ByteArray {
        val out = ByteArrayOutputStream()
        out.writeSegment(marker, writePayload)
        return out.toByteArray()
    }

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
        write((value ushr 8) and 0xFF)
        write(value and 0xFF)
    }

    private fun ByteArrayOutputStream.writeU16LE(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
    }

    private fun ByteArrayOutputStream.writeU32BE(value: Int) {
        write((value ushr 24) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 8) and 0xFF)
        write(value and 0xFF)
    }

    private fun ByteArrayOutputStream.writeU32LE(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 24) and 0xFF)
    }
}
