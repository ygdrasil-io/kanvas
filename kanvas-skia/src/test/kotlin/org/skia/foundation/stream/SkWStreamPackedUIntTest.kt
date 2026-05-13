package org.skia.foundation.stream

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Verifies the variable-length packed-uint wire format matches
 * upstream Skia exactly (`src/core/SkStream.cpp`,
 * `SkWStream::writePackedUInt`) :
 *
 *  - values 0..0xFD encode as a single byte
 *  - values 0xFE..0xFFFF encode as `0xFE` + little-endian u16
 *  - values above 0xFFFF encode as `0xFF` + little-endian u32
 */
class SkWStreamPackedUIntTest {

    private fun encode(n: Long): ByteArray {
        val sink = SkDynamicMemoryWStream()
        assertTrue(sink.writePackedUInt(n))
        return sink.detachAsData().toByteArray()
    }

    @Test
    fun `zero encodes as one byte`() {
        assertArrayEquals(byteArrayOf(0), encode(0L))
        assertEquals(1, SkWStream.sizeOfPackedUInt(0L))
    }

    @Test
    fun `0xFD encodes as one byte at the boundary`() {
        assertArrayEquals(byteArrayOf(0xFD.toByte()), encode(0xFDL))
        assertEquals(1, SkWStream.sizeOfPackedUInt(0xFDL))
    }

    @Test
    fun `0xFE escapes into the three-byte form`() {
        assertArrayEquals(
            byteArrayOf(0xFE.toByte(), 0xFE.toByte(), 0x00),
            encode(0xFEL),
        )
        assertEquals(3, SkWStream.sizeOfPackedUInt(0xFEL))
    }

    @Test
    fun `0xFFFF still fits in the three-byte form`() {
        assertArrayEquals(
            byteArrayOf(0xFE.toByte(), 0xFF.toByte(), 0xFF.toByte()),
            encode(0xFFFFL),
        )
        assertEquals(3, SkWStream.sizeOfPackedUInt(0xFFFFL))
    }

    @Test
    fun `0x10000 spills into the five-byte form`() {
        assertArrayEquals(
            byteArrayOf(0xFF.toByte(), 0x00, 0x00, 0x01, 0x00),
            encode(0x10000L),
        )
        assertEquals(5, SkWStream.sizeOfPackedUInt(0x10000L))
    }

    @Test
    fun `0xCAFEBABE round-trips through the five-byte form`() {
        assertArrayEquals(
            byteArrayOf(
                0xFF.toByte(),
                0xBE.toByte(), 0xBA.toByte(), 0xFE.toByte(), 0xCA.toByte(),
            ),
            encode(0xCAFEBABEL),
        )
        assertEquals(5, SkWStream.sizeOfPackedUInt(0xCAFEBABEL))
    }

    @Test
    fun `0xFFFFFFFF is the upper bound of the five-byte form`() {
        assertArrayEquals(
            byteArrayOf(
                0xFF.toByte(),
                0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            ),
            encode(0xFFFFFFFFL),
        )
    }

    @Test
    fun `negative values are rejected`() {
        val sink = SkDynamicMemoryWStream()
        assertThrows(IllegalArgumentException::class.java) {
            sink.writePackedUInt(-1L)
        }
    }

    @Test
    fun `values past 32-bit range are rejected`() {
        val sink = SkDynamicMemoryWStream()
        assertThrows(IllegalArgumentException::class.java) {
            sink.writePackedUInt(0x1_0000_0000L)
        }
    }
}
