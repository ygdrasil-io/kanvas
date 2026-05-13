package org.skia.foundation.stream

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class SkFILEStreamTest {

    @Test
    fun `read returns the file bytes`(@TempDir tmp: Path) {
        val payload = ByteArray(16) { it.toByte() }
        val file = File(tmp.toFile(), "src.bin").apply { writeBytes(payload) }

        val stream = SkFILEStream(file.absolutePath)
        try {
            assertTrue(stream.isValid())
            assertEquals(16L, stream.getLength())
            val buf = ByteArray(16)
            assertEquals(16, stream.read(buf, 16))
            assertArrayEquals(payload, buf)
            assertTrue(stream.isAtEnd())
        } finally {
            stream.close()
        }
    }

    @Test
    fun `seek then read picks up at the requested offset`(@TempDir tmp: Path) {
        val payload = ByteArray(32) { (it + 1).toByte() }
        val file = File(tmp.toFile(), "seek.bin").apply { writeBytes(payload) }

        val stream = SkFILEStream(file.absolutePath)
        try {
            assertTrue(stream.seek(10L))
            assertEquals(10L, stream.getPosition())
            val buf = ByteArray(4)
            assertEquals(4, stream.read(buf, 4))
            assertArrayEquals(payload.copyOfRange(10, 14), buf)
        } finally {
            stream.close()
        }
    }

    @Test
    fun `rewind brings the stream back to the start`(@TempDir tmp: Path) {
        val payload = ByteArray(8) { it.toByte() }
        val file = File(tmp.toFile(), "rewind.bin").apply { writeBytes(payload) }

        val stream = SkFILEStream(file.absolutePath)
        try {
            stream.read(ByteArray(4), 4)
            assertEquals(4L, stream.getPosition())
            assertTrue(stream.rewind())
            assertEquals(0L, stream.getPosition())
            val buf = ByteArray(2)
            stream.read(buf, 2)
            assertArrayEquals(byteArrayOf(0, 1), buf)
        } finally {
            stream.close()
        }
    }

    @Test
    fun `move shifts relative to the current position`(@TempDir tmp: Path) {
        val payload = ByteArray(20) { it.toByte() }
        val file = File(tmp.toFile(), "move.bin").apply { writeBytes(payload) }

        val stream = SkFILEStream(file.absolutePath)
        try {
            stream.read(ByteArray(5), 5)
            assertTrue(stream.move(3L))
            assertEquals(8L, stream.getPosition())
            assertTrue(stream.move(-2L))
            assertEquals(6L, stream.getPosition())
        } finally {
            stream.close()
        }
    }

    @Test
    fun `close marks the stream invalid`(@TempDir tmp: Path) {
        val file = File(tmp.toFile(), "close.bin").apply { writeBytes(ByteArray(4)) }
        val stream = SkFILEStream(file.absolutePath)
        assertTrue(stream.isValid())
        stream.close()
        assertFalse(stream.isValid())
        assertEquals(0, stream.read(ByteArray(4), 4))
    }
}
