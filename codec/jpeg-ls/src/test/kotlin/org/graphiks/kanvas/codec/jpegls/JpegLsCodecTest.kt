package org.graphiks.kanvas.codec.jpegls

import java.util.Base64
import java.nio.file.Files
import java.security.MessageDigest
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
    fun `decodes a CharLS RGB sample-interleaved fixture exactly`() {
        val fixture = charlsResource("sample-lossless-6x4.jls.base64")
        val document = requireNotNull(JpegLsDocument.open(fixture).document)
        val codec = requireNotNull(Codec.MakeFromData(fixture))
        val (bitmap, result) = codec.getImage()

        assertEquals(Codec.Result.kSuccess, result, decodeDiagnostic(fixture))
        assertEquals(3, document.componentCount)
        assertEquals(2, document.interleaveMode)
        assertNotNull(bitmap)
        repeat(24) { index ->
            val x = index % 6
            val y = index / 6
            val red = 0x41 + ((x + y) and 1) * 0x30
            val green = 0x50 + x * 3 + y
            val blue = 0x42 + ((x * y) and 3)
            assertEquals(
                0xFF000000.toInt() or (red shl 16) or (green shl 8) or blue,
                bitmap!!.getPixel(x, y),
                "x=$x y=$y",
            )
        }
    }

    @Test
    fun `decodes CharLS RGB sample-interleaved run interruption exactly`() {
        val fixture = charlsResource("sample-run-lossless-6x4.jls.base64")
        val document = requireNotNull(JpegLsDocument.open(fixture).document)
        val (bitmap, result) = requireNotNull(Codec.MakeFromData(fixture)).getImage()

        assertEquals(Codec.Result.kSuccess, result, decodeDiagnostic(fixture))
        assertEquals(2, document.interleaveMode)
        assertNotNull(bitmap)
        repeat(24) { index ->
            val x = index % 6
            val y = index / 6
            val pixel = if (x == 4 && y == 2) 0xFF706050.toInt() else 0xFF415042.toInt()
            assertEquals(pixel, bitmap!!.getPixel(x, y), "x=$x y=$y")
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
    fun `decodes CharLS RGB ILV1 NEAR 1 regular fixture to its oracle reconstruction`() {
        assertCharlsRgbNearFixture(
            fixture = charlsResource("line-near1.jls.base64"),
            source = ppmRgb(charlsResource("line-source.ppm.base64")),
            reconstruction = ppmRgb(charlsResource("line-near1-reconstruction.ppm.base64")),
            near = 1,
        )
    }

    @Test
    fun `decodes CharLS RGB ILV1 NEAR 1 run fixture to its oracle reconstruction`() {
        assertCharlsRgbNearFixture(
            fixture = charlsResource("line-run-near1.jls.base64"),
            source = ppmRgb(charlsResource("line-run-source.ppm.base64")),
            reconstruction = ppmRgb(charlsResource("line-run-near1-reconstruction.ppm.base64")),
            near = 1,
        )
    }

    @Test
    fun `decodes CharLS RGB ILV1 NEAR 127 run fixture to its oracle reconstruction`() {
        assertCharlsRgbNearFixture(
            fixture = charlsResource("line-run-near127.jls.base64"),
            source = ppmRgb(charlsResource("line-run-source.ppm.base64")),
            reconstruction = ppmRgb(charlsResource("line-run-near127-reconstruction.ppm.base64")),
            near = 127,
        )
    }

    @Test
    fun `pinned CharLS RGB NEAR fixtures match their documented SHA-256`() {
        assertEquals(
            "eb933e7350eb37c385edb25d2949012bf77640f4d4f1ec36c748f58b7a0314e4",
            sha256(charlsResource("line-source.ppm.base64")),
        )
        assertEquals(
            "f64fcc2255c1e3ec760b8649a88ecb753142364584cdc83bc31d8853c4ed6f1d",
            sha256(charlsResource("line-near1.jls.base64")),
        )
        assertEquals(
            "dcc562c99e14984918324d670aafd4a7a8fe2abd7db5632941a1d88af58acba0",
            sha256(charlsResource("line-near1-reconstruction.ppm.base64")),
        )
        assertEquals(
            "1728363fcc8cebc8abf6edbeb3ef734b00cf3f93fab77afda8d1cc4dbdfafb59",
            sha256(charlsResource("line-run-source.ppm.base64")),
        )
        assertEquals(
            "1a1324c21c96f2d5ca00bbb87756f7166679fc17df6f47cda7fe5b871a6c681c",
            sha256(charlsResource("line-run-near1.jls.base64")),
        )
        assertEquals(
            "c652010198a2fc09a0a66da980b42bef211d8bc326b8d3e4823a56a7de538d72",
            sha256(charlsResource("line-run-near1-reconstruction.ppm.base64")),
        )
        assertEquals(
            "50aeb4c4cd46b91a0b80fc7e2751b2f282083dc8e5c6ede75d329196d4b915b0",
            sha256(charlsResource("line-run-near127.jls.base64")),
        )
        assertEquals(
            "568c3a328bed5fe0be2936c6bc986677e60c30494ab983b201020c87de3c1936",
            sha256(charlsResource("line-run-near127-reconstruction.ppm.base64")),
        )
    }

    @Test
    fun `pinned CharLS RGB sample interleave fixtures match their documented SHA-256`() {
        assertEquals(
            "9ab6f360d63d15651ec76416abe003002964496df2797fd7ce67226d10ea1aee",
            sha256(charlsResource("sample-lossless-6x4.jls.base64")),
        )
        assertEquals(
            "6b06b88a6ae4da9727bb010042dc3b9bcaa0e65f39d5c21e098a41825dc034a4",
            sha256(charlsResource("sample-run-lossless-6x4.jls.base64")),
        )
    }

    @Test
    fun `refuses an RGB interleave mode beyond the JPEG-LS range`() {
        val invalidInterleave = CHARLS_RGB_LINE_FIXTURE.copyOf().also { it[33] = 3 }

        val opened = JpegLsDocument.open(invalidInterleave)

        assertNull(opened.document)
        assertEquals("jpeg-ls.scan.unsupported", opened.diagnostic?.code)
        assertEquals(Codec.Result.kUnimplemented, opened.diagnostic?.result)
    }

    @Test
    fun `refuses unproven near-lossless RGB sample interleave in both directions`() {
        val nearSample = charlsResource("sample-lossless-6x4.jls.base64").also { it[32] = 1 }

        val opened = JpegLsDocument.open(nearSample)

        assertNull(opened.document)
        assertEquals("jpeg-ls.scan.unsupported", opened.diagnostic?.code)
        assertEquals(Codec.Result.kUnimplemented, opened.diagnostic?.result)
        assertNull(
            JpegLsEncoder.encode(
                rgbBitmap(1, 1, arrayOf(intArrayOf(0x41, 0x50, 0x42))),
                JpegLsEncoder.Options(nearLossless = 1, rgbInterleaveMode = JpegLsRgbInterleaveMode.Sample),
            ),
        )
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
    fun `refuses RGB NEAR above the 8-bit MAXVAL bound`() {
        val near128 = charlsResource("line-near1.jls.base64").also { it[32] = 128.toByte() }

        val opened = JpegLsDocument.open(near128)

        assertNull(opened.document)
        assertEquals("jpeg-ls.scan.near.invalid", opened.diagnostic?.code)
        assertEquals(Codec.Result.kErrorInInput, opened.diagnostic?.result)
    }

    @Test
    fun `refuses HP JPEG-LS color transforms before decoding RGB pixels`() {
        listOf(1, 2, 3).forEach { transform ->
            val encoded = CHARLS_RGB_LINE_FIXTURE.copyOfRange(0, 2) + mrfxColorTransform(transform) +
                CHARLS_RGB_LINE_FIXTURE.copyOfRange(2, CHARLS_RGB_LINE_FIXTURE.size)

            val opened = JpegLsDocument.open(encoded)

            assertNull(opened.document, "HP$transform must not create a decodable document")
            assertEquals("jpeg-ls.color-transform.unsupported", opened.diagnostic?.code)
            assertEquals(Codec.Result.kUnimplemented, opened.diagnostic?.result)
            assertNull(Codec.MakeFromData(encoded), "HP$transform must not create pixels through Codec")
        }
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

        assertEquals(JpegLsCodingParameters.maximumSampleValue / 2, JpegLsCodingParameters.maximumNearLossless)
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
    fun `encoder writes an RGB sample-interleaved JPEG-LS that decodes exactly`() {
        val samples = Array(24) { index ->
            val x = index % 6
            val y = index / 6
            intArrayOf(0x41 + ((x + y) and 1) * 0x30, 0x50 + x * 3 + y, 0x42 + ((x * y) and 3))
        }
        val encoded = requireNotNull(
            JpegLsEncoder.encode(
                rgbBitmap(6, 4, samples),
                JpegLsEncoder.Options(rgbInterleaveMode = JpegLsRgbInterleaveMode.Sample),
            ),
        )
        val document = requireNotNull(JpegLsDocument.open(encoded).document)
        val (bitmap, result) = requireNotNull(Codec.MakeFromData(encoded)).getImage()

        assertEquals(2, document.interleaveMode)
        assertEquals(Codec.Result.kSuccess, result)
        samples.forEachIndexed { index, sample ->
            assertEquals(
                0xFF000000.toInt() or (sample[0] shl 16) or (sample[1] shl 8) or sample[2],
                bitmap!!.getPixel(index % 6, index / 6),
                "index=$index",
            )
        }
    }

    @Test
    fun `encoder writes RGB ILV1 NEAR 1 JPEG-LS within its declared error bound`() {
        val sourceSamples = ppmRgb(charlsResource("line-run-source.ppm.base64"))
        val source = rgbBitmap(8, 4, sourceSamples)

        val encoded = requireNotNull(JpegLsEncoder.encode(source, JpegLsEncoder.Options(nearLossless = 1)))
        val document = requireNotNull(JpegLsDocument.open(encoded).document)
        val (decoded, result) = requireNotNull(Codec.MakeFromData(encoded)).getImage()

        assertEquals(1, document.nearLossless)
        assertEquals(1, document.interleaveMode)
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(decoded)
        sourceSamples.forEachIndexed { index, sample ->
            val pixel = decoded!!.getPixel(index % source.width, index / source.width)
            assertTrue(abs((pixel ushr 16 and 0xFF) - sample[0]) <= 1, "R index=$index")
            assertTrue(abs((pixel ushr 8 and 0xFF) - sample[1]) <= 1, "G index=$index")
            assertTrue(abs((pixel and 0xFF) - sample[2]) <= 1, "B index=$index")
        }
    }

    @Test
    fun `encoder writes RGB ILV1 NEAR 127 JPEG-LS within its declared error bound`() {
        val sourceSamples = ppmRgb(charlsResource("line-run-source.ppm.base64"))
        val source = rgbBitmap(8, 4, sourceSamples)

        val encoded = requireNotNull(JpegLsEncoder.encode(source, JpegLsEncoder.Options(nearLossless = 127)))
        val document = requireNotNull(JpegLsDocument.open(encoded).document)
        val (decoded, result) = requireNotNull(Codec.MakeFromData(encoded)).getImage()

        assertEquals(127, document.nearLossless)
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(decoded)
        sourceSamples.forEachIndexed { index, sample ->
            val pixel = decoded!!.getPixel(index % source.width, index / source.width)
            assertTrue(abs((pixel ushr 16 and 0xFF) - sample[0]) <= 127, "R index=$index")
            assertTrue(abs((pixel ushr 8 and 0xFF) - sample[1]) <= 127, "G index=$index")
            assertTrue(abs((pixel and 0xFF) - sample[2]) <= 127, "B index=$index")
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
    fun `optional CharLS oracle decodes encoded RGB sample pixels exactly`() {
        val oracle = System.getProperty("kanvas.jpeg-ls.oracle.charls").orEmpty()
        if (oracle.isBlank()) return
        val samples = Array(24) { index ->
            val x = index % 6
            val y = index / 6
            intArrayOf(0x41 + ((x + y) and 1) * 0x30, 0x50 + x * 3 + y, 0x42 + ((x * y) and 3))
        }
        val encoded = requireNotNull(
            JpegLsEncoder.encode(
                rgbBitmap(6, 4, samples),
                JpegLsEncoder.Options(rgbInterleaveMode = JpegLsRgbInterleaveMode.Sample),
            ),
        )
        val directory = Files.createTempDirectory("kanvas-jpeg-ls-rgb-sample-oracle-")
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
                samples.flatMap { it.asIterable() }.map(Int::toByte).toByteArray(),
                decoded.copyOfRange(decoded.size - samples.size * 3, decoded.size),
            )
        } finally {
            Files.deleteIfExists(directory.resolve("encoded.jls"))
            Files.deleteIfExists(directory.resolve("decoded.ppm"))
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun `optional CharLS oracle decodes encoded RGB sample run interruption exactly`() {
        val oracle = System.getProperty("kanvas.jpeg-ls.oracle.charls").orEmpty()
        if (oracle.isBlank()) return
        val samples = Array(24) { index ->
            if (index == 2 * 6 + 4) intArrayOf(0x70, 0x60, 0x50) else intArrayOf(0x41, 0x50, 0x42)
        }
        val encoded = requireNotNull(
            JpegLsEncoder.encode(
                rgbBitmap(6, 4, samples),
                JpegLsEncoder.Options(rgbInterleaveMode = JpegLsRgbInterleaveMode.Sample),
            ),
        )
        val directory = Files.createTempDirectory("kanvas-jpeg-ls-rgb-sample-run-oracle-")
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
                samples.flatMap { it.asIterable() }.map(Int::toByte).toByteArray(),
                decoded.copyOfRange(decoded.size - samples.size * 3, decoded.size),
            )
        } finally {
            Files.deleteIfExists(directory.resolve("encoded.jls"))
            Files.deleteIfExists(directory.resolve("decoded.ppm"))
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun `optional CharLS oracle decodes encoded RGB ILV1 NEAR 1 pixels within bound`() {
        val oracle = System.getProperty("kanvas.jpeg-ls.oracle.charls").orEmpty()
        if (oracle.isBlank()) return
        val samples = ppmRgb(charlsResource("line-source.ppm.base64"))
        val source = rgbBitmap(4, 3, samples)
        val encoded = requireNotNull(JpegLsEncoder.encode(source, JpegLsEncoder.Options(1)))
        val directory = Files.createTempDirectory("kanvas-jpeg-ls-rgb-near-oracle-")
        try {
            val input = directory.resolve("encoded.jls")
            val output = directory.resolve("decoded.ppm")
            Files.write(input, encoded)
            val process = ProcessBuilder(oracle, "decode", input.toString(), output.toString())
                .redirectErrorStream(true)
                .start()
            val processOutput = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.waitFor(), processOutput)
            ppmRgb(Files.readAllBytes(output)).forEachIndexed { index, decoded ->
                assertTrue(abs(decoded[0] - samples[index][0]) <= 1, "R index=$index")
                assertTrue(abs(decoded[1] - samples[index][1]) <= 1, "G index=$index")
                assertTrue(abs(decoded[2] - samples[index][2]) <= 1, "B index=$index")
            }
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

    private fun assertCharlsRgbNearFixture(
        fixture: ByteArray,
        source: Array<IntArray>,
        reconstruction: Array<IntArray>,
        near: Int,
    ) {
        val document = requireNotNull(JpegLsDocument.open(fixture).document)
        val (bitmap, result) = requireNotNull(Codec.MakeFromData(fixture)).getImage()

        assertEquals(near, document.nearLossless)
        assertEquals(1, document.interleaveMode)
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        reconstruction.forEachIndexed { index, expected ->
            val pixel = bitmap!!.getPixel(index % bitmap.width, index / bitmap.width)
            assertEquals(expected[0], pixel ushr 16 and 0xFF, "R reconstruction index=$index")
            assertEquals(expected[1], pixel ushr 8 and 0xFF, "G reconstruction index=$index")
            assertEquals(expected[2], pixel and 0xFF, "B reconstruction index=$index")
            assertTrue(abs(expected[0] - source[index][0]) <= near, "R source index=$index")
            assertTrue(abs(expected[1] - source[index][1]) <= near, "G source index=$index")
            assertTrue(abs(expected[2] - source[index][2]) <= near, "B source index=$index")
        }
    }

    private fun charlsResource(name: String): ByteArray = Base64.getDecoder().decode(
        requireNotNull(javaClass.getResourceAsStream("/jpeg-ls-charls/$name")) { "missing CharLS resource $name" }
            .use { input -> input.readBytes().decodeToString().trim() },
    )

    private fun ppmRgb(ppm: ByteArray): Array<IntArray> {
        var position = 0
        fun token(): String {
            while (position < ppm.size && ppm[position].toInt().toChar().isWhitespace()) position++
            val start = position
            while (position < ppm.size && !ppm[position].toInt().toChar().isWhitespace()) position++
            return ppm.copyOfRange(start, position).decodeToString()
        }
        require(token() == "P6")
        val width = token().toInt()
        val height = token().toInt()
        require(token() == "255")
        require(position < ppm.size && ppm[position].toInt().toChar().isWhitespace())
        position++
        require(ppm.size - position == width * height * 3)
        return Array(width * height) { intArrayOf(ppm[position++].u8(), ppm[position++].u8(), ppm[position++].u8()) }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { byte -> byte.toInt().and(0xFF).toString(16).padStart(2, '0') }

    private fun decodeDiagnostic(data: ByteArray): String =
        JpegLsDocument.open(data).document?.decode()?.diagnostic.toString()

    private fun mrfxColorTransform(transform: Int): ByteArray = byteArrayOf(
        0xFF.toByte(), 0xE8.toByte(), 0x00, 0x07,
        'm'.code.toByte(), 'r'.code.toByte(), 'f'.code.toByte(), 'x'.code.toByte(), transform.toByte(),
    )

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
