package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn
import java.nio.ByteBuffer

/**
 * R-suivi.21 verification suite for [SkICC].
 *
 * Pins the public contract : the header layout, the sentinel `acsp`
 * signature, the "parsing returns null" stub, and (since R-suivi.21)
 * the tag-table emission. See [SkICCWriteToICCTagTableTest] for the
 * deep parse of the tag-table itself.
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
    fun `WriteToICC emits at least the v4 header plus a tag table`() {
        val bytes = SkICC.WriteToICC(SkNamedTransferFn.kSRGB, SkNamedGamut.kSRGB)
        // Header (128) + tag count (4) + at least one tag table entry (12).
        assert(bytes.size >= SkICC.HEADER_SIZE + 4 + SkICC.TAG_TABLE_ENTRY_SIZE) {
            "expected header + tag table; got ${bytes.size} bytes"
        }
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
        // The size field must match the actual byte count.
        assertEquals(bytes.size, size)
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
