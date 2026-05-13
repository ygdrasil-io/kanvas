package org.skia.foundation.stream

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * R-suivi.29 — `peek`, `fork`, and `duplicate` on [SkMemoryStream] and
 * [SkStream] base behaviour.
 */
class SkMemoryStreamPeekTest {

    @Test
    fun `peek returns requested bytes without advancing the cursor`() {
        val data = ByteArray(10) { it.toByte() }
        val stream = SkMemoryStream(data)
        // Position must stay at 0 after a peek.
        val peekBuffer = ByteArray(4)
        val peeked = stream.peek(peekBuffer, 4)
        assertEquals(4, peeked)
        assertArrayEquals(byteArrayOf(0, 1, 2, 3), peekBuffer)
        assertEquals(0L, stream.getPosition(), "peek must NOT advance cursor")

        // A subsequent read returns the same bytes.
        val readBuffer = ByteArray(4)
        val read = stream.read(readBuffer, 4)
        assertEquals(4, read)
        assertArrayEquals(peekBuffer, readBuffer)
        assertEquals(4L, stream.getPosition(), "read DOES advance cursor")
    }

    @Test
    fun `peek clamps at end of stream and still preserves cursor`() {
        val data = ByteArray(3) { it.toByte() }
        val stream = SkMemoryStream(data)
        // Move to position 1 first.
        stream.seek(1L)
        val buf = ByteArray(8)
        val n = stream.peek(buf, 8)
        // Only two bytes available from offset 1.
        assertEquals(2, n)
        assertEquals(1L, stream.getPosition(), "peek must preserve cursor at offset 1")
    }

    @Test
    fun `peek zero or negative size returns zero`() {
        val stream = SkMemoryStream(ByteArray(4))
        val buf = ByteArray(8)
        assertEquals(0, stream.peek(buf, 0))
        assertEquals(0, stream.peek(buf, -1))
        assertEquals(0L, stream.getPosition())
    }

    @Test
    fun `fork returns a fresh stream sharing the buffer with same position`() {
        val data = ByteArray(8) { it.toByte() }
        val orig = SkMemoryStream(data)
        orig.seek(3L)
        val forked = orig.fork()
        // Distinct stream instance...
        assertNotSame(orig, forked)
        // ... but the backing buffer is shared (same reference).
        assertSame(data, forked.getMemoryBase())
        // Position matches the original's.
        assertEquals(3L, forked.getPosition())
        // Mutating the fork's position doesn't affect the original.
        forked.seek(0L)
        assertEquals(3L, orig.getPosition(), "orig position unchanged by fork seek")
        assertEquals(0L, forked.getPosition())
    }

    @Test
    fun `duplicate returns a fresh stream at position zero over the same buffer`() {
        val data = ByteArray(8) { (it + 100).toByte() }
        val orig = SkMemoryStream(data)
        orig.seek(5L)
        val dup = orig.duplicate()
        assertNotSame(orig, dup)
        // duplicate rewinds the copy to 0.
        assertEquals(0L, dup.getPosition())
        // Same backing buffer.
        assertSame(data, dup.getMemoryBase())
        // The original is untouched.
        assertEquals(5L, orig.getPosition())
    }

    @Test
    fun `base class peek returns zero for streams that are not seekable`() {
        // A custom forward-only stream that only implements the
        // minimum surface — peek should fall through to 0.
        val fakeStream = object : SkStream() {
            override fun read(buffer: ByteArray, size: Int): Int = 0
            override fun isAtEnd(): Boolean = true
            override fun hasLength(): Boolean = false
            override fun getLength(): Long = 0L
            override fun hasPosition(): Boolean = false
            override fun getPosition(): Long = 0L
            override fun rewind(): Boolean = false
        }
        val buf = ByteArray(4)
        assertEquals(0, fakeStream.peek(buf, 4))
    }
}
