package org.graphiks.kanvas.codec.png

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PngDocumentTest {

    @Test
    fun `saves pristine source bytes identically including unknown ancillary chunks and IDAT segmentation`() {
        val source = png(
            "IHDR" to ihdr(width = 2, height = 3),
            "vpAg" to byteArrayOf(0x11, 0x22),
            "IDAT" to byteArrayOf(0x33),
            "IDAT" to byteArrayOf(0x44, 0x55),
            "IEND" to ByteArray(0),
        )

        val saved = open(source).save()

        assertArrayEquals(source, saved.bytes)
        assertTrue(saved.report.isEmpty)
    }

    @Test
    fun `snapshots caller bytes before parsing and protects every byte result`() {
        val source = png(
            "IHDR" to ihdr(width = 2, height = 3),
            "IDAT" to byteArrayOf(0x11),
            "IEND" to ByteArray(0),
        )
        val expected = source.copyOf()
        val document = open(source)

        source[0] = 0
        val exposedOriginal = document.originalBytes
        exposedOriginal[0] = 0
        val exposedSave = document.save().bytes
        exposedSave[0] = 0

        assertArrayEquals(expected, document.originalBytes)
        assertArrayEquals(expected, document.save().bytes)
        assertEquals(2, document.header.width)
        assertEquals(3, document.header.height)
    }

    @Test
    fun `exposes immutable shared parser chunk records`() {
        val document = open(
            png(
                "IHDR" to ihdr(),
                "vpAg" to byteArrayOf(0x11),
                "IDAT" to byteArrayOf(0x22),
                "IEND" to ByteArray(0),
            ),
        )

        @Suppress("UNCHECKED_CAST")
        val mutableView = document.chunks as MutableList<PngChunkRecord>

        assertThrows(UnsupportedOperationException::class.java) { mutableView.clear() }
        assertEquals(listOf("IHDR", "vpAg", "IDAT", "IEND"), document.chunks.map(PngChunkRecord::type))
    }

    @Test
    fun `propagates APNG parser diagnostics with chunk type and offset`() {
        val result = PngDocument.open(
            png(
                "IHDR" to ihdr(),
                "acTL" to ByteArray(8),
                "IDAT" to byteArrayOf(0x11),
                "IEND" to ByteArray(0),
            ),
        )

        assertInstanceOf(PngDocumentOpenResult.Failure::class.java, result)
        val diagnostic = (result as PngDocumentOpenResult.Failure).diagnostic
        assertEquals("png.apng.unsupported", diagnostic.code)
        assertEquals(33L, diagnostic.offset)
        assertEquals("acTL", diagnostic.chunkType)
    }

    @Test
    fun `propagates parser diagnostics with chunk type and offset`() {
        val source = png(
            "IHDR" to ihdr(),
            "IDAT" to byteArrayOf(0x11),
            "IEND" to ByteArray(0),
        )
        source[41] = (source[41].toInt() xor 1).toByte()

        val result = PngDocument.open(source)

        assertInstanceOf(PngDocumentOpenResult.Failure::class.java, result)
        val diagnostic = (result as PngDocumentOpenResult.Failure).diagnostic
        assertEquals("png.chunk.crc.invalid", diagnostic.code)
        assertEquals(33L, diagnostic.offset)
        assertEquals("IDAT", diagnostic.chunkType)
    }

    @Test
    fun `rejects over-limit input before invoking the snapshot path`() {
        val source = ByteArray(16)
        var snapshotCalled = false

        val result = PngDocument.open(
            bytes = source,
            limits = PngContainerLimits.Default.copy(maxInputBytes = source.size.toLong() - 1L),
        ) {
            snapshotCalled = true
            error("The snapshot path must not run for over-limit input")
        }

        assertInstanceOf(PngDocumentOpenResult.Failure::class.java, result)
        val diagnostic = (result as PngDocumentOpenResult.Failure).diagnostic
        assertEquals("png.input.limit", diagnostic.code)
        assertEquals(0L, diagnostic.offset)
        assertEquals(null, diagnostic.chunkType)
        assertFalse(snapshotCalled)
    }

    private fun open(bytes: ByteArray): PngDocument {
        val result = PngDocument.open(bytes)
        assertInstanceOf(PngDocumentOpenResult.Success::class.java, result)
        return (result as PngDocumentOpenResult.Success).document
    }

    private fun png(vararg chunks: Pair<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().apply {
            write(PNG_SIGNATURE)
            for ((type, payload) in chunks) writeChunk(type, payload)
        }.toByteArray()

    private fun ihdr(
        width: Int = 1,
        height: Int = 1,
    ): ByteArray = ByteArray(13).also { bytes ->
        writeI32BE(bytes, 0, width)
        writeI32BE(bytes, 4, height)
        bytes[8] = 8
    }

    private fun ByteArrayOutputStream.writeChunk(type: String, payload: ByteArray) {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        writeI32BE(payload.size)
        write(typeBytes)
        write(payload)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(payload)
        writeI32BE(crc.value.toInt())
    }

    private fun ByteArrayOutputStream.writeI32BE(value: Int) {
        write((value ushr 24) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 8) and 0xFF)
        write(value and 0xFF)
    }

    private fun writeI32BE(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 24).toByte()
        bytes[offset + 1] = (value ushr 16).toByte()
        bytes[offset + 2] = (value ushr 8).toByte()
        bytes[offset + 3] = value.toByte()
    }

    private companion object {
        val PNG_SIGNATURE: ByteArray = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
    }
}
