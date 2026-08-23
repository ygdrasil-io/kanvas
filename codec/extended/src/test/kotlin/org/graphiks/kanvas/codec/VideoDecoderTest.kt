package org.graphiks.kanvas.codec

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.InputStream

class VideoDecoderTest {
    @Test
    fun `InputStream factory preserves the explicit FFmpeg refusal without consuming the stream`() {
        val stream = RecordingInputStream()
        val refusal = assertThrows(NotImplementedError::class.java) {
            VideoDecoder.MakeFromStream(stream)
        }

        assertTrue(refusal.message!!.contains("STUB.FFMPEG"))
        assertEquals(0, stream.readCount)
        assertEquals(0, stream.closeCount)
    }

    private class RecordingInputStream : InputStream() {
        var readCount: Int = 0
            private set
        var closeCount: Int = 0
            private set

        override fun read(): Int {
            readCount++
            return -1
        }

        override fun close() {
            closeCount++
        }
    }
}
