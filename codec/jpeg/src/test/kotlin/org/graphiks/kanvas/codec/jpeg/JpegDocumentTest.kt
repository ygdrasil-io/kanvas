package org.graphiks.kanvas.codec.jpeg

import org.graphiks.kanvas.codec.test.CodecTestFixtures
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class JpegDocumentTest {

    @Test
    fun `open retains APP bytes and refuses an encoded-byte budget overflow`() {
        val data = withAppSegments(
            CodecTestFixtures.simpleGrayscaleJpeg(8, 8),
            appSegment(0xE3, byteArrayOf(1, 2)),
        )

        assertArrayEquals(
            byteArrayOf(1, 2),
            JpegDocument.open(data).document!!.segments.single { it.marker == 0xE3 }.payload,
        )
        assertEquals(
            "jpeg.limit.encoded-bytes",
            JpegDocument.open(data, JpegLimits(8, 64, 8, 8)).diagnostic!!.code,
        )
    }

    @Test
    fun `stream open refuses input before unbounded read`() {
        assertEquals(
            "jpeg.limit.encoded-bytes",
            JpegDocument.open(
                ByteArrayInputStream(ByteArray(9)),
                JpegLimits(8, 64, 8, 8),
            ).diagnostic!!.code,
        )
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
}
