package org.graphiks.kanvas.codec.jpegls

import java.util.Base64
import java.nio.file.Files
import kotlin.math.abs
import org.graphiks.kanvas.codec.Codec
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.skia.foundation.SkBitmap

class JpegLsCodecTest {

    @Test
    fun `detects only JPEG-LS SOF55 streams`() {
        assertTrue(JpegLsCodec.Decoder.matches(CHARLS_RUN_FIXTURE))
        assertFalse(JpegLsCodec.Decoder.matches(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xC0.toByte())))
        assertTrue(JpegLsCodec.Decoder.matches(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xF7.toByte())))
    }

    @Test
    fun `claims SOF55 before the classic JPEG provider without capturing SOF0`() {
        val names = Codec.Decoders.all().map { it.name }

        assertTrue("jpeg-ls" in names)
        assertFalse(JpegLsCodec.Decoder.matches(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xC0.toByte(), 0x00, 0x11)))
    }

    @Test
    fun `opens and decodes a CharLS lossless run-mode fixture exactly`() {
        val codec = Codec.MakeFromData(CHARLS_RUN_FIXTURE)

        assertNotNull(codec)
        assertEquals(8, codec!!.getInfo().width)
        assertEquals(4, codec.getInfo().height)
        val (bitmap, result) = codec.getImage()
        assertEquals(Codec.Result.kSuccess, result, decodeDiagnostic(CHARLS_RUN_FIXTURE))
        assertNotNull(bitmap)
        for (y in 0 until 4) {
            for (x in 0 until 8) {
                assertEquals(0xFF414141.toInt(), bitmap!!.getPixel(x, y), "x=$x y=$y")
            }
        }
    }

    @Test
    fun `decodes a CharLS regular-mode fixture exactly`() {
        val codec = Codec.MakeFromData(CHARLS_REGULAR_FIXTURE)
        val (bitmap, result) = requireNotNull(codec).getImage()

        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        val expected = intArrayOf(
            0x41, 0x71, 0x41, 0x71, 0x41, 0x71, 0x41, 0x71,
            0x41, 0x71, 0x41, 0x71, 0x41, 0x71, 0x41, 0x71,
            0x71, 0x41, 0x71, 0x41, 0x71, 0x41, 0x71, 0x41,
            0x71, 0x41, 0x71, 0x41, 0x71, 0x41, 0x71, 0x41,
        )
        expected.forEachIndexed { index, sample ->
            assertEquals(
                0xFF000000.toInt() or (sample shl 16) or (sample shl 8) or sample,
                bitmap!!.getPixel(index % 8, index / 8),
                "index=$index",
            )
        }
    }

    @Test
    fun `decodes a CharLS RGB line-interleaved fixture exactly`() {
        val document = requireNotNull(JpegLsDocument.open(CHARLS_RGB_LINE_FIXTURE).document)
        val codec = requireNotNull(Codec.MakeFromData(CHARLS_RGB_LINE_FIXTURE))
        val (bitmap, result) = codec.getImage()

        assertEquals(Codec.Result.kSuccess, result)
        assertEquals(3, document.componentCount)
        assertEquals(1, document.interleaveMode)
        assertNotNull(bitmap)
        assertEquals(4, bitmap!!.width)
        assertEquals(3, bitmap.height)
        CHARLS_RGB_LINE_SAMPLES.forEachIndexed { index, sample ->
            assertEquals(
                0xFF000000.toInt() or (sample[0] shl 16) or (sample[1] shl 8) or sample[2],
                bitmap.getPixel(index % 4, index / 4),
                "index=$index",
            )
        }
    }

    @Test
    fun `decodes CharLS RGB line data with independent component run indexes`() {
        val codec = requireNotNull(Codec.MakeFromData(CHARLS_RGB_LINE_RUN_FIXTURE))
        val (bitmap, result) = codec.getImage()

        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        repeat(32) { index ->
            val expected = 0xFF000000.toInt() or (0x41 shl 16) or ((0x50 + index % 8) shl 8) or 0x42
            assertEquals(expected, bitmap!!.getPixel(index % 8, index / 8), "index=$index")
        }
    }

    @Test
    fun `refuses sample-interleaved RGB without treating it as grayscale`() {
        val sampleInterleaved = CHARLS_RGB_LINE_FIXTURE.copyOf().also { it[33] = 2 }

        val opened = JpegLsDocument.open(sampleInterleaved)

        assertNull(opened.document)
        assertEquals("jpeg-ls.scan.unsupported", opened.diagnostic?.code)
        assertEquals(Codec.Result.kUnimplemented, opened.diagnostic?.result)
    }

    @Test
    fun `refuses non-RGB component identities and sampling in a color SOF55`() {
        val invalidIdentity = CHARLS_RGB_LINE_FIXTURE.copyOf().also { it[12] = 2 }
        val invalidSampling = CHARLS_RGB_LINE_FIXTURE.copyOf().also { it[13] = 0x21 }

        listOf(invalidIdentity, invalidSampling).forEach { encoded ->
            val opened = JpegLsDocument.open(encoded)
            assertNull(opened.document)
            assertEquals("jpeg-ls.frame.unsupported", opened.diagnostic?.code)
            assertEquals(Codec.Result.kUnimplemented, opened.diagnostic?.result)
        }
    }

    @Test
    fun `refuses RGB NEAR coding until an independent near-lossless oracle is added`() {
        val nearOne = CHARLS_RGB_LINE_FIXTURE.copyOf().also { it[32] = 1 }

        val opened = JpegLsDocument.open(nearOne)

        assertNull(opened.document)
        assertEquals("jpeg-ls.scan.unsupported", opened.diagnostic?.code)
        assertEquals(Codec.Result.kUnimplemented, opened.diagnostic?.result)
    }

    @Test
    fun `retains APP and COM metadata around a color JPEG-LS scan`() {
        val withMetadata = CHARLS_RGB_LINE_FIXTURE.copyOfRange(0, 2) + JFIF_APP0 + COMMENT +
            CHARLS_RGB_LINE_FIXTURE.copyOfRange(2, CHARLS_RGB_LINE_FIXTURE.size)

        val document = requireNotNull(JpegLsDocument.open(withMetadata).document)
        val decoded = document.decode()

        assertEquals(listOf(0xE0, 0xFE), document.metadataSegments.map { it.marker })
        assertArrayEquals(byteArrayOf('r'.code.toByte(), 'g'.code.toByte(), 'b'.code.toByte()), document.copyPayload(document.metadataSegments[1]))
        assertNotNull(decoded.bitmap)
        assertEquals(0xFF414243.toInt(), decoded.bitmap!!.getPixel(0, 0))
    }

    @Test
    fun `decodes a CharLS NEAR 1 fixture within its declared error bound`() {
        val codec = requireNotNull(Codec.MakeFromData(CHARLS_NEAR_1_FIXTURE))
        val (bitmap, result) = codec.getImage()

        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(1, requireNotNull(JpegLsDocument.open(CHARLS_NEAR_1_FIXTURE).document).nearLossless)
        CHARLS_NEAR_1_SOURCE.forEachIndexed { index, sourceSample ->
            val decodedSample = bitmap!!.getPixel(index % 8, index / 8) and 0xFF
            assertEquals(CHARLS_NEAR_1_DECODED[index], decodedSample, "index=$index")
            assertTrue(abs(decodedSample - sourceSample) <= 1, "index=$index source=$sourceSample decoded=$decodedSample")
        }
    }

    @Test
    fun `decodes a CharLS NEAR 13 fixture exactly as its oracle reconstruction`() {
        val codec = requireNotNull(Codec.MakeFromData(CHARLS_NEAR_13_FIXTURE))
        val (bitmap, result) = codec.getImage()

        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(13, requireNotNull(JpegLsDocument.open(CHARLS_NEAR_13_FIXTURE).document).nearLossless)
        CHARLS_NEAR_1_SOURCE.forEachIndexed { index, sourceSample ->
            val decodedSample = bitmap!!.getPixel(index % 8, index / 8) and 0xFF
            assertEquals(CHARLS_NEAR_13_DECODED[index], decodedSample, "index=$index")
            assertTrue(abs(decodedSample - sourceSample) <= 13, "index=$index source=$sourceSample decoded=$decodedSample")
        }
    }

    @Test
    fun `decodes a CharLS NEAR 127 fixture exactly as its oracle reconstruction`() {
        val codec = requireNotNull(Codec.MakeFromData(CHARLS_NEAR_127_FIXTURE))
        val (bitmap, result) = codec.getImage()

        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(127, requireNotNull(JpegLsDocument.open(CHARLS_NEAR_127_FIXTURE).document).nearLossless)
        CHARLS_NEAR_1_SOURCE.forEachIndexed { index, sourceSample ->
            val decodedSample = bitmap!!.getPixel(index % 8, index / 8) and 0xFF
            assertEquals(CHARLS_NEAR_127_DECODED[index], decodedSample, "index=$index")
            assertTrue(abs(decodedSample - sourceSample) <= 127, "index=$index source=$sourceSample decoded=$decodedSample")
        }
    }

    @Test
    fun `uses lower-bound default thresholds for NEAR 127`() {
        val parameters = JpegLsCodingParameters.defaults(127)

        assertEquals(255, parameters.maximumSampleValue)
        assertEquals(128, parameters.threshold1)
        assertEquals(128, parameters.threshold2)
        assertEquals(128, parameters.threshold3)
        assertEquals(64, parameters.reset)
    }

    @Test
    fun `decodes the CharLS NEAR 127 gradient from 128 through 254 exactly as its reconstruction`() {
        val codec = requireNotNull(Codec.MakeFromData(CHARLS_NEAR_127_GRADIENT_FIXTURE))
        val (bitmap, result) = codec.getImage()

        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        CHARLS_NEAR_127_GRADIENT_DECODED.forEachIndexed { index, sample ->
            assertEquals(sample, bitmap!!.getPixel(index, 0) and 0xFF, "index=$index")
        }
    }

    @Test
    fun `parses default LSE parameters before the scan`() {
        val withPreset = CHARLS_REGULAR_FIXTURE.copyOfRange(0, 2) + DEFAULT_LSE + CHARLS_REGULAR_FIXTURE.copyOfRange(2, CHARLS_REGULAR_FIXTURE.size)

        val codec = Codec.MakeFromData(withPreset)
        val (bitmap, result) = requireNotNull(codec).getImage()

        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(0xFF414141.toInt(), bitmap!!.getPixel(0, 0))
        assertEquals(0xFF717171.toInt(), bitmap.getPixel(1, 0))
    }

    @Test
    fun `parses an explicit NEAR 1 default LSE parameter set`() {
        val withPreset = CHARLS_NEAR_1_FIXTURE.copyOfRange(0, 2) + NEAR_1_DEFAULT_LSE +
            CHARLS_NEAR_1_FIXTURE.copyOfRange(2, CHARLS_NEAR_1_FIXTURE.size)

        val document = requireNotNull(JpegLsDocument.open(withPreset).document)
        val decoded = document.decode()

        assertEquals(1, document.nearLossless)
        assertNotNull(decoded.bitmap)
        CHARLS_NEAR_1_SOURCE.forEachIndexed { index, sourceSample ->
            assertTrue(abs((decoded.bitmap!!.getPixel(index % 8, index / 8) and 0xFF) - sourceSample) <= 1)
        }
    }

    @Test
    fun `parses an explicit NEAR 127 lower-bound default LSE parameter set`() {
        val withPreset = CHARLS_NEAR_127_GRADIENT_FIXTURE.copyOfRange(0, 2) + NEAR_127_DEFAULT_LSE +
            CHARLS_NEAR_127_GRADIENT_FIXTURE.copyOfRange(2, CHARLS_NEAR_127_GRADIENT_FIXTURE.size)

        val document = requireNotNull(JpegLsDocument.open(withPreset).document)
        val decoded = document.decode()

        assertEquals(127, document.nearLossless)
        assertNotNull(decoded.bitmap)
        CHARLS_NEAR_127_GRADIENT_DECODED.forEachIndexed { index, sample ->
            assertEquals(sample, decoded.bitmap!!.getPixel(index, 0) and 0xFF, "index=$index")
        }
    }

    @Test
    fun `refuses an explicit NEAR 127 MAXVAL threshold LSE parameter set`() {
        val withPreset = CHARLS_NEAR_127_GRADIENT_FIXTURE.copyOfRange(0, 2) + NEAR_127_MAXVAL_LSE +
            CHARLS_NEAR_127_GRADIENT_FIXTURE.copyOfRange(2, CHARLS_NEAR_127_GRADIENT_FIXTURE.size)

        val opened = JpegLsDocument.open(withPreset)

        assertNull(opened.document)
        assertEquals("jpeg-ls.lse.unsupported", opened.diagnostic?.code)
        assertEquals(Codec.Result.kUnimplemented, opened.diagnostic?.result)
    }

    @Test
    fun `refuses a non-default LSE parameter set without broadening the profile`() {
        val customLse = NEAR_1_DEFAULT_LSE.copyOf().also { it[8] = 0x07 }
        val withCustomLse = CHARLS_NEAR_1_FIXTURE.copyOfRange(0, 2) + customLse +
            CHARLS_NEAR_1_FIXTURE.copyOfRange(2, CHARLS_NEAR_1_FIXTURE.size)

        val opened = JpegLsDocument.open(withCustomLse)

        assertNull(opened.document)
        assertEquals("jpeg-ls.lse.unsupported", opened.diagnostic?.code)
        assertEquals(Codec.Result.kUnimplemented, opened.diagnostic?.result)
    }

    @Test
    fun `open retains APP0 metadata without exposing mutable payload`() {
        val withJfif = CHARLS_RUN_FIXTURE.copyOfRange(0, 2) + JFIF_APP0 + CHARLS_RUN_FIXTURE.copyOfRange(2, CHARLS_RUN_FIXTURE.size)

        val opened = JpegLsDocument.open(withJfif)
        val document = requireNotNull(opened.document)
        val segment = document.metadataSegments.single()
        val payload = document.copyPayload(segment)

        assertEquals(0xE0, segment.marker)
        assertArrayEquals(JFIF_APP0.copyOfRange(4, JFIF_APP0.size), payload)
        payload[0] = 0
        assertArrayEquals(JFIF_APP0.copyOfRange(4, JFIF_APP0.size), document.copyPayload(segment))
        assertArrayEquals(withJfif, document.copyEncodedBytes())
    }

    @Test
    fun `open rejects a truncated SOF55 with a stable diagnostic`() {
        val opened = JpegLsDocument.open(CHARLS_RUN_FIXTURE.copyOf(9))

        assertNull(opened.document)
        assertEquals("jpeg-ls.sof.truncated", opened.diagnostic?.code)
    }

    @Test
    fun `open rejects a JPEG-LS NEAR value beyond the 8-bit bound`() {
        val invalidNear = CHARLS_NEAR_1_FIXTURE.copyOf().also { it[22] = 128.toByte() }

        val opened = JpegLsDocument.open(invalidNear)

        assertNull(opened.document)
        assertEquals("jpeg-ls.scan.near.invalid", opened.diagnostic?.code)
    }

    @Test
    fun `encoder rejects a JPEG-LS NEAR value beyond the 8-bit bound`() {
        assertThrows(IllegalArgumentException::class.java) {
            JpegLsEncoder.Options(nearLossless = 128)
        }
    }

    @Test
    fun `open applies pixel limits before entropy allocation`() {
        val opened = JpegLsDocument.open(CHARLS_RUN_FIXTURE, JpegLsLimits(maxPixels = 31))

        assertNull(opened.document)
        assertEquals("jpeg-ls.limit.pixels", opened.diagnostic?.code)
    }

    @Test
    fun `decode refuses an entropy stream without EOI`() {
        val codec = requireNotNull(Codec.MakeFromData(CHARLS_RUN_FIXTURE.copyOf(CHARLS_RUN_FIXTURE.size - 2)))

        val (_, result) = codec.getImage()

        assertEquals(Codec.Result.kErrorInInput, result)
    }

    @Test
    fun `bounded parser and decoder reject deterministic corruptions without throwing`() {
        val mutations = buildList {
            add(ByteArray(0))
            for (length in 0 until CHARLS_REGULAR_FIXTURE.size step 3) {
                add(CHARLS_REGULAR_FIXTURE.copyOf(length))
            }
            for (index in CHARLS_REGULAR_FIXTURE.indices step 2) {
                add(CHARLS_REGULAR_FIXTURE.copyOf().also { it[index] = (it[index].toInt() xor 0x5A).toByte() })
            }
        }

        mutations.forEachIndexed { index, candidate ->
            assertDoesNotThrow({
                JpegLsDocument.open(candidate).document?.decode()
            }, "mutation=$index")
        }
    }

    @Test
    fun `encoder writes a grayscale JPEG-LS that the dispatcher decodes exactly`() {
        val source = grayscaleBitmap(
            width = 8,
            height = 4,
            samples = intArrayOf(
                0x41, 0x71, 0x41, 0x71, 0x41, 0x71, 0x41, 0x71,
                0x71, 0x41, 0x71, 0x41, 0x71, 0x41, 0x71, 0x41,
                0x41, 0x71, 0x41, 0x71, 0x41, 0x71, 0x41, 0x71,
                0x41, 0x71, 0x41, 0x71, 0x41, 0x71, 0x41, 0x71,
            ),
        )

        val encoded = requireNotNull(JpegLsEncoder.encode(source))
        val codec = Codec.MakeFromData(encoded)
        val (decoded, result) = requireNotNull(codec).getImage()

        assertTrue(encoded.copyOfRange(0, 4).contentEquals(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xF7.toByte())))
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(decoded)
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                assertEquals(source.getPixel(x, y), decoded!!.getPixel(x, y), "x=$x y=$y")
            }
        }
    }

    @Test
    fun `encoder writes an RGB line-interleaved JPEG-LS that decodes exactly`() {
        val source = rgbBitmap(4, 3, CHARLS_RGB_LINE_SAMPLES)

        val encoded = requireNotNull(JpegLsEncoder.encode(source))
        val codec = requireNotNull(Codec.MakeFromData(encoded))
        val (decoded, result) = codec.getImage()

        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(decoded)
        CHARLS_RGB_LINE_SAMPLES.forEachIndexed { index, sample ->
            assertEquals(
                0xFF000000.toInt() or (sample[0] shl 16) or (sample[1] shl 8) or sample[2],
                decoded!!.getPixel(index % 4, index / 4),
                "index=$index",
            )
        }
    }

    @Test
    fun `encoder writes a NEAR 1 JPEG-LS within its declared error bound`() {
        val sourceSamples = IntArray(48) { index -> (index * 37 + index / 8 * 19) and 0xFF }
        val source = grayscaleBitmap(8, 6, sourceSamples)

        val encoded = requireNotNull(JpegLsEncoder.encode(source, JpegLsEncoder.Options(nearLossless = 1)))
        val codec = requireNotNull(Codec.MakeFromData(encoded))
        val (decoded, result) = codec.getImage()

        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(decoded)
        assertEquals(1, requireNotNull(JpegLsDocument.open(encoded).document).nearLossless)
        sourceSamples.forEachIndexed { index, sourceSample ->
            val decodedSample = decoded!!.getPixel(index % 8, index / 8) and 0xFF
            assertTrue(abs(decodedSample - sourceSample) <= 1, "index=$index source=$sourceSample decoded=$decodedSample")
        }
    }

    @Test
    fun `encoder writes a NEAR 13 JPEG-LS within its declared error bound`() {
        val sourceSamples = IntArray(48) { index -> (index * 37 + index / 8 * 19) and 0xFF }
        val source = grayscaleBitmap(8, 6, sourceSamples)

        val encoded = requireNotNull(JpegLsEncoder.encode(source, JpegLsEncoder.Options(nearLossless = 13)))
        val codec = requireNotNull(Codec.MakeFromData(encoded))
        val (decoded, result) = codec.getImage()

        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(decoded)
        assertEquals(13, requireNotNull(JpegLsDocument.open(encoded).document).nearLossless)
        sourceSamples.forEachIndexed { index, sourceSample ->
            val decodedSample = decoded!!.getPixel(index % 8, index / 8) and 0xFF
            assertTrue(abs(decodedSample - sourceSample) <= 13, "index=$index source=$sourceSample decoded=$decodedSample")
        }
    }

    @Test
    fun `encoder writes a NEAR 127 JPEG-LS within its declared error bound`() {
        val source = grayscaleBitmap(8, 6, CHARLS_NEAR_1_SOURCE)

        val encoded = requireNotNull(JpegLsEncoder.encode(source, JpegLsEncoder.Options(nearLossless = 127)))
        val codec = requireNotNull(Codec.MakeFromData(encoded))
        val (decoded, result) = codec.getImage()

        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(decoded)
        assertEquals(127, requireNotNull(JpegLsDocument.open(encoded).document).nearLossless)
        CHARLS_NEAR_1_SOURCE.forEachIndexed { index, sourceSample ->
            val decodedSample = decoded!!.getPixel(index % 8, index / 8) and 0xFF
            assertTrue(abs(decodedSample - sourceSample) <= 127, "index=$index source=$sourceSample decoded=$decodedSample")
        }
    }

    @Test
    fun `optional CharLS oracle decodes encoded grayscale pixels exactly`() {
        val oracle = System.getProperty("kanvas.jpeg-ls.oracle.charls").orEmpty()
        if (oracle.isBlank()) return
        val samples = IntArray(32) { index -> (index * 37 + 11) and 0xFF }
        val source = grayscaleBitmap(8, 4, samples)
        val encoded = requireNotNull(JpegLsEncoder.encode(source))
        val directory = Files.createTempDirectory("kanvas-jpeg-ls-oracle-")
        try {
            val input = directory.resolve("encoded.jls")
            val output = directory.resolve("decoded.pgm")
            Files.write(input, encoded)
            val process = ProcessBuilder(oracle, "decode", input.toString(), output.toString())
                .redirectErrorStream(true)
                .start()
            val processOutput = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.waitFor(), processOutput)
            val decoded = Files.readAllBytes(output)
            assertArrayEquals(samples.map(Int::toByte).toByteArray(), decoded.copyOfRange(decoded.size - samples.size, decoded.size))
        } finally {
            Files.deleteIfExists(directory.resolve("encoded.jls"))
            Files.deleteIfExists(directory.resolve("decoded.pgm"))
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun `optional CharLS oracle decodes encoded RGB line pixels exactly`() {
        val oracle = System.getProperty("kanvas.jpeg-ls.oracle.charls").orEmpty()
        if (oracle.isBlank()) return
        val source = rgbBitmap(4, 3, CHARLS_RGB_LINE_SAMPLES)
        val encoded = requireNotNull(JpegLsEncoder.encode(source))
        val directory = Files.createTempDirectory("kanvas-jpeg-ls-rgb-oracle-")
        try {
            val input = directory.resolve("encoded.jls")
            val output = directory.resolve("decoded.ppm")
            Files.write(input, encoded)
            val process = ProcessBuilder(oracle, "decode", input.toString(), output.toString())
                .redirectErrorStream(true)
                .start()
            val processOutput = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.waitFor(), processOutput)
            val decoded = Files.readAllBytes(output)
            assertArrayEquals(
                CHARLS_RGB_LINE_SAMPLES.flatMap { it.asIterable() }.map(Int::toByte).toByteArray(),
                decoded.copyOfRange(decoded.size - CHARLS_RGB_LINE_SAMPLES.size * 3, decoded.size),
            )
        } finally {
            Files.deleteIfExists(directory.resolve("encoded.jls"))
            Files.deleteIfExists(directory.resolve("decoded.ppm"))
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun `optional CharLS oracle decodes encoded NEAR 1 pixels within bound`() {
        val oracle = System.getProperty("kanvas.jpeg-ls.oracle.charls").orEmpty()
        if (oracle.isBlank()) return
        val samples = IntArray(48) { index -> (index * 37 + index / 8 * 19) and 0xFF }
        val encoded = requireNotNull(JpegLsEncoder.encode(grayscaleBitmap(8, 6, samples), JpegLsEncoder.Options(1)))
        val directory = Files.createTempDirectory("kanvas-jpeg-ls-near-oracle-")
        try {
            val input = directory.resolve("encoded.jls")
            val output = directory.resolve("decoded.pgm")
            Files.write(input, encoded)
            val process = ProcessBuilder(oracle, "decode", input.toString(), output.toString())
                .redirectErrorStream(true)
                .start()
            val processOutput = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.waitFor(), processOutput)
            val outputBytes = Files.readAllBytes(output)
            val decoded = outputBytes.copyOfRange(outputBytes.size - samples.size, outputBytes.size)
            decoded.forEachIndexed { index, sample ->
                assertTrue(abs(sample.u8() - samples[index]) <= 1, "index=$index")
            }
        } finally {
            Files.deleteIfExists(directory.resolve("encoded.jls"))
            Files.deleteIfExists(directory.resolve("decoded.pgm"))
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun `optional CharLS oracle decodes encoded NEAR 127 pixels within bound`() {
        val oracle = System.getProperty("kanvas.jpeg-ls.oracle.charls").orEmpty()
        if (oracle.isBlank()) return
        val encoded = requireNotNull(
            JpegLsEncoder.encode(grayscaleBitmap(8, 6, CHARLS_NEAR_1_SOURCE), JpegLsEncoder.Options(127)),
        )
        val directory = Files.createTempDirectory("kanvas-jpeg-ls-near-boundary-oracle-")
        try {
            val input = directory.resolve("encoded.jls")
            val output = directory.resolve("decoded.pgm")
            Files.write(input, encoded)
            val process = ProcessBuilder(oracle, "decode", input.toString(), output.toString())
                .redirectErrorStream(true)
                .start()
            val processOutput = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.waitFor(), processOutput)
            val outputBytes = Files.readAllBytes(output)
            val decoded = outputBytes.copyOfRange(outputBytes.size - CHARLS_NEAR_1_SOURCE.size, outputBytes.size)
            decoded.forEachIndexed { index, sample ->
                assertTrue(abs(sample.u8() - CHARLS_NEAR_1_SOURCE[index]) <= 127, "index=$index")
            }
        } finally {
            Files.deleteIfExists(directory.resolve("encoded.jls"))
            Files.deleteIfExists(directory.resolve("decoded.pgm"))
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun `optional CharLS oracle decodes encoded NEAR 127 gradient from 128 through 254 within bound`() {
        val oracle = System.getProperty("kanvas.jpeg-ls.oracle.charls").orEmpty()
        if (oracle.isBlank()) return
        val samples = IntArray(127) { index -> 128 + index }
        val encoded = requireNotNull(
            JpegLsEncoder.encode(grayscaleBitmap(127, 1, samples), JpegLsEncoder.Options(127)),
        )
        val directory = Files.createTempDirectory("kanvas-jpeg-ls-near-gradient-oracle-")
        try {
            val input = directory.resolve("encoded.jls")
            val output = directory.resolve("decoded.pgm")
            Files.write(input, encoded)
            val process = ProcessBuilder(oracle, "decode", input.toString(), output.toString())
                .redirectErrorStream(true)
                .start()
            val processOutput = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.waitFor(), processOutput)
            val outputBytes = Files.readAllBytes(output)
            val decoded = outputBytes.copyOfRange(outputBytes.size - samples.size, outputBytes.size)
            decoded.forEachIndexed { index, sample ->
                assertTrue(abs(sample.u8() - samples[index]) <= 127, "index=$index")
            }
        } finally {
            Files.deleteIfExists(directory.resolve("encoded.jls"))
            Files.deleteIfExists(directory.resolve("decoded.pgm"))
            Files.deleteIfExists(directory)
        }
    }

    private fun grayscaleBitmap(width: Int, height: Int, samples: IntArray): SkBitmap {
        require(samples.size == width * height)
        return SkBitmap(width, height).also { bitmap ->
            samples.forEachIndexed { index, sample ->
                bitmap.setPixel(index % width, index / width, 0xFF000000.toInt() or (sample shl 16) or (sample shl 8) or sample)
            }
        }
    }

    private fun rgbBitmap(width: Int, height: Int, samples: Array<IntArray>): SkBitmap {
        require(samples.size == width * height)
        return SkBitmap(width, height).also { bitmap ->
            samples.forEachIndexed { index, sample ->
                bitmap.setPixel(
                    index % width,
                    index / width,
                    0xFF000000.toInt() or (sample[0] shl 16) or (sample[1] shl 8) or sample[2],
                )
            }
        }
    }

    private fun decodeDiagnostic(data: ByteArray): String =
        JpegLsDocument.open(data).document?.decode()?.diagnostic.toString()

    private companion object {
        /**
         * Generated from a project-owned 8x4 PGM by CharLS 3.0.0
         * (`c0bae6496fa5d787fbb4698debd1e5decb40cf3a`), BSD-3-Clause:
         * SHA-256 `f9dc3cd87c141d69cc66bdc0a0c86d024c7fa8bed21c9f5f60ccca2b11c54f64`.
         */
        val CHARLS_RUN_FIXTURE: ByteArray = Base64.getDecoder().decode("/9j/9wALCAAEAAgBAREA/9oACAEBAAAAAAAAAYCV8/9g/9k=")

        /**
         * Generated independently by the same pinned CharLS oracle from a
         * project-owned alternating 8x4 PGM; it enters regular LOCO-I mode:
         * SHA-256 `122b795e59e34670b59c610c2d92d451720154e416473ae1cca1ec4e0f3a050b`.
         */
        val CHARLS_REGULAR_FIXTURE: ByteArray = Base64.getDecoder().decode(
            "/9j/9wALCAAEAAgBAREA/9oACAEBAAAAAAAAAYAAAAFeES+xL7EvyV4AAACvgAAAr58uLSwrAAAAr0v+/9k=",
        )

        /**
         * Generated by CharLS 3.0.0 (`c0bae6496fa5d787fbb4698debd1e5decb40cf3a`)
         * from a project-owned binary 4x3 PPM with `--interleave-mode 1` (ILV=1,
         * line interleave), no transform and `NEAR=0`:
         * SHA-256 `e105dc472f9cec7ad860d8557b159b90fe540543db74e09d1be17876ff345a11`.
         */
        val CHARLS_RGB_LINE_FIXTURE: ByteArray = Base64.getDecoder().decode(
            "/9j/9wARCAADAAQDAREAAhEAAxEA/9oADAMBAAIAAwAAAQAAAAGAX0Q7cRfwIyMdsiksAErFqhTg/9k=",
        )

        val CHARLS_RGB_LINE_SAMPLES: Array<IntArray> = arrayOf(
            intArrayOf(0x41, 0x42, 0x43), intArrayOf(0x44, 0x45, 0x46),
            intArrayOf(0x47, 0x48, 0x49), intArrayOf(0x4A, 0x4B, 0x4C),
            intArrayOf(0x4D, 0x4E, 0x4F), intArrayOf(0x50, 0x51, 0x52),
            intArrayOf(0x53, 0x54, 0x55), intArrayOf(0x56, 0x57, 0x58),
            intArrayOf(0x61, 0x62, 0x63), intArrayOf(0x64, 0x65, 0x66),
            intArrayOf(0x67, 0x68, 0x69), intArrayOf(0x6A, 0x6B, 0x6C),
        )

        /**
         * Independent CharLS 3.0.0 ILV=1 fixture with constant R/B lines and
         * a varying G line, so component run indexes diverge:
         * SHA-256 `f2ab751d6e1654785b6cb1aacbc64eef24c166880ba5c03f2d4a0b3bd89c08db`.
         */
        val CHARLS_RGB_LINE_RUN_FIXTURE: ByteArray = Base64.getDecoder().decode(
            "/9j/9wARCAAEAAgDAREAAhEAAxEA/9oADAMBAAIAAwAAAQAAAAGAleL6qqIf+fyV5f9n9/f2/9k=",
        )

        /**
         * Generated by CharLS 3.0.0 (`c0bae6496fa5d787fbb4698debd1e5decb40cf3a`)
         * from the project-owned 8x6 [CHARLS_NEAR_1_SOURCE] PGM with
         * `charls-cli encode input.pgm output.jls --near-lossless 1`:
         * SHA-256 `59f46387e0f6d29884ea924aa47fba1dd7ce0ea66f17296a5b445c365d5f2920`.
         */
        val CHARLS_NEAR_1_FIXTURE: ByteArray = Base64.getDecoder().decode(
            "/9j/9wALCAAGAAgBAREA/9oACAEBAAEAAN1quxgAE/gAEBAQTNmAAACNVMDBgMAAADGQaPnQ/9k=",
        )

        /** CharLS 3.0.0 reconstruction of [CHARLS_NEAR_1_FIXTURE]. */
        val CHARLS_NEAR_1_DECODED: IntArray = intArrayOf(
            0, 0, 3, 3, 3, 6, 6, 6,
            255, 255, 252, 252, 252, 251, 248, 247,
            39, 42, 43, 44, 45, 45, 46, 47,
            81, 89, 100, 109, 119, 130, 139, 151,
            150, 141, 131, 120, 111, 101, 92, 80,
            0, 255, 0, 254, 0, 255, 1, 255,
        )

        /** CharLS 3.0.0 NEAR=13 fixture from the same project-owned 8x6 PGM. */
        val CHARLS_NEAR_13_FIXTURE: ByteArray = Base64.getDecoder().decode(
            "/9j/9wALCAAGAAgBAREA/9oACAEBAA0AAPxX4j+V1kXV+GTISMT/2Q==",
        )

        /** CharLS 3.0.0 reconstruction of [CHARLS_NEAR_13_FIXTURE]. */
        val CHARLS_NEAR_13_DECODED: IntArray = intArrayOf(
            0, 0, 0, 0, 0, 0, 0, 0,
            243, 243, 243, 243, 243, 243, 243, 243,
            27, 54, 55, 56, 57, 58, 59, 60,
            81, 81, 108, 108, 108, 135, 135, 162,
            162, 135, 135, 109, 109, 108, 81, 81,
            0, 243, 0, 243, 0, 244, 1, 243,
        )

        /**
         * CharLS 3.0.0 NEAR=127 fixture from the same project-owned 8x6 PGM:
         * SHA-256 `5f0cd53aadba768a58a343e9f6ec877b7a6fa17a51ecaa432d6d5b5f38428dfd`.
         * This exercises the boundary `RANGE=2`, `qbpp=1`, run and run interruption.
         */
        val CHARLS_NEAR_127_FIXTURE: ByteArray = Base64.getDecoder().decode(
            "/9j/9wALCAAGAAgBAREA/9oACAEBAH8AAPyv/3ss7eqpSP/Z",
        )

        /** CharLS 3.0.0 reconstruction of [CHARLS_NEAR_127_FIXTURE]. */
        val CHARLS_NEAR_127_DECODED: IntArray = intArrayOf(
            0, 0, 0, 0, 0, 0, 0, 0,
            255, 255, 255, 255, 255, 255, 255, 255,
            0, 0, 0, 0, 0, 0, 0, 0,
            1, 1, 1, 1, 1, 255, 255, 255,
            255, 255, 255, 0, 0, 0, 0, 0,
            0, 255, 0, 255, 0, 255, 0, 255,
        )

        /**
         * CharLS 3.0.0 NEAR=127 reconstruction of a project-owned one-row
         * gradient containing every source sample from 128 through 254:
         * source SHA-256 `506afb53f5c819c4fe0862f605f8e00ae403f23750d4029649a3806b345c9109`,
         * fixture SHA-256 `546d1cdfcbb0ff11e269ba72fc8a01f22c91ab646256116e431c0af124469335`.
         * This fixture exercises the `NEAR=127` lower-bound defaults
         * `T1=T2=T3=128` against an independent CharLS reconstruction.
         */
        val CHARLS_NEAR_127_GRADIENT_FIXTURE: ByteArray = Base64.getDecoder().decode(
            "/9j/9wALCAABAH8BAREA/9oACAEBAH8AAFf/f/9//3//f/9//3//f/9/wP/Z",
        )

        /** CharLS 3.0.0 reconstruction of [CHARLS_NEAR_127_GRADIENT_FIXTURE]. */
        val CHARLS_NEAR_127_GRADIENT_DECODED: IntArray = IntArray(127) { 255 }

        val CHARLS_NEAR_1_SOURCE: IntArray = intArrayOf(
            0, 1, 2, 3, 4, 5, 6, 7,
            255, 254, 253, 252, 251, 250, 249, 248,
            40, 41, 42, 43, 44, 45, 46, 47,
            80, 90, 100, 110, 120, 130, 140, 150,
            151, 141, 131, 121, 111, 101, 91, 81,
            0, 255, 0, 255, 0, 255, 0, 255,
        )

        val DEFAULT_LSE: ByteArray = byteArrayOf(
            0xFF.toByte(), 0xF8.toByte(), 0x00, 0x0D,
            0x01,
            0x00, 0xFF.toByte(),
            0x00, 0x03,
            0x00, 0x07,
            0x00, 0x15,
            0x00, 0x40,
        )

        val NEAR_1_DEFAULT_LSE: ByteArray = byteArrayOf(
            0xFF.toByte(), 0xF8.toByte(), 0x00, 0x0D,
            0x01,
            0x00, 0xFF.toByte(),
            0x00, 0x06,
            0x00, 0x0C,
            0x00, 0x1C,
            0x00, 0x40,
        )

        val NEAR_127_DEFAULT_LSE: ByteArray = byteArrayOf(
            0xFF.toByte(), 0xF8.toByte(), 0x00, 0x0D,
            0x01,
            0x00, 0xFF.toByte(),
            0x00, 0x80.toByte(),
            0x00, 0x80.toByte(),
            0x00, 0x80.toByte(),
            0x00, 0x40,
        )

        val NEAR_127_MAXVAL_LSE: ByteArray = byteArrayOf(
            0xFF.toByte(), 0xF8.toByte(), 0x00, 0x0D,
            0x01,
            0x00, 0xFF.toByte(),
            0x00, 0xFF.toByte(),
            0x00, 0xFF.toByte(),
            0x00, 0xFF.toByte(),
            0x00, 0x40,
        )

        val JFIF_APP0: ByteArray = byteArrayOf(
            0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10,
            'J'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 0,
            0x01, 0x02, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
        )

        val COMMENT: ByteArray = byteArrayOf(
            0xFF.toByte(), 0xFE.toByte(), 0x00, 0x05,
            'r'.code.toByte(), 'g'.code.toByte(), 'b'.code.toByte(),
        )
    }
}
