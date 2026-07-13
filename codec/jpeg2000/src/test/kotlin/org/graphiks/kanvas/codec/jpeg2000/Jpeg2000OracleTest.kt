package org.graphiks.kanvas.codec.jpeg2000

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.junit.jupiter.api.parallel.Resources

/**
 * Opt-in interoperability evidence. The OpenJPEG executable is never
 * discovered through PATH and is not a production or normal-CI dependency.
 */
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class Jpeg2000OracleTest {

    @Test
    fun `configured but blank OpenJPEG oracle fails rather than skips`() {
        val previousOracle = System.getProperty(OPENJPEG_ORACLE_PROPERTY)
        System.setProperty(OPENJPEG_ORACLE_PROPERTY, "")
        try {
            assertThrows(AssertionError::class.java) {
                decodeWithOracle(Jpeg2000TestFixtures.openJpegLossless5x3(), width = 5, height = 3)
            }
        } finally {
            if (previousOracle == null) System.clearProperty(OPENJPEG_ORACLE_PROPERTY)
            else System.setProperty(OPENJPEG_ORACLE_PROPERTY, previousOracle)
        }
    }

    @Test
    fun `configured but non executable OpenJPEG oracle fails rather than skips`() {
        val previousOracle = System.getProperty(OPENJPEG_ORACLE_PROPERTY)
        val missingOracle = Files.createTempDirectory("kanvas-jpeg2000-missing-openjpeg-")
            .resolve("opj_decompress")
        System.setProperty(
            OPENJPEG_ORACLE_PROPERTY,
            missingOracle.toString(),
        )
        try {
            assertThrows(AssertionError::class.java) {
                decodeWithOracle(Jpeg2000TestFixtures.openJpegLossless5x3(), width = 5, height = 3)
            }
        } finally {
            if (previousOracle == null) System.clearProperty(OPENJPEG_ORACLE_PROPERTY)
            else System.setProperty(OPENJPEG_ORACLE_PROPERTY, previousOracle)
            Files.deleteIfExists(missingOracle.parent)
        }
    }

    @Test
    @DisplayName("OpenJPEG oracle fixture=openjpeg-2.5.4-lossless-ndecomp0-5x5.jp2 compares every source and Kanvas pixel")
    fun `explicit OpenJPEG oracle compares the JP2 source and Kanvas pixels`() {
        val fixture = Jpeg2000TestFixtures.openJpegLosslessNdecomp0_5x5Jp2()
        val sourcePixels = decodeWithOracle(
            fixture,
            width = 5,
            height = 5,
            fixtureFileName = JP2_FIXTURE_ID,
        )
        val decoded = requireNotNull(Jpeg2000Document.open(fixture).document).decode()
        assertNull(decoded.diagnostic, "fixture=$JP2_FIXTURE_ID")
        val bitmap = requireNotNull(decoded.bitmap) { "fixture=$JP2_FIXTURE_ID" }
        assertEquals(5, bitmap.width, "fixture=$JP2_FIXTURE_ID")
        assertEquals(5, bitmap.height, "fixture=$JP2_FIXTURE_ID")
        val kanvasPixels = ByteArray(bitmap.pixels8888.size) { index ->
            val pixel = bitmap.pixels8888[index]
            val sample = pixel and 0xFF
            assertEquals(0xFF, pixel ushr 24, "fixture=$JP2_FIXTURE_ID pixel=$index alpha")
            assertEquals(sample, (pixel ushr 16) and 0xFF, "fixture=$JP2_FIXTURE_ID pixel=$index red")
            assertEquals(sample, (pixel ushr 8) and 0xFF, "fixture=$JP2_FIXTURE_ID pixel=$index green")
            sample.toByte()
        }

        assertArrayEquals(sourcePixels, kanvasPixels, "fixture=$JP2_FIXTURE_ID OpenJPEG P5 vs Kanvas")
    }

    @Test
    fun `P5 payload preserves a leading whitespace sample`() {
        val p5 = "P5\n1 1\n255\n".encodeToByteArray() + byteArrayOf('\n'.code.toByte())

        assertArrayEquals(byteArrayOf('\n'.code.toByte()), p5Pixels(p5, width = 1, height = 1))
    }

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

    private fun decodeWithOracle(
        j2k: ByteArray,
        width: Int,
        height: Int,
        fixtureFileName: String = "fixture.j2k",
    ): ByteArray {
        val configuredOracle = System.getProperty(OPENJPEG_ORACLE_PROPERTY) ?: run {
            assumeTrue(false, "Set -Pjpeg2000OracleOpenJpeg=/absolute/path/to/opj_decompress to enable")
            error("unreachable")
        }
        assertTrue(configuredOracle.isNotBlank(), "OpenJPEG oracle path must not be blank")
        val oracle = Path.of(configuredOracle)
        assertTrue(Files.isExecutable(oracle), "OpenJPEG oracle is not executable: $oracle")

        val directory = Files.createTempDirectory("kanvas-jpeg2000-openjpeg-oracle-")
        try {
            val input = directory.resolve(fixtureFileName)
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
        assertTrue(
            cursor < pgm.size && pgm[cursor].toInt().toChar().isWhitespace(),
            "P5 header is missing its payload separator",
        )
        val separator = pgm[cursor++]
        if (separator == '\r'.code.toByte() && cursor < pgm.size && pgm[cursor] == '\n'.code.toByte()) cursor++
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
        const val OPENJPEG_ORACLE_PROPERTY = "kanvas.jpeg2000.oracle.openjpeg"
        const val JP2_FIXTURE_ID = "openjpeg-2.5.4-lossless-ndecomp0-5x5.jp2"

        val expectedPixels = byteArrayOf(
            0, 1, 127, 254.toByte(), 255.toByte(),
            16, 32, 64, 128.toByte(), 240.toByte(),
            17, 34, 68, 136.toByte(), 238.toByte(),
        )
    }
}
