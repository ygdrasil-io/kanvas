package org.graphiks.math

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests focused on the byte-order parity between [SkColor4f.toBytes_RGBA] /
 * [SkColor4f.Companion.FromBytes_RGBA] and Skia upstream
 * (`src/core/SkColor.cpp` `Sk4f_toL32` / `Sk4f_fromL32`).
 *
 * Upstream stores `[R, G, B, A]` as 4 consecutive bytes in memory; read as a
 * little-endian `uint32_t` this is `(A << 24) | (B << 16) | (G << 8) | R`
 * — i.e. R lives in the LSB and A in the MSB.
 */
class SkColor4fTest {

    @Test
    fun `toBytes_RGBA places R in LSB and A in MSB`() {
        // R = 1.0 → byte 0 = 0xFF, all other channels zero.
        val rOnly = SkColor4f(1f, 0f, 0f, 0f).toBytes_RGBA()
        assertEquals(0xFF, rOnly and 0xFF, "R should be byte 0 (LSB)")
        assertEquals(0x00, (rOnly ushr 8) and 0xFF)
        assertEquals(0x00, (rOnly ushr 16) and 0xFF)
        assertEquals(0x00, (rOnly ushr 24) and 0xFF)

        // G = 1.0 → byte 1.
        val gOnly = SkColor4f(0f, 1f, 0f, 0f).toBytes_RGBA()
        assertEquals(0x00, gOnly and 0xFF)
        assertEquals(0xFF, (gOnly ushr 8) and 0xFF, "G should be byte 1")
        assertEquals(0x00, (gOnly ushr 16) and 0xFF)
        assertEquals(0x00, (gOnly ushr 24) and 0xFF)

        // B = 1.0 → byte 2.
        val bOnly = SkColor4f(0f, 0f, 1f, 0f).toBytes_RGBA()
        assertEquals(0x00, bOnly and 0xFF)
        assertEquals(0x00, (bOnly ushr 8) and 0xFF)
        assertEquals(0xFF, (bOnly ushr 16) and 0xFF, "B should be byte 2")
        assertEquals(0x00, (bOnly ushr 24) and 0xFF)

        // A = 1.0 → byte 3 (MSB).
        val aOnly = SkColor4f(0f, 0f, 0f, 1f).toBytes_RGBA()
        assertEquals(0x00, aOnly and 0xFF)
        assertEquals(0x00, (aOnly ushr 8) and 0xFF)
        assertEquals(0x00, (aOnly ushr 16) and 0xFF)
        assertEquals(0xFF, (aOnly ushr 24) and 0xFF, "A should be byte 3 (MSB)")
    }

    @Test
    fun `toBytes_RGBA exact bit layout for a non-symmetric colour`() {
        // R=0.5 ⇒ 0x80, G=0.25 ⇒ 0x40, B=0.125 ⇒ 0x20, A=1 ⇒ 0xFF.
        // Bytes [R, G, B, A] = [0x80, 0x40, 0x20, 0xFF] read as little-endian
        // uint32 ⇒ 0xFF204080.
        val packed = SkColor4f(0.5f, 0.25f, 0.125f, 1f).toBytes_RGBA()
        assertEquals(0xFF204080.toInt(), packed)
    }

    @Test
    fun `FromBytes_RGBA reads bytes in R G B A order`() {
        // bytes [0x11, 0x22, 0x33, 0x44] little-endian = 0x44332211.
        val c = SkColor4f.FromBytes_RGBA(0x44332211)
        assertEquals(0x11 / 255f, c.fR, 1e-6f)
        assertEquals(0x22 / 255f, c.fG, 1e-6f)
        assertEquals(0x33 / 255f, c.fB, 1e-6f)
        assertEquals(0x44 / 255f, c.fA, 1e-6f)
    }

    @Test
    fun `roundtrip toBytes FromBytes preserves channels within 1 ulp_8`() {
        val samples = listOf(
            SkColor4f(0f, 0f, 0f, 0f),
            SkColor4f(1f, 1f, 1f, 1f),
            SkColor4f(0.25f, 0.5f, 0.75f, 1f),
            SkColor4f(0.5f, 0.25f, 0.125f, 1f),
            SkColor4f(1f, 0f, 0f, 1f),
            SkColor4f(0f, 1f, 0f, 1f),
            SkColor4f(0f, 0f, 1f, 1f),
            SkColor4f(0.123f, 0.456f, 0.789f, 0.5f),
        )
        for (c in samples) {
            val round = SkColor4f.FromBytes_RGBA(c.toBytes_RGBA())
            assertEquals(c.fR, round.fR, 1f / 255f, "fR for $c")
            assertEquals(c.fG, round.fG, 1f / 255f, "fG for $c")
            assertEquals(c.fB, round.fB, 1f / 255f, "fB for $c")
            assertEquals(c.fA, round.fA, 1f / 255f, "fA for $c")
        }
    }

    @Test
    fun `roundtrip FromBytes toBytes preserves the raw uint32`() {
        // Integer roundtrip — clamped 8-bit values must survive identically.
        val packedSamples = intArrayOf(
            0x00000000,
            0xFFFFFFFF.toInt(),
            0xFF0000FF.toInt(), // bytes [FF, 00, 00, FF] — opaque red upstream
            0xFF00FF00.toInt(), // bytes [00, FF, 00, FF] — opaque green upstream
            0xFFFF0000.toInt(), // bytes [00, 00, FF, FF] — opaque blue upstream
            0xDEADBEEF.toInt(),
            0x12345678,
        )
        for (p in packedSamples) {
            val back = SkColor4f.FromBytes_RGBA(p).toBytes_RGBA()
            assertEquals(p, back, "raw uint32 0x${Integer.toHexString(p)}")
        }
    }
}
