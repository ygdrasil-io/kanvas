package org.graphiks.kanvas.codec

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.io.InputStream

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
                    FailsPastLimitStream(9),
                    maxEncodedBytes = 8,
                ),
            )
            assertFalse(matchesCalled)
        } finally {
            Codec.Decoders.unregister(TEST_DECODER_NAME)
        }
    }

    @Test
    fun `legacy unary stream overload remains available to JVM callers`() {
        assertNotNull(
            Codec.Companion::class.java.getMethod("MakeFromStream", InputStream::class.java),
        )
    }

    @Test
    fun `unmaterializable stream budget is rejected before reading`() {
        val stream = object : InputStream() {
            override fun read(): Int = throw AssertionError("unmaterializable budget must not read")
        }

        assertNull(Codec.MakeFromStream(stream, Long.MAX_VALUE))
    }

    private class FailsPastLimitStream(
        private val readableBytes: Int,
    ) : InputStream() {
        private var bytesServed: Int = 0

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

    private companion object {
        const val TEST_DECODER_NAME: String = "codec-stream-limit-test"
    }
}
