package org.graphiks.kanvas.codec.jpeg

import org.graphiks.kanvas.codec.test.CodecTestFixtures
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.InputStream

class JpegDocumentTest {

    @Test
    fun `open retains APP bytes through explicit copies`() {
        val data = withAppSegments(
            CodecTestFixtures.simpleGrayscaleJpeg(8, 8),
            appSegment(0xE3, byteArrayOf(1, 2)),
        )
        val document = JpegDocument.open(data).document!!
        val segment = document.segments.single { it.marker == 0xE3 }

        assertArrayEquals(
            byteArrayOf(1, 2),
            document.copyPayload(segment),
        )
        document.copyPayload(segment)[0] = 99
        data[segment.range.first] = 42
        assertEquals(
            byteArrayOf(1, 2).toList(),
            document.copyPayload(segment).toList(),
        )

        val otherDocument = JpegDocument.open(
            withAppSegments(
                CodecTestFixtures.simpleGrayscaleJpeg(8, 8),
                appSegment(0xE3, byteArrayOf(3, 4)),
            ),
        ).document!!
        assertThrows(IllegalArgumentException::class.java) {
            otherDocument.copyPayload(segment)
        }
    }

    @Test
    fun `segments list cannot be mutated`() {
        val document = JpegDocument.open(CodecTestFixtures.simpleGrayscaleJpeg()).document!!
        val originalSize = document.segments.size

        @Suppress("UNCHECKED_CAST")
        val mutableSegments = document.segments as MutableList<JpegSegment>
        assertThrows(UnsupportedOperationException::class.java) {
            mutableSegments.clear()
        }
        assertEquals(originalSize, document.segments.size)
    }

    @Test
    fun `open rejects malformed SOS and incomplete SOF with invalid-input diagnostics`() {
        assertInvalid(
            byteArrayOf(
                0xFF.toByte(), 0xD8.toByte(),
                0xFF.toByte(), 0xDA.toByte(), 0, 2,
                0xFF.toByte(), 0xD9.toByte(),
            ),
        )
        assertInvalid(
            byteArrayOf(
                0xFF.toByte(), 0xD8.toByte(),
                0xFF.toByte(), 0xC0.toByte(), 0, 8,
                8, 0, 8, 0, 8, 1,
                0xFF.toByte(), 0xD9.toByte(),
            ),
        )
    }

    @Test
    fun `open refuses encoded-byte overflow and unmaterializable limits`() {
        val limits = JpegLimits(8, 64, 8, 8)
        assertEquals(
            "jpeg.limit.encoded-bytes",
            JpegDocument.open(ByteArray(9), limits).diagnostic!!.code,
        )
        assertEquals(
            "jpeg.limit.encoded-bytes",
            JpegDocument.open(
                byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()),
                JpegLimits(Long.MAX_VALUE, 64, 8, 8),
            ).diagnostic!!.code,
        )
    }

    @Test
    fun `stream open stops after maximum plus one bytes`() {
        val maximum = 8
        val stream = FailsPastLimitStream(maximum + 1)

        assertEquals(
            "jpeg.limit.encoded-bytes",
            JpegDocument.open(stream, JpegLimits(maximum.toLong(), 64, 8, 8)).diagnostic!!.code,
        )
        assertEquals(maximum + 1, stream.bytesServed)
    }

    @Test
    fun `stream open rejects a budget requiring an unmaterializable sentinel before reading`() {
        val stream = object : InputStream() {
            override fun read(): Int = throw AssertionError("unmaterializable sentinel budget must not read")
        }
        val maxMaterializableBytes = Int.MAX_VALUE.toLong() - 8L

        assertEquals(
            "jpeg.limit.encoded-bytes",
            JpegDocument.open(stream, JpegLimits(maxMaterializableBytes, 64, 8, 8)).diagnostic!!.code,
        )
    }

    private fun assertInvalid(data: ByteArray) {
        val diagnostic = JpegDocument.open(data).diagnostic!!
        assertEquals("jpeg.input.invalid", diagnostic.code)
        assertEquals(2, diagnostic.offset)
    }

    private fun withAppSegments(jpeg: ByteArray, vararg segments: ByteArray): ByteArray =
        jpeg.copyOfRange(0, 2) + segments.fold(ByteArray(0)) { bytes, segment -> bytes + segment } +
            jpeg.copyOfRange(2, jpeg.size)

    private fun appSegment(marker: Int, payload: ByteArray): ByteArray {
        val length = payload.size + 2
        return byteArrayOf(
            0xFF.toByte(),
            marker.toByte(),
            (length ushr 8).toByte(),
            length.toByte(),
        ) + payload
    }

    private class FailsPastLimitStream(
        private val readableBytes: Int,
    ) : InputStream() {
        var bytesServed: Int = 0
            private set

        override fun read(): Int {
            if (bytesServed >= readableBytes) {
                throw AssertionError("stream read beyond maximum plus one bytes")
            }
            bytesServed++
            return 0
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (bytesServed >= readableBytes) {
                throw AssertionError("stream read beyond maximum plus one bytes")
            }
            val count = minOf(length, readableBytes - bytesServed)
            buffer.fill(0, offset, offset + count)
            bytesServed += count
            return count
        }
    }
}
