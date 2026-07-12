package org.graphiks.kanvas.codec.jpegls

import java.util.Base64
import java.nio.file.Files
import org.graphiks.kanvas.codec.Codec
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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

    private fun grayscaleBitmap(width: Int, height: Int, samples: IntArray): SkBitmap {
        require(samples.size == width * height)
        return SkBitmap(width, height).also { bitmap ->
            samples.forEachIndexed { index, sample ->
                bitmap.setPixel(index % width, index / width, 0xFF000000.toInt() or (sample shl 16) or (sample shl 8) or sample)
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

        val DEFAULT_LSE: ByteArray = byteArrayOf(
            0xFF.toByte(), 0xF8.toByte(), 0x00, 0x0D,
            0x01,
            0x00, 0xFF.toByte(),
            0x00, 0x03,
            0x00, 0x07,
            0x00, 0x15,
            0x00, 0x40,
        )

        val JFIF_APP0: ByteArray = byteArrayOf(
            0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10,
            'J'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 0,
            0x01, 0x02, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
        )
    }
}
