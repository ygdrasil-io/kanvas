package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.skia.skcms.SkNamedGamut
import org.skia.skcms.SkNamedTransferFn

/**
 * Phase H of MIGRATION_PLAN_COLORSPACE_PORT.md — verify the `SkColorSpace`
 * hash fields (`transferFnHash`, `toXYZD50Hash`) match what upstream Skia
 * (`SkColorSpace.cpp:132-133`) computes from the same input bytes.
 *
 * Ground truth from the standalone wyhash driver (`tools/wyhash_test.cpp`).
 */
class SkColorSpaceHashTest {

    @Test
    fun `sRGB transferFnHash matches upstream Skia`() {
        // SkChecksum::Hash32(&fTransferFn, 7*sizeof(float)) on the sRGB TF.
        assertEquals(0x105632bd, SkColorSpace.makeSRGB().transferFnHash)
    }

    @Test
    fun `sRGB toXYZD50Hash matches upstream Skia`() {
        assertEquals(0x7910144c, SkColorSpace.makeSRGB().toXYZD50Hash)
    }

    @Test
    fun `sRGB-linear transferFnHash matches upstream Skia`() {
        // Linear TF has the same gamut as sRGB, so it shares toXYZD50Hash.
        assertEquals(0x70e19594, SkColorSpace.makeSRGBLinear().transferFnHash)
        assertEquals(0x7910144c, SkColorSpace.makeSRGBLinear().toXYZD50Hash)
    }

    @Test
    fun `Rec_2020 hashes match upstream Skia`() {
        val rec2020 = SkColorSpace.makeRGB(
            SkNamedTransferFn.kRec2020,
            SkNamedGamut.kRec2020,
        )!!
        assertEquals(0xef6bae87.toInt(), rec2020.transferFnHash)
        assertEquals(0x9ebacc71.toInt(), rec2020.toXYZD50Hash)
    }

    @Test
    fun `singleton equals stays stable across the rewire`() {
        // Phase H rewrites the hash, but Equals must keep working.
        assertEquals(true, SkColorSpace.equals(SkColorSpace.makeSRGB(), SkColorSpace.makeSRGB()))
        assertEquals(true, SkColorSpace.equals(
            SkColorSpace.makeSRGBLinear(), SkColorSpace.makeSRGBLinear()))
        assertEquals(false, SkColorSpace.equals(
            SkColorSpace.makeSRGB(), SkColorSpace.makeSRGBLinear()))
    }

    @Test
    fun `Rec_2020 hash differs from sRGB`() {
        val rec2020 = SkColorSpace.makeRGB(
            SkNamedTransferFn.kRec2020,
            SkNamedGamut.kRec2020,
        )!!
        val srgb = SkColorSpace.makeSRGB()
        assertNotEquals(srgb.transferFnHash, rec2020.transferFnHash)
        assertNotEquals(srgb.toXYZD50Hash, rec2020.toXYZD50Hash)
    }
}
