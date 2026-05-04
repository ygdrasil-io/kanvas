package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * Phase H of MIGRATION_PLAN_COLORSPACE_PORT.md — `SkChecksum.hash32` is
 * bit-compatible with upstream Skia
 * ([src/core/SkChecksum.cpp](file:///Users/chaos/workspace/kanvas-forge/skia-main/src/core/SkChecksum.cpp)).
 *
 * Ground-truth values were produced by compiling the upstream wyhash code
 * (`tools/wyhash_test.cpp` in the worktree) with `clang++ -O2`. Any change
 * to the wyhash secrets, byte order, or branch structure would break these
 * assertions.
 */
class SkChecksumTest {

    @Test
    fun `empty input matches upstream`() {
        assertEquals(0xe2bde459.toInt(), SkChecksum.hash32(ByteArray(0)))
    }

    @Test
    fun `len-1 input takes the wyr3 branch`() {
        assertEquals(0x3263626f.toInt(), SkChecksum.hash32(byteArrayOf(0x42)))
    }

    @Test
    fun `len-2 input still takes the wyr3 branch`() {
        assertEquals(0xbd69a8b2.toInt(),
            SkChecksum.hash32(byteArrayOf(0x42, 0x43)))
    }

    @Test
    fun `len-3 input edge of the wyr3 branch`() {
        assertEquals(0x07f2b4b3,
            SkChecksum.hash32(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `len-4 enters the wyr4 branch`() {
        assertEquals(0xf2edd464.toInt(),
            SkChecksum.hash32(byteArrayOf(1, 2, 3, 4)))
    }

    @Test
    fun `len-11 ascii hello world`() {
        val data = "hello world".toByteArray(Charsets.US_ASCII)
        assertEquals(0x1c3b2573, SkChecksum.hash32(data))
    }

    @Test
    fun `len-64 enters the deeper main loop`() {
        val data = ByteArray(64) { it.toByte() }
        assertEquals(0x962d6746.toInt() , SkChecksum.hash32(data))
    }

    @Test
    fun `seed is honored`() {
        val data = byteArrayOf(1, 2, 3, 4)
        val h0 = SkChecksum.hash32(data, seed = 0)
        val h1 = SkChecksum.hash32(data, seed = 1)
        assertNotEquals(h0, h1)
    }

    @Test
    fun `length is honored independently from buffer size`() {
        val data = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val h4 = SkChecksum.hash32(data, length = 4)
        val firstFour = byteArrayOf(1, 2, 3, 4)
        assertEquals(SkChecksum.hash32(firstFour), h4)
    }

    @Test
    fun `hash is stable across calls`() {
        val data = "abcdefghijklmnopqrstuvwxyz".toByteArray(Charsets.US_ASCII)
        val first = SkChecksum.hash32(data)
        val second = SkChecksum.hash32(data)
        val third = SkChecksum.hash32(data)
        assertEquals(first, second)
        assertEquals(first, third)
    }

    @Test
    fun `hash64 matches hash32 in low 32 bits`() {
        val data = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val h64 = SkChecksum.hash64(data)
        val h32 = SkChecksum.hash32(data)
        assertEquals(h32, h64.toInt())
    }
}
