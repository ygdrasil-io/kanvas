package org.skia.foundation.stream

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SkMemoryStreamTest {

    @Test
    fun `read returns the requested bytes in order`() {
        val data = ByteArray(10) { it.toByte() }
        val stream = SkMemoryStream(data)
        val buffer = ByteArray(4)
        assertEquals(4, stream.read(buffer, 4))
        assertArrayEquals(byteArrayOf(0, 1, 2, 3), buffer)
        assertEquals(4L, stream.getPosition())
    }

    @Test
    fun `read clamps at end of stream`() {
        val data = ByteArray(3) { it.toByte() }
        val stream = SkMemoryStream(data)
        val buffer = ByteArray(8)
        assertEquals(3, stream.read(buffer, 8))
        assertTrue(stream.isAtEnd())
        assertEquals(0, stream.read(buffer, 4))
    }

    @Test
    fun `rewind resets the position`() {
        val data = ByteArray(8) { it.toByte() }
        val stream = SkMemoryStream(data)
        stream.read(ByteArray(4), 4)
        assertEquals(4L, stream.getPosition())
        assertTrue(stream.rewind())
        assertEquals(0L, stream.getPosition())
        val buffer = ByteArray(2)
        stream.read(buffer, 2)
        assertArrayEquals(byteArrayOf(0, 1), buffer)
    }

    @Test
    fun `seek to an absolute position then read`() {
        val data = ByteArray(8) { it.toByte() }
        val stream = SkMemoryStream(data)
        assertTrue(stream.seek(5L))
        assertEquals(5L, stream.getPosition())
        val buffer = ByteArray(3)
        assertEquals(3, stream.read(buffer, 3))
        assertArrayEquals(byteArrayOf(5, 6, 7), buffer)
    }

    @Test
    fun `seek clamps past end of stream`() {
        val data = ByteArray(4) { it.toByte() }
        val stream = SkMemoryStream(data)
        assertTrue(stream.seek(100L))
        assertEquals(4L, stream.getPosition())
        assertTrue(stream.isAtEnd())
    }

    @Test
    fun `move shifts the position relative to current`() {
        val data = ByteArray(10) { it.toByte() }
        val stream = SkMemoryStream(data)
        stream.read(ByteArray(3), 3)
        assertTrue(stream.move(2L))
        assertEquals(5L, stream.getPosition())
        assertTrue(stream.move(-4L))
        assertEquals(1L, stream.getPosition())
        // negative move clamps to 0
        assertTrue(stream.move(-100L))
        assertEquals(0L, stream.getPosition())
    }

    @Test
    fun `skip advances without populating a buffer`() {
        val data = ByteArray(10) { it.toByte() }
        val stream = SkMemoryStream(data)
        assertEquals(4L, stream.skip(4L))
        val buffer = ByteArray(2)
        stream.read(buffer, 2)
        assertArrayEquals(byteArrayOf(4, 5), buffer)
    }

    @Test
    fun `hasLength and getLength report the array size`() {
        val data = ByteArray(42)
        val stream = SkMemoryStream(data)
        assertTrue(stream.hasLength())
        assertEquals(42L, stream.getLength())
    }

    @Test
    fun `getMemoryBase returns the backing array`() {
        val data = ByteArray(5) { (it + 1).toByte() }
        val stream = SkMemoryStream(data)
        assertSame(data, stream.getMemoryBase())
    }

    @Test
    fun `fork preserves the current position`() {
        val data = ByteArray(10) { it.toByte() }
        val stream = SkMemoryStream(data)
        stream.read(ByteArray(3), 3)
        val forked = stream.fork()
        assertEquals(3L, forked.getPosition())
        val buf = ByteArray(2)
        forked.read(buf, 2)
        assertArrayEquals(byteArrayOf(3, 4), buf)
        // Original is unaffected by the fork's read.
        assertEquals(3L, stream.getPosition())
    }

    @Test
    fun `empty stream is at end immediately`() {
        val stream = SkMemoryStream(ByteArray(0))
        assertTrue(stream.isAtEnd())
        assertEquals(0L, stream.getLength())
        assertFalse(stream.hasPosition().not()) // hasPosition is true
        assertEquals(0L, stream.getPosition())
    }
}
