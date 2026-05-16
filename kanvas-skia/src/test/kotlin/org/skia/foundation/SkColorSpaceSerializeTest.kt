package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn

/**
 * Phase G of MIGRATION_PLAN_COLORSPACE_PORT.md — verify the wire format
 * and round-trip semantics of `serialize` / `deserialize` /
 * `writeToMemory`.
 */
class SkColorSpaceSerializeTest {

    @Test
    fun `serialized size is 68 bytes`() {
        // 4-byte header + 7 TF floats + 9 matrix floats = 4 + 16*4 = 68.
        assertEquals(68, SkColorSpace.SERIALIZED_SIZE)
        assertEquals(68, SkColorSpace.makeSRGB().serialize().size)
    }

    @Test
    fun `header version is 1`() {
        val data = SkColorSpace.makeSRGB().serialize()
        assertEquals(1, data[0].toInt())
        // Reserved bytes must be zero.
        assertEquals(0, data[1].toInt())
        assertEquals(0, data[2].toInt())
        assertEquals(0, data[3].toInt())
    }

    @Test
    fun `writeToMemory with null returns required size`() {
        val cs = SkColorSpace.makeSRGB()
        assertEquals(SkColorSpace.SERIALIZED_SIZE, cs.writeToMemory(null))
    }

    @Test
    fun `writeToMemory rejects undersized buffer`() {
        val cs = SkColorSpace.makeSRGB()
        try {
            cs.writeToMemory(ByteArray(64))
            org.junit.jupiter.api.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {}
    }

    // -----------------------------------------------------------------------
    // Round-trips
    // -----------------------------------------------------------------------

    @Test
    fun `sRGB round-trips through serialize-deserialize as the singleton`() {
        val cs = SkColorSpace.makeSRGB()
        val data = cs.serialize()
        val back = SkColorSpace.deserialize(data)
        assertNotNull(back)
        assertSame(cs, back, "deserialize must snap to the sRGB singleton")
    }

    @Test
    fun `sRGB-linear round-trips as the singleton`() {
        val cs = SkColorSpace.makeSRGBLinear()
        val data = cs.serialize()
        assertSame(cs, SkColorSpace.deserialize(data))
    }

    @Test
    fun `Rec_2020 round-trips as a fresh equivalent instance`() {
        val cs = SkColorSpace.makeRGB(SkNamedTransferFn.kRec2020, SkNamedGamut.kRec2020)!!
        val data = cs.serialize()
        val back = SkColorSpace.deserialize(data)
        assertNotNull(back)
        assertEquals(cs.hash(), back!!.hash(), "hash must round-trip")
        assertTrue(SkColorSpace.equals(cs, back))
    }

    @Test
    fun `colorSpin round-trips`() {
        val original = SkColorSpace.makeSRGB().makeColorSpin()
        val data = original.serialize()
        val back = SkColorSpace.deserialize(data)
        assertNotNull(back)
        assertEquals(original.hash(), back!!.hash())
    }

    // -----------------------------------------------------------------------
    // Error paths
    // -----------------------------------------------------------------------

    @Test
    fun `deserialize rejects buffer too short`() {
        assertNull(SkColorSpace.deserialize(ByteArray(67)))
    }

    @Test
    fun `deserialize rejects unknown version`() {
        val data = SkColorSpace.makeSRGB().serialize()
        data[0] = 99
        assertNull(SkColorSpace.deserialize(data))
    }

    @Test
    fun `deserialize accepts excess bytes after the 68-byte payload`() {
        // SkColorSpace::Deserialize ignores anything past offset 68.
        val cs = SkColorSpace.makeSRGB()
        val padded = cs.serialize() + ByteArray(32)
        assertSame(cs, SkColorSpace.deserialize(padded))
    }

    // -----------------------------------------------------------------------
    // Wire format byte-level check on a known TF (sRGB)
    // -----------------------------------------------------------------------

    @Test
    fun `sRGB serialized bytes match the float bit-pattern of kSRGB`() {
        val data = SkColorSpace.makeSRGB().serialize()
        // First TF float at offset 4 = kSRGB.g = 2.4f
        val gBits = (data[4].toInt() and 0xFF) or
            ((data[5].toInt() and 0xFF) shl 8) or
            ((data[6].toInt() and 0xFF) shl 16) or
            ((data[7].toInt() and 0xFF) shl 24)
        assertEquals(2.4f.toRawBits(), gBits)
    }
}
