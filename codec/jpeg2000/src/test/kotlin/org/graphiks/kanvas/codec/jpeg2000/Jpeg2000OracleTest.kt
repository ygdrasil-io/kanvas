package org.graphiks.kanvas.codec.jpeg2000

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/**
 * Opt-in interoperability evidence. The OpenJPEG executable is never
 * discovered through PATH and is not a production or normal-CI dependency.
 */
class Jpeg2000OracleTest {

    @Test
    fun `explicit OpenJPEG oracle decodes pinned reversible lossless fixture exactly`() {
        assertArrayEquals(
            expectedPixels,
            decodeWithOracle(Jpeg2000TestFixtures.openJpegLossless5x3(), width = 5, height = 3),
        )
    }

    @Test
    fun `explicit OpenJPEG oracle decodes pinned two-codeblock fixture exactly`() {
        assertArrayEquals(
            sourceP2Pixels(
                "/jpeg2000-openjpeg/source-two-codeblocks-96x17.pgm",
                width = 96,
                height = 17,
            ),
            decodeWithOracle(Jpeg2000TestFixtures.openJpegLosslessTwoCodeblocks96x17(), width = 96, height = 17),
        )
    }

    @Test
    fun `explicit OpenJPEG oracle decodes pinned Ndecomp one fixture exactly`() {
        assertArrayEquals(
            sourceP2Pixels(
                "/jpeg2000-openjpeg/source-two-codeblocks-96x17.pgm",
                width = 96,
                height = 17,
            ),
            decodeWithOracle(Jpeg2000TestFixtures.openJpegLosslessNdecomp1_96x17(), width = 96, height = 17),
        )
    }

    @Test
    fun `explicit OpenJPEG oracle decodes pinned odd Ndecomp one fixture exactly`() {
        assertArrayEquals(
            expectedPixels,
            decodeWithOracle(Jpeg2000TestFixtures.openJpegLosslessNdecomp1_5x3(), width = 5, height = 3),
        )
    }

    @Test
    fun `explicit OpenJPEG oracle decodes pinned random odd Ndecomp one fixture exactly`() {
        assertArrayEquals(
            sourceP2Pixels(
                "/jpeg2000-openjpeg/source-ndecomp2-5x5-random.pgm",
                width = 5,
                height = 5,
            ),
            decodeWithOracle(Jpeg2000TestFixtures.openJpegLosslessNdecomp1_5x5(), width = 5, height = 5),
        )
    }

    @Test
    fun `explicit OpenJPEG oracle decodes pinned Ndecomp two fixture exactly`() {
        assertArrayEquals(
            sourceP2Pixels(
                "/jpeg2000-openjpeg/source-ndecomp2-8x8.pgm",
                width = 8,
                height = 8,
            ),
            decodeWithOracle(Jpeg2000TestFixtures.openJpegLosslessNdecomp2_8x8(), width = 8, height = 8),
        )
    }

    @Test
    fun `explicit OpenJPEG oracle decodes pinned odd Ndecomp two fixture exactly`() {
        assertArrayEquals(
            sourceP2Pixels(
                "/jpeg2000-openjpeg/source-ndecomp2-5x5-random.pgm",
                width = 5,
                height = 5,
            ),
            decodeWithOracle(Jpeg2000TestFixtures.openJpegLosslessNdecomp2_5x5(), width = 5, height = 5),
        )
    }

    private fun decodeWithOracle(j2k: ByteArray, width: Int, height: Int): ByteArray {
        val configuredOracle = System.getProperty("kanvas.jpeg2000.oracle.openjpeg").orEmpty()
        assumeTrue(
            configuredOracle.isNotBlank(),
            "Set -Pjpeg2000OracleOpenJpeg=/absolute/path/to/opj_decompress to enable",
        )
        val oracle = Path.of(configuredOracle)
        assumeTrue(Files.isExecutable(oracle), "OpenJPEG oracle is not executable: $oracle")

        val directory = Files.createTempDirectory("kanvas-jpeg2000-openjpeg-oracle-")
        try {
            val input = directory.resolve("fixture.j2k")
            val output = directory.resolve("decoded.pgm")
            Files.write(input, j2k)

            val process = ProcessBuilder(oracle.toString(), "-i", input.toString(), "-o", output.toString())
                .redirectErrorStream(true)
                .start()
            val log = process.inputStream.use { it.readBytes().decodeToString() }
            assertEquals(0, process.waitFor(), "OpenJPEG output=$log")

            return p5Pixels(Files.readAllBytes(output), width, height)
        } finally {
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    private fun p5Pixels(pgm: ByteArray, width: Int, height: Int): ByteArray {
        var cursor = 0
        fun token(): String {
            while (cursor < pgm.size && pgm[cursor].toInt().toChar().isWhitespace()) cursor++
            if (cursor < pgm.size && pgm[cursor] == '#'.code.toByte()) {
                while (cursor < pgm.size && pgm[cursor] != '\n'.code.toByte()) cursor++
                return token()
            }
            val start = cursor
            while (cursor < pgm.size && !pgm[cursor].toInt().toChar().isWhitespace()) cursor++
            return pgm.copyOfRange(start, cursor).decodeToString()
        }

        assertEquals("P5", token())
        assertEquals(width.toString(), token())
        assertEquals(height.toString(), token())
        assertEquals("255", token())
        while (cursor < pgm.size && pgm[cursor].toInt().toChar().isWhitespace()) cursor++
        val pixels = pgm.copyOfRange(cursor, pgm.size)
        assertTrue(pixels.size == width * height, "P5 payload size=${pixels.size}")
        return pixels
    }

    private fun sourceP2Pixels(resource: String, width: Int, height: Int): ByteArray {
        val pgm = requireNotNull(javaClass.getResourceAsStream(resource)) { "Missing source fixture $resource" }
            .use { it.readBytes() }
        var cursor = 0
        fun token(): String {
            while (cursor < pgm.size && pgm[cursor].toInt().toChar().isWhitespace()) cursor++
            if (cursor < pgm.size && pgm[cursor] == '#'.code.toByte()) {
                while (cursor < pgm.size && pgm[cursor] != '\n'.code.toByte()) cursor++
                return token()
            }
            val start = cursor
            while (cursor < pgm.size && !pgm[cursor].toInt().toChar().isWhitespace()) cursor++
            return pgm.copyOfRange(start, cursor).decodeToString()
        }

        assertEquals("P2", token())
        assertEquals(width.toString(), token())
        assertEquals(height.toString(), token())
        assertEquals("255", token())
        return ByteArray(width * height) { token().toInt().toByte() }
    }

    private companion object {
        val expectedPixels = byteArrayOf(
            0, 1, 127, 254.toByte(), 255.toByte(),
            16, 32, 64, 128.toByte(), 240.toByte(),
            17, 34, 68, 136.toByte(), 238.toByte(),
        )
    }
}
