package org.graphiks.kanvas.codec

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkEncodedImageFormat
import org.skia.foundation.SkImageInfo
import org.skia.foundation.skcms.SkcmsICCProfile
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
    fun `unary stream overload accepts a recognized stream beyond the default limit`() {
        val encodedBytes = Codec.DEFAULT_MAX_STREAM_BYTES + 1
        var dispatchedBytes = -1L
        val decoder = object : Codec.Decoder {
            override val name: String = LARGE_STREAM_DECODER_NAME

            override fun matches(data: ByteArray): Boolean =
                data.size.toLong() == encodedBytes &&
                    LARGE_STREAM_HEADER.indices.all { index -> data[index] == LARGE_STREAM_HEADER[index] }

            override fun make(data: ByteArray): Codec {
                dispatchedBytes = data.size.toLong()
                return StreamTestCodec()
            }
        }
        Codec.Decoders.register(decoder)

        try {
            assertNotNull(Codec.MakeFromStream(RecognizedLargeStream(encodedBytes)))
            assertEquals(encodedBytes, dispatchedBytes)
        } finally {
            Codec.Decoders.unregister(LARGE_STREAM_DECODER_NAME)
        }
    }

    @Test
    fun `unmaterializable stream budget is rejected before reading`() {
        val stream = object : InputStream() {
            override fun read(): Int = throw AssertionError("unmaterializable budget must not read")
        }

        assertNull(Codec.MakeFromStream(stream, Long.MAX_VALUE))
    }

    @Test
    fun `stream budget requiring an unmaterializable sentinel is rejected before reading`() {
        val stream = object : InputStream() {
            override fun read(): Int = throw AssertionError("unmaterializable sentinel budget must not read")
        }
        val maxMaterializableBytes = Int.MAX_VALUE.toLong() - 8L

        assertNull(Codec.MakeFromStream(stream, maxMaterializableBytes))
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

    private class RecognizedLargeStream(
        private val encodedBytes: Long,
    ) : InputStream() {
        private var bytesServed: Long = 0

        override fun read(): Int {
            if (bytesServed >= encodedBytes) return -1
            val value = if (bytesServed < LARGE_STREAM_HEADER.size) {
                LARGE_STREAM_HEADER[bytesServed.toInt()].toInt() and 0xFF
            } else {
                0
            }
            bytesServed++
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            if (bytesServed >= encodedBytes) return -1
            val count = minOf(length.toLong(), encodedBytes - bytesServed).toInt()
            buffer.fill(0, offset, offset + count)
            if (bytesServed < LARGE_STREAM_HEADER.size) {
                val headerBytes = minOf(LARGE_STREAM_HEADER.size - bytesServed.toInt(), count)
                LARGE_STREAM_HEADER.copyInto(buffer, offset, 0, headerBytes)
            }
            bytesServed += count.toLong()
            return count
        }
    }

    private class StreamTestCodec : Codec() {
        override fun getInfo(): SkImageInfo = SkImageInfo.Make(
            width = 1,
            height = 1,
            colorType = SkColorType.kRGBA_8888,
            alphaType = SkAlphaType.kUnpremul,
            colorSpace = SkColorSpace.makeSRGB(),
        )

        override fun getEncodedFormat(): SkEncodedImageFormat = SkEncodedImageFormat.kPNG

        override fun getICCProfile(): SkcmsICCProfile? = null

        override fun getPixels(info: SkImageInfo, dst: SkBitmap): Codec.Result = Codec.Result.kSuccess
    }

    private companion object {
        const val TEST_DECODER_NAME: String = "codec-stream-limit-test"
        const val LARGE_STREAM_DECODER_NAME: String = "codec-large-stream-test"
        val LARGE_STREAM_HEADER: ByteArray = "kanvas-large-stream".encodeToByteArray()
    }
}
