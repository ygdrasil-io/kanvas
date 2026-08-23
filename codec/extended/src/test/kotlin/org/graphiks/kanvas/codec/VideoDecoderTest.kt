package org.graphiks.kanvas.codec

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class VideoDecoderTest {
    @Test
    fun `InputStream factory preserves the explicit FFmpeg refusal`() {
        val refusal = assertThrows(NotImplementedError::class.java) {
            VideoDecoder.MakeFromStream(ByteArrayInputStream(byteArrayOf(0)))
        }

        assertTrue(refusal.message!!.contains("STUB.FFMPEG"))
    }
}
