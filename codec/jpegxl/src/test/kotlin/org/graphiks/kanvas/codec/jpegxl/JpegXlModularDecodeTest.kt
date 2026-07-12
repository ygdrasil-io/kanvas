package org.graphiks.kanvas.codec.jpegxl

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.graphiks.kanvas.codec.Codec
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/**
 * Pixel evidence for the deliberately narrow, raw JPEG XL Modular profile
 * described in `src/test/resources/jpegxl-modular/README.md`.
 */
class JpegXlModularDecodeTest {

    @Test
    fun `pinned raw modular lossless fixture has documented SHA-256`() {
        assertEquals(
            "c68282d6f7644cdf3485010a566c18b5ded40c3c25dcce59fe3672eeade06aa9",
            sha256(fixture("flower-510x532-8bit-lossless.jxl")),
        )
        assertEquals(
            "4580f75490c0bc38159a381615571e2a341fc0adde99b4b3b0ed5bbea97da1fc",
            sha256(fixture("flower-510x532-8bit-lossless.pgm")),
        )
    }

    @Test
    fun `public codec decodes narrow raw modular lossless grayscale pixels exactly`() {
        val expected = p5(fixture("flower-510x532-8bit-lossless.pgm"))
        val encoded = fixture("flower-510x532-8bit-lossless.jxl")
        val codec = requireNotNull(Codec.MakeFromData(encoded))

        val (actual, result) = codec.getImage()

        val diagnostic = requireNotNull(JpegXlDocument.open(encoded).document).decode().diagnostic
        assertEquals(Codec.Result.kSuccess, result, "diagnostic=$diagnostic")
        val bitmap = requireNotNull(actual)
        assertEquals(expected.width, bitmap.width)
        assertEquals(expected.height, bitmap.height)
        val grayscale = ByteArray(expected.samples.size) { index ->
            ((bitmap.pixels[index] ushr 16) and 0xFF).toByte()
        }
        assertArrayEquals(expected.samples, grayscale)
        bitmap.pixels.forEachIndexed { index, pixel ->
            val sample = expected.samples[index].toInt() and 0xFF
            assertEquals(0xFF000000.toInt() or (sample shl 16) or (sample shl 8) or sample, pixel, "pixel=$index")
        }
    }

    @Test
    fun `opt in djxl oracle retains pinned modular lossless pixels exactly`() {
        val configuredOracle = System.getProperty("kanvas.jpegxl.oracle.djxl").orEmpty()
        assumeTrue(
            configuredOracle.isNotBlank(),
            "Set -PjpegxlOracleDjxl=/absolute/path/to/djxl to enable the external JPEG XL oracle",
        )
        val oracle = Path.of(configuredOracle)
        assumeTrue(Files.isExecutable(oracle), "djxl oracle is not executable: $oracle")
        val temporaryDirectory = Files.createTempDirectory("kanvas-jpegxl-modular-oracle-")
        try {
            val input = temporaryDirectory.resolve("fixture.jxl")
            val output = temporaryDirectory.resolve("oracle.pgm")
            Files.write(input, fixture("flower-510x532-8bit-lossless.jxl"))
            val process = ProcessBuilder(oracle.toString(), input.toString(), output.toString(), "--num_threads=0", "-v")
                .redirectErrorStream(true)
                .start()
            val log = process.inputStream.use { it.readBytes().decodeToString() }
            assertEquals(0, process.waitFor(), "djxl output=$log")
            assertArrayEquals(
                fixture("flower-510x532-8bit-lossless.pgm"),
                Files.readAllBytes(output),
            )
        } finally {
            Files.walk(temporaryDirectory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    private fun fixture(name: String): ByteArray = requireNotNull(
        javaClass.getResourceAsStream("/jpegxl-modular/$name"),
    ) { "missing JPEG XL Modular fixture: $name" }.use { it.readBytes() }

    private fun sha256(data: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(data)
        .joinToString(separator = "") { byte -> byte.toInt().and(0xFF).toString(16).padStart(2, '0') }

    private fun p5(data: ByteArray): P5 {
        var cursor = 0
        fun token(): String {
            while (cursor < data.size && data[cursor].toInt().toChar().isWhitespace()) cursor++
            if (cursor < data.size && data[cursor] == '#'.code.toByte()) {
                while (cursor < data.size && data[cursor] != '\n'.code.toByte()) cursor++
                return token()
            }
            val start = cursor
            while (cursor < data.size && !data[cursor].toInt().toChar().isWhitespace()) cursor++
            return data.copyOfRange(start, cursor).decodeToString()
        }
        require(token() == "P5")
        val width = token().toInt()
        val height = token().toInt()
        require(token() == "255")
        while (cursor < data.size && data[cursor].toInt().toChar().isWhitespace()) cursor++
        val samples = data.copyOfRange(cursor, data.size)
        require(samples.size == width * height)
        return P5(width, height, samples)
    }

    private data class P5(val width: Int, val height: Int, val samples: ByteArray)
}
