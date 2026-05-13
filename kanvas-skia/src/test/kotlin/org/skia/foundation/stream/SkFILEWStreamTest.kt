package org.skia.foundation.stream

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class SkFILEWStreamTest {

    @Test
    fun `write flush and bytesWritten reflect what landed on disk`(@TempDir tmp: Path) {
        val file = File(tmp.toFile(), "out.bin")
        val stream = SkFILEWStream(file.absolutePath)
        try {
            assertTrue(stream.isValid())
            assertTrue(stream.write(byteArrayOf(1, 2, 3, 4, 5), 5))
            assertEquals(5L, stream.bytesWritten())
            stream.flush()
        } finally {
            stream.close()
        }
        assertFalse(stream.isValid())
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), file.readBytes())
    }

    @Test
    fun `multiple writes append in order`(@TempDir tmp: Path) {
        val file = File(tmp.toFile(), "append.bin")
        val stream = SkFILEWStream(file.absolutePath)
        try {
            assertTrue(stream.write(byteArrayOf(10, 11), 2))
            assertTrue(stream.write(byteArrayOf(12, 13, 14), 3))
            assertEquals(5L, stream.bytesWritten())
        } finally {
            stream.close()
        }
        assertArrayEquals(byteArrayOf(10, 11, 12, 13, 14), file.readBytes())
    }

    @Test
    fun `fSize reports the file length after close`(@TempDir tmp: Path) {
        val file = File(tmp.toFile(), "size.bin")
        val stream = SkFILEWStream(file.absolutePath)
        stream.write(ByteArray(256), 256)
        stream.close()
        assertEquals(256L, stream.fSize())
        assertEquals(256L, file.length())
    }

    @Test
    fun `helpers writeText and newline emit UTF-8 bytes`(@TempDir tmp: Path) {
        val file = File(tmp.toFile(), "text.bin")
        val stream = SkFILEWStream(file.absolutePath)
        try {
            assertTrue(stream.writeText("hi"))
            assertTrue(stream.newline())
            assertTrue(stream.writeText("bye"))
        } finally {
            stream.close()
        }
        assertEquals("hi\nbye", file.readText(Charsets.UTF_8))
    }

    @Test
    fun `little endian helpers write the correct byte order`(@TempDir tmp: Path) {
        val file = File(tmp.toFile(), "le.bin")
        val stream = SkFILEWStream(file.absolutePath)
        try {
            assertTrue(stream.write8(0x42.toByte()))
            assertTrue(stream.write16(0x1234.toShort()))
            assertTrue(stream.write32(0xCAFEBABE.toInt()))
        } finally {
            stream.close()
        }
        val expected = byteArrayOf(
            0x42,
            // 0x1234 little-endian -> 34 12
            0x34, 0x12,
            // 0xCAFEBABE little-endian -> BE BA FE CA
            0xBE.toByte(), 0xBA.toByte(), 0xFE.toByte(), 0xCA.toByte()
        )
        assertArrayEquals(expected, file.readBytes())
    }
}
