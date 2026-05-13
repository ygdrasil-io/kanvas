package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.skia.skcms.SkNamedGamut
import org.skia.skcms.SkNamedTransferFn
import java.nio.ByteBuffer

/**
 * R2.16 verification suite for [SkICC].
 *
 * The surface is a stub today — these tests pin the contract that
 * a future ICC body port must keep honouring : the header layout,
 * the sentinel `acsp` signature, and the "parsing returns null"
 * contract.
 */
class SkICCTest {

    @Test
    fun `Make returns null — parsing not implemented`() {
        val empty = ByteBuffer.allocate(0)
        assertNull(SkICC.Make(empty, 0L))
    }

    @Test
    fun `Make returns null even on plausible-looking bytes`() {
        val plausible = ByteBuffer.allocate(128)
        // Plant the 'acsp' signature at the right offset — the stub
        // still must return null, because parsing isn't implemented.
        plausible.put(36, 'a'.code.toByte())
        plausible.put(37, 'c'.code.toByte())
        plausible.put(38, 's'.code.toByte())
        plausible.put(39, 'p'.code.toByte())
        assertNull(SkICC.Make(plausible, 128L))
    }

    @Test
    fun `WriteToICC returns the canonical 128-byte ICC v4 header`() {
        val bytes = SkICC.WriteToICC(SkNamedTransferFn.kSRGB, SkNamedGamut.kSRGB)
        assertEquals(SkICC.HEADER_SIZE, bytes.size, "stub must emit exactly the v4 header")
    }

    @Test
    fun `WriteToICC plants the acsp signature at offset 36`() {
        val bytes = SkICC.WriteToICC(SkNamedTransferFn.kSRGB, SkNamedGamut.kSRGB)
        assertEquals('a'.code.toByte(), bytes[36])
        assertEquals('c'.code.toByte(), bytes[37])
        assertEquals('s'.code.toByte(), bytes[38])
        assertEquals('p'.code.toByte(), bytes[39])
    }

    @Test
    fun `WriteToICC writes the profile size at offset 0 — big-endian uint32`() {
        val bytes = SkICC.WriteToICC(SkNamedTransferFn.kSRGB, SkNamedGamut.kSRGB)
        val size =
            (bytes[0].toInt() and 0xFF shl 24) or
                (bytes[1].toInt() and 0xFF shl 16) or
                (bytes[2].toInt() and 0xFF shl 8) or
                (bytes[3].toInt() and 0xFF)
        assertEquals(SkICC.HEADER_SIZE, size)
    }

    @Test
    fun `WriteToICC writes the v4_3 profile version at offset 8`() {
        val bytes = SkICC.WriteToICC(SkNamedTransferFn.kSRGB, SkNamedGamut.kSRGB)
        assertEquals(0x04.toByte(), bytes[8])
        assertEquals(0x30.toByte(), bytes[9])
        assertEquals(0x00.toByte(), bytes[10])
        assertEquals(0x00.toByte(), bytes[11])
    }

    @Test
    fun `WriteToICC stamps mntr RGB XYZ profile descriptors`() {
        val bytes = SkICC.WriteToICC(SkNamedTransferFn.kSRGB, SkNamedGamut.kSRGB)
        // Profile/device class.
        assertEquals("mntr", String(bytes, 12, 4))
        // Data colour space.
        assertEquals("RGB ", String(bytes, 16, 4))
        // PCS — XYZ for display profiles.
        assertEquals("XYZ ", String(bytes, 20, 4))
    }

    @Test
    fun `WriteToICC accepts any transfer function and gamut without throwing`() {
        // Stub accepts every well-formed input.
        SkICC.WriteToICC(SkNamedTransferFn.kLinear, SkNamedGamut.kRec2020)
        SkICC.WriteToICC(SkNamedTransferFn.k2Dot2, SkNamedGamut.kAdobeRGB)
        SkICC.WriteToICC(SkNamedTransferFn.kRec709, SkNamedGamut.kDisplayP3)
    }
}
