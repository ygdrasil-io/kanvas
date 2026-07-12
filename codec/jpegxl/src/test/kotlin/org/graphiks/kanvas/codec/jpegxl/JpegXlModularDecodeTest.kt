package org.graphiks.kanvas.codec.jpegxl

import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Base64
import org.graphiks.kanvas.codec.Codec
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/**
 * Pixel evidence for the deliberately narrow, raw JPEG XL Modular profile
 * described in `src/test/resources/jpegxl-modular/README.md`.
 */
class JpegXlModularDecodeTest {

    @Test
    fun `simple one symbol prefix code returns its implicit symbol without reading a code bit`() {
        val modularClass = Class.forName("org.graphiks.kanvas.codec.jpegxl.JpegXlModularKt")
        val readerClass = Class.forName("org.graphiks.kanvas.codec.jpegxl.JxlBits")
        val reader = readerClass.getDeclaredConstructor(
            ByteArray::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
        ).apply { isAccessible = true }.newInstance(byteArrayOf(0b0000_1000), 0, 1)
        val simple = modularClass.getDeclaredMethod(
            "readJxlSimpleHuffman",
            readerClass,
            Int::class.javaPrimitiveType,
        ).apply { isAccessible = true }

        // JPEG XL Prefix/Huffman simple form: num_symbols=1 and symbol=2 in
        // an alphabet of four. There are no bits for the sole codeword.
        val code = assertDoesNotThrow<Any> { simple.invoke(null, reader, 4) }
        val singleton = code.javaClass.getDeclaredMethod("getSingleton").apply { isAccessible = true }.invoke(code)
        assertEquals(2, singleton)
        val position = readerClass.getDeclaredField("position").apply { isAccessible = true }
        val positionBeforeRead = position.getInt(reader)
        val read = code.javaClass.getDeclaredMethod("read", readerClass).apply { isAccessible = true }
        assertEquals(2, read.invoke(code, reader))
        assertEquals(positionBeforeRead, position.getInt(reader))
    }

    @Test
    fun `public codec decodes raw one section modular grayscale pixels exactly`() {
        val encoded = Base64.getMimeDecoder().decode(fixture("single-group-4x3-8bit-lossless.jxl.base64"))
        assertEquals("b01d8f59c10376d91f06d2df8c20e04e34f8684282a7a2f8659f1f6fcc6e97c7", sha256(encoded))
        val expected = byteArrayOf(103, 101, 100, 99, 98, 99, 100, 101, 97, 99, 101, 101)
        val codec = requireNotNull(Codec.MakeFromData(encoded))

        val (actual, result) = codec.getImage()

        val diagnostic = requireNotNull(JpegXlDocument.open(encoded).document).decode().diagnostic
        assertEquals(Codec.Result.kSuccess, result, "diagnostic=$diagnostic")
        val bitmap = requireNotNull(actual)
        assertEquals(4, bitmap.width)
        assertEquals(3, bitmap.height)
        val grayscale = ByteArray(expected.size) { index ->
            ((bitmap.pixels[index] ushr 16) and 0xFF).toByte()
        }
        assertArrayEquals(expected, grayscale)
        bitmap.pixels.forEachIndexed { index, pixel ->
            val sample = expected[index].toInt() and 0xFF
            assertEquals(0xFF000000.toInt() or (sample shl 16) or (sample shl 8) or sample, pixel, "pixel=$index")
        }
    }

    @Test
    fun `public codec decodes an exact jxlc envelope around the proven raw modular profile`() {
        val raw = fixture("flower-510x532-8bit-lossless.jxl")
        val expected = p5(fixture("flower-510x532-8bit-lossless.pgm"))
        val encoded = exactJxlcContainer(raw)
        val codec = requireNotNull(Codec.MakeFromData(encoded))

        val (actual, result) = codec.getImage()

        assertEquals(Codec.Result.kSuccess, result)
        val bitmap = requireNotNull(actual)
        assertEquals(expected.width, bitmap.width)
        assertEquals(expected.height, bitmap.height)
        val grayscale = ByteArray(expected.samples.size) { index ->
            ((bitmap.pixels[index] ushr 16) and 0xFF).toByte()
        }
        assertArrayEquals(expected.samples, grayscale)
    }

    @Test
    fun `public codec decodes the pinned libjxl jxlc modular grayscale fixture exactly`() {
        val expected = p5(fixture("flower-510x532-8bit-lossless.pgm"))
        val encoded = Base64.getMimeDecoder().decode(
            fixture("flower-510x532-8bit-lossless-jxlc.jxl.base64"),
        )
        assertEquals("ee9348318a009ffbae25ba279db37c64bc4a2c729b9a276ad0743c23a8f30218", sha256(encoded))
        val codec = requireNotNull(Codec.MakeFromData(encoded))

        val (actual, result) = codec.getImage()

        assertEquals(Codec.Result.kSuccess, result)
        val bitmap = requireNotNull(actual)
        assertEquals(expected.width, bitmap.width)
        assertEquals(expected.height, bitmap.height)
        val grayscale = ByteArray(expected.samples.size) { index ->
            ((bitmap.pixels[index] ushr 16) and 0xFF).toByte()
        }
        assertArrayEquals(expected.samples, grayscale)
    }

    @Test
    fun `public codec refuses jxlc pixel decode when the envelope carries an extra box`() {
        val encoded = exactJxlcContainer(
            fixture("flower-510x532-8bit-lossless.jxl"),
            extraBox = "Exif" to byteArrayOf(0, 0, 0, 0),
        )
        val document = requireNotNull(JpegXlDocument.open(encoded).document)

        val decoded = document.decode()

        assertNull(decoded.bitmap)
        assertEquals(Codec.Result.kUnimplemented, decoded.diagnostic?.result)
        assertEquals("jpegxl.container.topology.unimplemented", decoded.diagnostic?.code)
    }

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
        assertEquals(
            "ee9348318a009ffbae25ba279db37c64bc4a2c729b9a276ad0743c23a8f30218",
            sha256(Base64.getMimeDecoder().decode(fixture("flower-510x532-8bit-lossless-jxlc.jxl.base64"))),
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
    fun `opt in djxl oracle retains pinned jxlc modular lossless pixels exactly`() {
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
            Files.write(
                input,
                Base64.getMimeDecoder().decode(fixture("flower-510x532-8bit-lossless-jxlc.jxl.base64")),
            )
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

    private fun exactJxlcContainer(raw: ByteArray, extraBox: Pair<String, ByteArray>? = null): ByteArray =
        ByteArrayOutputStream().also { output ->
            output.writeBox("JXL ", byteArrayOf(0x0D, 0x0A, 0x87.toByte(), 0x0A))
            output.writeBox(
                "ftyp",
                byteArrayOf(
                    'j'.code.toByte(), 'x'.code.toByte(), 'l'.code.toByte(), ' '.code.toByte(),
                    0, 0, 0, 0,
                    'j'.code.toByte(), 'x'.code.toByte(), 'l'.code.toByte(), ' '.code.toByte(),
                ),
            )
            output.writeBox("jxlc", raw)
            extraBox?.let { (type, payload) -> output.writeBox(type, payload) }
        }.toByteArray()

    private fun ByteArrayOutputStream.writeBox(type: String, payload: ByteArray) {
        require(type.length == 4)
        writeU32(payload.size + 8)
        write(type.toByteArray(Charsets.ISO_8859_1))
        write(payload)
    }

    private fun ByteArrayOutputStream.writeU32(value: Int) {
        write(value ushr 24)
        write(value ushr 16)
        write(value ushr 8)
        write(value)
    }

}
