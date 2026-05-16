package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn
import org.skia.foundation.skcms.SkcmsTransferFunction

class SkColorSpaceTest {

    @Test
    fun `MakeSRGB returns the same singleton`() {
        assertSame(SkColorSpace.makeSRGB(), SkColorSpace.makeSRGB())
    }

    @Test
    fun `MakeSRGBLinear returns the same singleton`() {
        assertSame(SkColorSpace.makeSRGBLinear(), SkColorSpace.makeSRGBLinear())
    }

    @Test
    fun `MakeSRGB is sRGB but MakeSRGBLinear is not`() {
        assertTrue(SkColorSpace.makeSRGB().isSRGB())
        assertFalse(SkColorSpace.makeSRGBLinear().isSRGB())
    }

    @Test
    fun `MakeSRGBLinear has linear gamma but MakeSRGB does not`() {
        assertTrue(SkColorSpace.makeSRGBLinear().gammaIsLinear())
        assertFalse(SkColorSpace.makeSRGB().gammaIsLinear())
        assertTrue(SkColorSpace.makeSRGB().gammaCloseToSRGB())
    }

    @Test
    fun `MakeRGB(kSRGB, kSRGB-gamut) snaps to the sRGB singleton`() {
        val cs = SkColorSpace.makeRGB(SkNamedTransferFn.kSRGB, SkNamedGamut.kSRGB)
        assertSame(SkColorSpace.makeSRGB(), cs)
    }

    @Test
    fun `MakeRGB with Rec_2020 gamut produces a fresh instance`() {
        val cs1 = SkColorSpace.makeRGB(SkNamedTransferFn.kRec2020, SkNamedGamut.kRec2020)!!
        val cs2 = SkColorSpace.makeRGB(SkNamedTransferFn.kRec2020, SkNamedGamut.kRec2020)!!
        // Same hash, but not the same instance — that's fine.
        assertEquals(cs1.hash(), cs2.hash())
        assertTrue(SkColorSpace.equals(cs1, cs2))
    }

    @Test
    fun `MakeRGB with invalid TF returns null`() {
        val invalid = SkcmsTransferFunction(g = -1f, a = 0f, b = 0f, c = 0f, d = 0f, e = 0f, f = 0f)
        assertNull(SkColorSpace.makeRGB(invalid, SkNamedGamut.kSRGB))
    }

    @Test
    fun `Equals null-pair is true and null-with-non-null is false`() {
        assertTrue(SkColorSpace.equals(null, null))
        assertFalse(SkColorSpace.equals(null, SkColorSpace.makeSRGB()))
        assertFalse(SkColorSpace.equals(SkColorSpace.makeSRGB(), null))
    }

    @Test
    fun `sRGB and Rec_2020 differ`() {
        val rec = SkColorSpace.makeRGB(SkNamedTransferFn.kRec2020, SkNamedGamut.kRec2020)!!
        assertFalse(SkColorSpace.equals(SkColorSpace.makeSRGB(), rec))
    }

    @Test
    fun `Lazy inverse fields are computed and cached`() {
        val rec = SkColorSpace.makeRGB(SkNamedTransferFn.kRec2020, SkNamedGamut.kRec2020)!!
        val invMat = rec.fromXYZD50
        val invTf = rec.invTransferFn
        assertNotNull(invMat)
        assertNotNull(invTf)
        // Second access returns same objects (lazy caching).
        assertSame(invMat, rec.fromXYZD50)
        assertSame(invTf, rec.invTransferFn)
    }
}
