package org.skia.foundation.stream

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SkDynamicMemoryWStreamTest {

    @Test
    fun `write appends and bytesWritten tracks the total`() {
        val stream = SkDynamicMemoryWStream()
        assertEquals(0L, stream.bytesWritten())
        assertTrue(stream.write(byteArrayOf(1, 2, 3, 4), 4))
        assertEquals(4L, stream.bytesWritten())
        assertTrue(stream.write(byteArrayOf(5, 6), 2))
        assertEquals(6L, stream.bytesWritten())
    }

    @Test
    fun `write 1 MB in chunks and detach as data`() {
        val stream = SkDynamicMemoryWStream()
        val chunk = ByteArray(1024) { (it and 0xFF).toByte() }
        val totalChunks = 1024
        repeat(totalChunks) {
            assertTrue(stream.write(chunk, chunk.size))
        }
        assertEquals((1024L * 1024L), stream.bytesWritten())

        val data = stream.detachAsData()
        assertEquals(1024 * 1024, data.size)
        // Reset semantics : after detach the stream is empty.
        assertEquals(0L, stream.bytesWritten())

        // Spot-check : first chunk, last chunk, and a mid-stream chunk.
        val bytes = data.toByteArray()
        assertArrayEquals(chunk, bytes.copyOfRange(0, 1024))
        assertArrayEquals(chunk, bytes.copyOfRange(512 * 1024, 513 * 1024))
        assertArrayEquals(chunk, bytes.copyOfRange(1023 * 1024, 1024 * 1024))
    }

    @Test
    fun `copyTo drains a snapshot without resetting`() {
        val stream = SkDynamicMemoryWStream()
        stream.write(byteArrayOf(1, 2, 3, 4), 4)
        val dst = ByteArray(4)
        stream.copyTo(dst)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), dst)
        // copyTo does NOT reset.
        assertEquals(4L, stream.bytesWritten())
    }

    @Test
    fun `read at an offset returns a slice`() {
        val stream = SkDynamicMemoryWStream()
        stream.write(ByteArray(10) { it.toByte() }, 10)
        val buf = ByteArray(4)
        val n = stream.read(offset = 3L, buffer = buf, size = 4)
        assertEquals(4, n)
        assertArrayEquals(byteArrayOf(3, 4, 5, 6), buf)
    }

    @Test
    fun `reset clears the buffer`() {
        val stream = SkDynamicMemoryWStream()
        stream.write(byteArrayOf(1, 2, 3), 3)
        stream.reset()
        assertEquals(0L, stream.bytesWritten())
        val data = stream.detachAsData()
        assertEquals(0, data.size)
    }

    @Test
    fun `flush and fSize are aligned with bytesWritten`() {
        val stream = SkDynamicMemoryWStream()
        stream.write(byteArrayOf(1, 2, 3, 4, 5), 5)
        stream.flush()
        assertEquals(5L, stream.fSize())
    }
}
