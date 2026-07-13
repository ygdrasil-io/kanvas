package org.graphiks.kanvas.codec.jpeg

import java.io.ByteArrayOutputStream
import org.graphiks.kanvas.codec.Codec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImageInfo

class JpegLosslessDecodeTest {

    @Test
    fun `decodes SOF3 Huffman predictors one through seven`() {
        val expected = intArrayOf(
            128, 140, 132,
            120, 130, 126,
            122, 124, 128,
        )

        for (predictor in 1..7) {
            val data = losslessJpeg(
                precision = 8,
                predictor = predictor,
                pointTransform = 0,
                width = 3,
                height = 3,
                components = listOf(FixtureComponent(1, expected)),
            )

            assertEquals(expected.toList(), losslessSamples(data).planes.single().toList(), "predictor=$predictor")
            assertPixel(data, 2, 2, 0xFF808080.toInt(), "predictor=$predictor")
        }
    }

    @Test
    fun `decodes SOF3 point transforms at eight twelve and sixteen bit precision`() {
        val cases = listOf(
            LosslessCase(8, 2, intArrayOf(128, 132, 136, 140)),
            LosslessCase(12, 3, intArrayOf(2_048, 2_056, 2_040, 2_048)),
            LosslessCase(16, 4, intArrayOf(32_768, 32_784, 32_752, 32_768)),
        )

        for (case in cases) {
            val data = losslessJpeg(
                precision = case.precision,
                predictor = 7,
                pointTransform = case.pointTransform,
                width = 2,
                height = 2,
                components = listOf(FixtureComponent(1, case.samples)),
            )
            val samples = losslessSamples(data)

            assertEquals(case.precision, samples.precision)
            assertEquals(case.samples.toList(), samples.planes.single().toList(), "precision=${case.precision}")
        }

        val sixteenBit = losslessJpeg(
            precision = 16,
            predictor = 1,
            pointTransform = 0,
            width = 1,
            height = 1,
            components = listOf(FixtureComponent(1, intArrayOf(32_768))),
        )
        val codec = JpegCodec.Decoder.make(sixteenBit)
        assertNotNull(codec)
        val info = SkImageInfo.Make(
            width = 1,
            height = 1,
            colorType = SkColorType.kRGBA_F16Norm,
            alphaType = SkAlphaType.kPremul,
            colorSpace = codec!!.getInfo().colorSpace,
        )
        val bitmap = SkBitmap(1, 1, info.colorSpace, info.colorType)

        assertEquals(Codec.Result.kSuccess, codec.getPixels(info, bitmap))
        val pixel = FloatArray(4)
        bitmap.getPixelF16(0, 0, pixel)
        assertEquals(32_768f / 65_535f, pixel[0], 0.00025f)
        assertEquals(pixel[0], pixel[1], 0.00025f)
        assertEquals(pixel[0], pixel[2], 0.00025f)
    }

    @Test
    fun `decodes multi scan SOF3 components across restart intervals`() {
        val data = losslessJpeg(
            precision = 8,
            predictor = 7,
            pointTransform = 0,
            width = 4,
            height = 2,
            components = listOf(
                FixtureComponent('R'.code, intArrayOf(128, 130, 132, 134, 136, 138, 140, 142)),
                FixtureComponent('G'.code, intArrayOf(64, 66, 68, 70, 72, 74, 76, 78)),
                FixtureComponent('B'.code, intArrayOf(192, 190, 188, 186, 184, 182, 180, 178)),
            ),
            scans = listOf(listOf(0), listOf(1), listOf(2)),
            restartInterval = 2,
            adobeTransform = 0,
        )
        val frame = parseJpeg(data, JpegDocument.open(data).document!!.metadata)
        assertNotNull(frame)
        assertEquals(3, frame!!.scans.size)

        val samples = decodeLossless(frame)
        assertEquals(listOf(128, 130, 132, 134, 136, 138, 140, 142), samples.planes[0].toList())
        assertEquals(listOf(64, 66, 68, 70, 72, 74, 76, 78), samples.planes[1].toList())
        assertEquals(listOf(192, 190, 188, 186, 184, 182, 180, 178), samples.planes[2].toList())
        assertPixel(data, 3, 1, 0xFF8E4EB2.toInt())
    }

    @Test
    fun `reports stable lossless sample range diagnostic`() {
        val data = losslessJpeg(
            precision = 8,
            predictor = 1,
            pointTransform = 0,
            width = 1,
            height = 1,
            components = listOf(FixtureComponent(1, intArrayOf(256))),
        )
        val document = JpegDocument.open(data).document!!

        val result = document.decode(JpegDecodeRequest(SkColorType.kRGBA_8888, null))

        assertEquals("jpeg.lossless.sample.range", result.diagnostic?.code)
    }

    private fun losslessSamples(data: ByteArray): DecodedJpegSamples {
        val document = JpegDocument.open(data).document!!
        val frame = parseJpeg(data, document.metadata)!!
        return decodeLossless(frame)
    }

    private fun assertPixel(data: ByteArray, x: Int, y: Int, expected: Int, label: String = "") {
        val codec = JpegCodec.Decoder.make(data)
        assertNotNull(codec, label)
        val (bitmap, result) = codec!!.getImage()
        assertEquals(Codec.Result.kSuccess, result, label)
        assertEquals(expected, bitmap!!.getPixel(x, y), label)
    }

    private fun losslessJpeg(
        precision: Int,
        predictor: Int,
        pointTransform: Int,
        width: Int,
        height: Int,
        components: List<FixtureComponent>,
        scans: List<List<Int>> = listOf(components.indices.toList()),
        restartInterval: Int = 0,
        adobeTransform: Int? = null,
    ): ByteArray {
        require(predictor in 1..7)
        require(pointTransform in 0 until precision)
        require(components.all { it.samples.size == width * height })
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
        out.writeSegment(0xC3) {
            write(precision)
            writeU16BE(height)
            writeU16BE(width)
            write(components.size)
            for (component in components) {
                write(component.id)
                write(0x11)
                write(0)
            }
        }
        out.writeSegment(0xC4) {
            write(0x00)
            repeat(4) { write(0) }
            write(17)
            repeat(11) { write(0) }
            for (symbol in 0..16) write(symbol)
        }
        if (restartInterval > 0) out.writeSegment(0xDD) { writeU16BE(restartInterval) }

        for (scan in scans) {
            out.writeSegment(0xDA) {
                write(scan.size)
                for (index in scan) {
                    write(components[index].id)
                    write(0)
                }
                write(predictor)
                write(0)
                write(pointTransform)
            }
            val mcuBits = ArrayList<String>(width * height)
            for (pixel in 0 until width * height) {
                mcuBits += buildString {
                    for (index in scan) {
                        val restart = restartInterval > 0 && pixel % restartInterval == 0
                        append(
                            encodeLosslessSample(
                                samples = components[index].samples,
                                pixel = pixel,
                                width = width,
                                precision = precision,
                                predictor = predictor,
                                pointTransform = pointTransform,
                                restart = restart,
                            ),
                        )
                    }
                }
            }
            out.write(entropyMcuBits(mcuBits, restartInterval))
        }
        out.writeMarker(0xD9)
        return out.toByteArray()
    }

    private fun encodeLosslessSample(
        samples: IntArray,
        pixel: Int,
        width: Int,
        precision: Int,
        predictor: Int,
        pointTransform: Int,
        restart: Boolean,
    ): String {
        val predicted = if (restart || pixel == 0) {
            1 shl (precision - 1)
        } else {
            val x = pixel % width
            when {
                x == 0 -> samples[pixel - width]
                pixel < width -> samples[pixel - 1]
                else -> losslessPredictor(
                    predictor,
                    left = samples[pixel - 1] shr pointTransform,
                    above = samples[pixel - width] shr pointTransform,
                    upperLeft = samples[pixel - width - 1] shr pointTransform,
                ) shl pointTransform
            }
        }
        val difference = (samples[pixel] - predicted) shr pointTransform
        val category = category(difference)
        return category.toString(2).padStart(5, '0') + amplitude(difference, category)
    }

    private fun losslessPredictor(predictor: Int, left: Int, above: Int, upperLeft: Int): Int = when (predictor) {
        1 -> left
        2 -> above
        3 -> upperLeft
        4 -> left + above - upperLeft
        5 -> left + ((above - upperLeft) shr 1)
        6 -> above + ((left - upperLeft) shr 1)
        7 -> (left + above) shr 1
        else -> error("unsupported predictor")
    }

    private fun category(value: Int): Int =
        if (value == 0) 0 else 32 - Integer.numberOfLeadingZeros(kotlin.math.abs(value))

    private fun amplitude(value: Int, category: Int): String {
        if (category == 0) return ""
        val encoded = if (value >= 0) value else value + (1 shl category) - 1
        return encoded.toString(2).padStart(category, '0')
    }

    private fun entropyMcuBits(mcuBits: List<String>, restartInterval: Int): ByteArray {
        if (restartInterval == 0) return entropyBits(mcuBits.joinToString(separator = ""))
        return ByteArrayOutputStream().apply {
            for (start in mcuBits.indices step restartInterval) {
                val end = (start + restartInterval).coerceAtMost(mcuBits.size)
                write(entropyBits(mcuBits.subList(start, end).joinToString(separator = "")))
                if (end < mcuBits.size) writeMarker(0xD0 + (start / restartInterval and 7))
            }
        }.toByteArray()
    }

    private fun entropyBits(bits: String): ByteArray {
        val bytes = ByteArray((bits.length + 7) / 8)
        for (index in bits.indices) {
            if (bits[index] == '1') bytes[index / 8] = (bytes[index / 8].toInt() or (1 shl (7 - (index and 7)))).toByte()
        }
        for (index in bits.length until bytes.size * 8) {
            bytes[index / 8] = (bytes[index / 8].toInt() or (1 shl (7 - (index and 7)))).toByte()
        }
        return ByteArrayOutputStream().apply {
            for (byte in bytes) {
                write(byte.toInt() and 0xFF)
                if (byte == 0xFF.toByte()) write(0)
            }
        }.toByteArray()
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
        write(value ushr 8)
        write(value and 0xFF)
    }

    private data class FixtureComponent(val id: Int, val samples: IntArray)

    private data class LosslessCase(
        val precision: Int,
        val pointTransform: Int,
        val samples: IntArray,
    )
}
