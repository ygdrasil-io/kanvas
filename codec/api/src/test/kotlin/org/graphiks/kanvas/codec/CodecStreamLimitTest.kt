package org.graphiks.kanvas.codec

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class CodecStreamLimitTest {

    @Test
    fun `stream budget prevents dispatch after maximum encoded bytes`() {
        var matchesCalled = false
        val decoder = object : Codec.Decoder {
            override val name: String = TEST_DECODER_NAME

            override fun matches(data: ByteArray): Boolean {
                matchesCalled = true
                return true
            }

            override fun make(data: ByteArray): Codec? = error("oversized stream must not be dispatched")
        }
        Codec.Decoders.register(decoder)

        try {
            assertNull(
                Codec.MakeFromStream(
                    ByteArrayInputStream(ByteArray(9)),
                    maxEncodedBytes = 8,
                ),
            )
            assertFalse(matchesCalled)
        } finally {
            Codec.Decoders.unregister(TEST_DECODER_NAME)
        }
    }

    private companion object {
        const val TEST_DECODER_NAME: String = "codec-stream-limit-test"
    }
}
