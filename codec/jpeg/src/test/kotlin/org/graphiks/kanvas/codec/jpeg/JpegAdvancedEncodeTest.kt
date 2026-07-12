package org.graphiks.kanvas.codec.jpeg

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path

class JpegAdvancedEncodeTest {

    @Test
    fun `arithmetic sequential entropy round trips DC and AC decisions`() {
        val coefficients = IntArray(64).also {
            it[0] = -5
            it[1] = 3
            it[4] = -7
        }
        val encoded = encodeArithmeticEntropy(coefficients)

        val decoder = ArithmeticDecoder(encoded)
        assertEquals(-5, decoder.decodeDcDifference(0, 0, 0, 1).value)
        val decodedAc = IntArray(64)
        decoder.decodeAcInitial(0, 1, 63, 5) { coefficient, value -> decodedAc[coefficient] = value }
        assertArrayEquals(coefficients.copyOfRange(1, 64), decodedAc.copyOfRange(1, 64))
    }

    @Test
    fun `arithmetic sequential entropy preserves DC magnitudes two three and four`() {
        for (expected in listOf(2, 3, 4)) {
            val decoded = ArithmeticDecoder(encodeArithmeticEntropy(IntArray(64).also { it[0] = expected }))
                .decodeDcDifference(0, 0, 0, 1)
            assertEquals(expected, decoded.value, "DC=$expected")
        }
    }

    @Test
    fun `arithmetic sequential entropy stuffs ff data bytes`() {
        val stuffingCandidate = (-4_096..4_096).firstNotNullOfOrNull { dc ->
            val bytes = encodeArithmeticEntropy(IntArray(64).also {
                it[0] = dc
                it[1] = (dc * 29) % 1_021
                it[4] = -((dc * 17) % 509)
            })
            bytes.takeIf { entropyData ->
                (0 until entropyData.size - 1).any { index ->
                    entropyData[index] == 0xFF.toByte() && entropyData[index + 1] == 0.toByte()
                }
            }
        }

        assertNotNull(stuffingCandidate)
        for (index in stuffingCandidate!!.indices) {
            if (stuffingCandidate[index] == 0xFF.toByte()) {
                assertTrue(index + 1 < stuffingCandidate.size)
                assertEquals(0, stuffingCandidate[index + 1].toInt() and 0xFF)
            }
        }
    }

    @Test
    fun `progressive grayscale DC plus AC script emits SOF2 and decodes`() {
        val source = grayscale(17, 9)

        val bytes = JpegEncoder.encode(
            source,
            JpegEncoder.Options(
                process = JpegEncodeProcess.ProgressiveHuffman,
                colorModel = JpegEncodeColorModel.Grayscale,
                sampling = JpegSampling.S444,
                progressiveScans = listOf(
                    JpegProgressiveScan(componentIds = listOf(1), spectralStart = 0, spectralEnd = 0),
                    JpegProgressiveScan(componentIds = listOf(1), spectralStart = 1, spectralEnd = 63),
                ),
            ),
        )

        assertNotNull(bytes)
        assertEquals(0xC2, firstMarker(bytes!!, setOf(0xC2)))
        assertEquals(2, JpegDocument.open(bytes).document!!.segments.count { it.marker == 0xDA })
        assertReasonableRoundTrip(source, bytes)
    }

    @Test
    fun `progressive 12-bit grayscale round trips a category 12 DC difference`() {
        val source = bitmap(16, 8) { x, _ ->
            val value = if (x < 8) 0 else 0xFF
            0xFF000000.toInt() or (value shl 16) or (value shl 8) or value
        }

        val bytes = JpegEncoder.encode(
            source,
            JpegEncoder.Options(
                quality = 100,
                process = JpegEncodeProcess.ProgressiveHuffman,
                precision = 12,
                colorModel = JpegEncodeColorModel.Grayscale,
                sampling = JpegSampling.S444,
                progressiveScans = listOf(
                    JpegProgressiveScan(componentIds = listOf(1), spectralStart = 0, spectralEnd = 0),
                ),
            ),
        )

        assertNotNull(bytes)
        assertReasonableRoundTrip(source, bytes!!)
    }

    @Test
    fun `progressive 12-bit grayscale round trips an AC category 11 or greater`() {
        val source = bitmap(8, 8) { x, y ->
            val value = if ((x + y) and 1 == 0) 0 else 0xFF
            0xFF000000.toInt() or (value shl 16) or (value shl 8) or value
        }

        val bytes = JpegEncoder.encode(
            source,
            JpegEncoder.Options(
                quality = 100,
                process = JpegEncodeProcess.ProgressiveHuffman,
                precision = 12,
                colorModel = JpegEncodeColorModel.Grayscale,
                sampling = JpegSampling.S444,
                progressiveScans = listOf(
                    JpegProgressiveScan(componentIds = listOf(1), spectralStart = 0, spectralEnd = 0),
                    JpegProgressiveScan(componentIds = listOf(1), spectralStart = 1, spectralEnd = 63),
                ),
            ),
        )

        assertNotNull(bytes)
        val (_, result) = JpegCodec.Decoder.make(bytes!!)!!.getImage()
        assertEquals(org.graphiks.kanvas.codec.Codec.Result.kSuccess, result)
        assertReasonableRoundTrip(source, bytes)
    }

    @Test
    fun `progressive color script writes restart markers and all declared scans`() {
        val source = color(17, 9)
        val script = listOf(
            JpegProgressiveScan(componentIds = listOf(1, 2, 3), spectralStart = 0, spectralEnd = 0),
            JpegProgressiveScan(componentIds = listOf(1), spectralStart = 1, spectralEnd = 63),
            JpegProgressiveScan(componentIds = listOf(2), spectralStart = 1, spectralEnd = 63),
            JpegProgressiveScan(componentIds = listOf(3), spectralStart = 1, spectralEnd = 63),
        )

        val bytes = JpegEncoder.encode(
            source,
            JpegEncoder.Options(
                process = JpegEncodeProcess.ProgressiveHuffman,
                sampling = JpegSampling.S420,
                restartInterval = 1,
                progressiveScans = script,
            ),
        )

        assertNotNull(bytes)
        assertEquals(0xC2, firstMarker(bytes!!, setOf(0xC2)))
        assertEquals(script.size, JpegDocument.open(bytes).document!!.segments.count { it.marker == 0xDA })
        assertTrue(restartMarkers(bytes).isNotEmpty())
        assertReasonableRoundTrip(source, bytes)
    }

    @Test
    fun `lossless grayscale encodes every predictor and supported precision exactly`() {
        val source = grayscale(5, 3)
        for (precision in listOf(8, 12, 16)) {
            for (predictor in 1..7) {
                val bytes = JpegEncoder.encode(
                    source,
                    JpegEncoder.Options(
                        process = JpegEncodeProcess.LosslessHuffman,
                        precision = precision,
                        colorModel = JpegEncodeColorModel.Grayscale,
                        sampling = JpegSampling.S444,
                        restartInterval = 2,
                        losslessParameters = JpegLosslessParameters(predictor = predictor, pointTransform = 0),
                    ),
                )

                assertNotNull(bytes, "precision=$precision predictor=$predictor")
                assertEquals(0xC3, firstMarker(bytes!!, setOf(0xC3)))
                val decoded = decodeLossless(parseJpeg(bytes, JpegDocument.open(bytes).document!!.metadata)!!)
                assertEquals(precision, decoded.precision)
                assertArrayEquals(expectedGray(source, precision), decoded.planes.single(), "precision=$precision predictor=$predictor")
            }
        }
    }

    @Test
    fun `lossless RGB remains exact with restart markers at every supported precision`() {
        val source = color(5, 3)
        for (precision in listOf(8, 12, 16)) {
            for (predictor in 1..7) {
                val bytes = JpegEncoder.encode(
                    source,
                    JpegEncoder.Options(
                        process = JpegEncodeProcess.LosslessHuffman,
                        precision = precision,
                        colorModel = JpegEncodeColorModel.Rgb,
                        sampling = JpegSampling.S444,
                        restartInterval = 2,
                        losslessParameters = JpegLosslessParameters(predictor = predictor, pointTransform = 0),
                    ),
                )

                assertNotNull(bytes, "precision=$precision predictor=$predictor")
                assertEquals(0xC3, firstMarker(bytes!!, setOf(0xC3)))
                assertTrue(restartMarkers(bytes).isNotEmpty())
                val decoded = decodeLossless(parseJpeg(bytes, JpegDocument.open(bytes).document!!.metadata)!!)
                assertArrayEquals(expectedChannel(source, precision) { it ushr 16 and 0xFF }, decoded.planes[0], "R precision=$precision predictor=$predictor")
                assertArrayEquals(expectedChannel(source, precision) { it ushr 8 and 0xFF }, decoded.planes[1], "G precision=$precision predictor=$predictor")
                assertArrayEquals(expectedChannel(source, precision) { it and 0xFF }, decoded.planes[2], "B precision=$precision predictor=$predictor")
                val (bitmap, result) = JpegCodec.Decoder.make(bytes)!!.getImage()
                assertEquals(org.graphiks.kanvas.codec.Codec.Result.kSuccess, result)
                assertArrayEquals(source.pixels, bitmap!!.pixels, "RGB APP14 precision=$precision predictor=$predictor")
            }
        }
    }

    @Test
    fun `lossless point transform explicitly truncates only transformed low bits`() {
        val source = grayscale(5, 3)
        val bytes = JpegEncoder.encode(
            source,
            JpegEncoder.Options(
                process = JpegEncodeProcess.LosslessHuffman,
                precision = 12,
                colorModel = JpegEncodeColorModel.Grayscale,
                sampling = JpegSampling.S444,
                restartInterval = 2,
                losslessParameters = JpegLosslessParameters(predictor = 7, pointTransform = 3),
            ),
        )!!

        val decoded = decodeLossless(parseJpeg(bytes, JpegDocument.open(bytes).document!!.metadata)!!)
        assertArrayEquals(expectedGray(source, precision = 12, pointTransform = 3), decoded.planes.single())
    }

    @Test
    fun `arithmetic sequential writes SOF9 DAC DRI and restart markers for grayscale and color at 8 and 12 bit`() {
        val cases = listOf(
            Triple(grayscale(17, 9), JpegEncodeColorModel.Grayscale, JpegSampling.S444),
            Triple(color(17, 9), JpegEncodeColorModel.YCbCr, JpegSampling.S420),
        )

        for ((source, colorModel, sampling) in cases) {
            for (precision in listOf(8, 12)) {
                val bytes = JpegEncoder.encode(
                    source,
                    JpegEncoder.Options(
                        process = JpegEncodeProcess.SequentialArithmetic,
                        precision = precision,
                        colorModel = colorModel,
                        sampling = sampling,
                        restartInterval = 1,
                    ),
                )

                assertNotNull(bytes, "$colorModel precision=$precision")
                val document = JpegDocument.open(bytes!!).document!!
                assertEquals(0xC9, firstMarker(bytes, setOf(0xC9)))
                assertTrue(document.segments.any { it.marker == 0xCC }, "$colorModel precision=$precision")
                assertTrue(document.segments.any { it.marker == 0xDD }, "$colorModel precision=$precision")
                assertTrue(document.segments.none { it.marker == 0xC4 }, "$colorModel precision=$precision")
                assertTrue(restartMarkers(bytes).isNotEmpty(), "$colorModel precision=$precision")
                assertReasonableRoundTrip(source, bytes, "$colorModel precision=$precision")
            }
        }
    }

    @Test
    fun `opt in djpeg oracle decodes generated SOF9`() {
        val configuredOracle = System.getProperty("kanvas.jpeg.oracle.djpeg").orEmpty()
        assumeTrue(
            configuredOracle.isNotBlank(),
            "Set -PjpegOracleDjpeg=/absolute/path/to/djpeg to enable the external SOF9 oracle",
        )
        val oracle = Path.of(configuredOracle)
        assumeTrue(Files.isExecutable(oracle), "djpeg oracle is not executable: $oracle")
        val source = grayscale(17, 9)
        val encoded = JpegEncoder.encode(
            source,
            JpegEncoder.Options(
                process = JpegEncodeProcess.SequentialArithmetic,
                colorModel = JpegEncodeColorModel.Grayscale,
                sampling = JpegSampling.S444,
                restartInterval = 1,
            ),
        )!!
        val jpeg = Files.createTempFile("kanvas-sof9-oracle-", ".jpg")
        try {
            Files.write(jpeg, encoded)
            val process = ProcessBuilder(oracle.toString(), "-pnm", jpeg.toString())
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.readBytes()
            assertEquals(0, process.waitFor(), output.decodeToString())
            assertTrue(output.size > 16)
            assertEquals('P'.code, output[0].toInt() and 0xFF)
            assertEquals('5'.code, output[1].toInt() and 0xFF)
        } finally {
            Files.deleteIfExists(jpeg)
        }
    }

    @Test
    fun `advanced unsupported configurations refuse rather than falling back to sequential`() {
        val source = color(8, 8)

        assertNull(JpegEncoder.encode(source, JpegEncoder.Options(process = JpegEncodeProcess.ProgressiveHuffman)))
        assertNull(
            JpegEncoder.encode(
                source,
                JpegEncoder.Options(
                    process = JpegEncodeProcess.ProgressiveHuffman,
                    progressiveScans = listOf(
                        JpegProgressiveScan(componentIds = listOf(1, 2, 3), spectralStart = 0, spectralEnd = 0, successiveLow = 1),
                    ),
                ),
            ),
        )
        assertNull(
            JpegEncoder.encode(
                source,
                JpegEncoder.Options(
                    process = JpegEncodeProcess.ProgressiveHuffman,
                    progressiveScans = listOf(
                        JpegProgressiveScan(componentIds = listOf(1), spectralStart = 1, spectralEnd = 63),
                        JpegProgressiveScan(componentIds = listOf(1, 2, 3), spectralStart = 0, spectralEnd = 0),
                    ),
                ),
            ),
        )
        assertNull(
            JpegEncoder.encode(
                source,
                JpegEncoder.Options(
                    process = JpegEncodeProcess.ProgressiveHuffman,
                    progressiveScans = listOf(
                        JpegProgressiveScan(componentIds = listOf(1), spectralStart = 0, spectralEnd = 0),
                    ),
                ),
            ),
        )
        assertNull(
            JpegEncoder.encode(
                source,
                JpegEncoder.Options(
                    process = JpegEncodeProcess.ProgressiveHuffman,
                    progressiveScans = listOf(
                        JpegProgressiveScan(componentIds = listOf(1, 2, 3), spectralStart = 0, spectralEnd = 0),
                        JpegProgressiveScan(componentIds = listOf(1, 2), spectralStart = 1, spectralEnd = 63),
                    ),
                ),
            ),
        )
        assertNull(
            JpegEncoder.encode(
                source,
                JpegEncoder.Options(
                    process = JpegEncodeProcess.ProgressiveHuffman,
                    progressiveScans = listOf(
                        JpegProgressiveScan(componentIds = listOf(1, 2, 3), spectralStart = 0, spectralEnd = 0),
                        JpegProgressiveScan(componentIds = listOf(1, 2, 3), spectralStart = 0, spectralEnd = 0),
                    ),
                ),
            ),
        )
        assertNull(
            JpegEncoder.encode(
                source,
                JpegEncoder.Options(
                    process = JpegEncodeProcess.LosslessHuffman,
                    losslessParameters = JpegLosslessParameters(predictor = 1, pointTransform = 0),
                    sampling = JpegSampling.S420,
                ),
            ),
        )
        assertNull(
            JpegEncoder.encode(
                source,
                JpegEncoder.Options(
                    process = JpegEncodeProcess.LosslessHuffman,
                    colorModel = JpegEncodeColorModel.Grayscale,
                    sampling = JpegSampling.S444,
                    losslessParameters = JpegLosslessParameters(predictor = 1, pointTransform = 8),
                ),
            ),
        )
        assertNull(
            JpegEncoder.encode(
                source,
                JpegEncoder.Options(
                    process = JpegEncodeProcess.LosslessHuffman,
                    sampling = JpegSampling.S444,
                    losslessParameters = JpegLosslessParameters(predictor = 1, pointTransform = 0),
                ),
            ),
        )
        assertNull(JpegEncoder.encode(source, JpegEncoder.Options(process = JpegEncodeProcess.DifferentialLosslessHuffman)))
        assertNull(
            JpegEncoder.encode(
                source,
                JpegEncoder.Options(hierarchy = listOf(JpegHierarchyLevel(1, 2, JpegEncodeProcess.SequentialHuffman))),
            ),
        )
    }

    private fun grayscale(width: Int, height: Int): SkBitmap = bitmap(width, height) { x, y ->
        val value = (x * 29 + y * 41) and 0xFF
        0xFF000000.toInt() or (value shl 16) or (value shl 8) or value
    }

    private fun color(width: Int, height: Int): SkBitmap = bitmap(width, height) { x, y ->
        0xFF000000.toInt() or
            ((x * 47 and 0xFF) shl 16) or
            ((y * 73 and 0xFF) shl 8) or
            ((x * 19 + y * 31) and 0xFF)
    }

    private fun bitmap(width: Int, height: Int, pixel: (Int, Int) -> Int): SkBitmap =
        SkBitmap(width, height, SkColorSpace.makeSRGB(), SkColorType.kRGBA_8888).also { bitmap ->
            for (y in 0 until height) for (x in 0 until width) bitmap.setPixel(x, y, pixel(x, y))
        }

    private fun expectedGray(source: SkBitmap, precision: Int, pointTransform: Int = 0): IntArray =
        expectedChannel(source, precision, pointTransform) { pixel -> pixel and 0xFF }

    private fun expectedChannel(
        source: SkBitmap,
        precision: Int = 8,
        pointTransform: Int = 0,
        component: (Int) -> Int,
    ): IntArray = IntArray(source.width * source.height) { index ->
        val scaled = (component(source.pixels[index]) * ((1 shl precision) - 1) + 127) / 255
        (scaled ushr pointTransform) shl pointTransform
    }

    private fun encodeArithmeticEntropy(coefficients: IntArray): ByteArray {
        val encoded = ByteArrayOutputStream()
        ArithmeticEncoder(encoded, componentCount = 1).apply {
            encodeSequentialBlock(
                coefficients = coefficients,
                component = 0,
                dcTable = 0,
                acTable = 0,
                dcLower = 0,
                dcUpper = 1,
                acK = 5,
            )
            finish()
        }
        return encoded.toByteArray()
    }

    private fun assertReasonableRoundTrip(source: SkBitmap, bytes: ByteArray, label: String = "") {
        val (decoded, result) = JpegCodec.Decoder.make(bytes)!!.getImage()
        assertEquals(org.graphiks.kanvas.codec.Codec.Result.kSuccess, result)
        var absoluteError = 0L
        for (index in source.pixels.indices) {
            val expected = source.pixels[index]
            val actual = decoded!!.pixels[index]
            absoluteError += kotlin.math.abs((expected ushr 16 and 0xFF) - (actual ushr 16 and 0xFF))
            absoluteError += kotlin.math.abs((expected ushr 8 and 0xFF) - (actual ushr 8 and 0xFF))
            absoluteError += kotlin.math.abs((expected and 0xFF) - (actual and 0xFF))
        }
        assertTrue(absoluteError / (source.width * source.height * 3) < 45, "$label mean RGB error: $absoluteError")
    }

    private fun firstMarker(bytes: ByteArray, markers: Set<Int>): Int {
        var offset = 2
        while (offset + 3 < bytes.size) {
            val marker = bytes[offset + 1].toInt() and 0xFF
            if (marker in markers) return marker
            offset += 2 + u16(bytes, offset + 2)
        }
        error("marker not found")
    }

    private fun restartMarkers(bytes: ByteArray): List<Int> = buildList {
        for (offset in 0 until bytes.size - 1) {
            if (bytes[offset] == 0xFF.toByte() && bytes[offset + 1] in 0xD0.toByte()..0xD7.toByte()) {
                add((bytes[offset + 1].toInt() and 0xFF) - 0xD0)
            }
        }
    }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
}
