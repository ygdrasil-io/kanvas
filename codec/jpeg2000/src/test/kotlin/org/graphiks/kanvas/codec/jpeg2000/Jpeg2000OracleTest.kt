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
            Files.write(input, Jpeg2000TestFixtures.openJpegLossless5x3())

            val process = ProcessBuilder(oracle.toString(), "-i", input.toString(), "-o", output.toString())
                .redirectErrorStream(true)
                .start()
            val log = process.inputStream.use { it.readBytes().decodeToString() }
            assertEquals(0, process.waitFor(), "OpenJPEG output=$log")

            assertArrayEquals(expectedPixels, p5Pixels(Files.readAllBytes(output)))
        } finally {
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    private fun p5Pixels(pgm: ByteArray): ByteArray {
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
        assertEquals("5", token())
        assertEquals("3", token())
        assertEquals("255", token())
        while (cursor < pgm.size && pgm[cursor].toInt().toChar().isWhitespace()) cursor++
        val pixels = pgm.copyOfRange(cursor, pgm.size)
        assertTrue(pixels.size == 15, "P5 payload size=${pixels.size}")
        return pixels
    }

    private companion object {
        val expectedPixels = byteArrayOf(
            0, 1, 127, 254.toByte(), 255.toByte(),
            16, 32, 64, 128.toByte(), 240.toByte(),
            17, 34, 68, 136.toByte(), 238.toByte(),
        )
    }
}
