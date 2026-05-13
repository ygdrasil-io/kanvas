package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.skcms.SkNamedGamut

/**
 * R2.19 — covers the PascalCase static factories on
 * [SkColorSpace] : `MakeSRGB`, `MakeSRGBLinear`, `MakeRGB`.
 *
 * The lowercase `makeSRGB` / `makeSRGBLinear` / `makeRGB` companion
 * methods already have coverage in [SkColorSpaceTest] ; this file
 * pins down the bit-equality contract of the new PascalCase names
 * and validates the [SkColorSpaceTransferFn] / [SkColorSpacePrimaries]
 * round-trip path.
 */
class SkColorSpaceFactoriesTest {

    // ─── MakeSRGB / MakeSRGBLinear ────────────────────────────────

    @Test
    fun `MakeSRGB returns the same singleton as makeSRGB`() {
        assertSame(SkColorSpace.makeSRGB(), SkColorSpace.MakeSRGB())
    }

    @Test
    fun `MakeSRGB hash-equals makeSRGB`() {
        assertEquals(SkColorSpace.makeSRGB().hash(), SkColorSpace.MakeSRGB().hash())
        assertTrue(SkColorSpace.equals(SkColorSpace.MakeSRGB(), SkColorSpace.makeSRGB()))
    }

    @Test
    fun `MakeSRGB is identified as sRGB`() {
        assertTrue(SkColorSpace.MakeSRGB().isSRGB())
        assertTrue(SkColorSpace.MakeSRGB().gammaCloseToSRGB())
    }

    @Test
    fun `MakeSRGBLinear returns the same singleton as makeSRGBLinear`() {
        assertSame(SkColorSpace.makeSRGBLinear(), SkColorSpace.MakeSRGBLinear())
    }

    @Test
    fun `MakeSRGBLinear has linear gamma`() {
        assertTrue(SkColorSpace.MakeSRGBLinear().gammaIsLinear())
    }

    @Test
    fun `MakeSRGBLinear is not equal to MakeSRGB`() {
        assertNotSame(SkColorSpace.MakeSRGB(), SkColorSpace.MakeSRGBLinear())
        assertNotEquals(SkColorSpace.MakeSRGB().hash(), SkColorSpace.MakeSRGBLinear().hash())
    }

    // ─── MakeRGB(transferFn, primaries) ───────────────────────────

    @Test
    fun `MakeRGB with kSRGB transferFn and Rec709 primaries hash-equals MakeSRGB`() {
        val cs = SkColorSpace.MakeRGB(
            SkColorSpaceTransferFn.kSRGB,
            SkColorSpacePrimaries(
                fRX = 0.64f, fRY = 0.33f,
                fGX = 0.3f, fGY = 0.6f,
                fBX = 0.15f, fBY = 0.06f,
                fWX = 0.3127f, fWY = 0.329f,
            ),
        )
        assertNotNull(cs)
        // makeRGB snaps quasi-sRGBish inputs back to the sRGB singleton.
        assertSame(SkColorSpace.MakeSRGB(), cs)
    }

    @Test
    fun `MakeRGB produces a non-null colorspace for valid arbitrary primaries`() {
        // ProPhotoRGB-ish primaries — far from sRGB; should NOT snap to
        // the sRGB singleton but still produce a valid colorspace.
        val cs = SkColorSpace.MakeRGB(
            SkColorSpaceTransferFn(g = 1.8f, a = 1f, b = 0f, c = 0f, d = 0f, e = 0f, f = 0f),
            SkColorSpacePrimaries(
                fRX = 0.7347f, fRY = 0.2653f,
                fGX = 0.1596f, fGY = 0.8404f,
                fBX = 0.0366f, fBY = 0.0001f,
                fWX = 0.34567f, fWY = 0.35850f,
            ),
        )
        assertNotNull(cs)
        assertNotSame(SkColorSpace.MakeSRGB(), cs)
    }

    @Test
    fun `MakeRGB returns null for degenerate primaries`() {
        // All-zero primaries — singular toXYZD50 matrix.
        val cs = SkColorSpace.MakeRGB(
            SkColorSpaceTransferFn.kSRGB,
            SkColorSpacePrimaries(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        )
        assertNull(cs)
    }

    @Test
    fun `MakeRGB with skcms matrix delegates to makeRGB`() {
        val cs = SkColorSpace.MakeRGB(SkColorSpaceTransferFn.kSRGB, SkNamedGamut.kSRGB)
        assertNotNull(cs)
        assertSame(SkColorSpace.MakeSRGB(), cs)
    }

    // ─── SkColorSpaceTransferFn data class ────────────────────────

    @Test
    fun `SkColorSpaceTransferFn kSRGB has the canonical 7-tuple`() {
        val tf = SkColorSpaceTransferFn.kSRGB
        assertEquals(2.4f, tf.g)
        assertEquals(1f / 1.055f, tf.a)
        assertEquals(0.055f / 1.055f, tf.b)
        assertEquals(1f / 12.92f, tf.c)
        assertEquals(0.04045f, tf.d)
        assertEquals(0f, tf.e)
        assertEquals(0f, tf.f)
    }

    @Test
    fun `SkColorSpaceTransferFn round-trips through SkcmsTransferFunction`() {
        val orig = SkColorSpaceTransferFn.kSRGB
        val skcms = orig.toSkcms()
        val back = SkColorSpaceTransferFn.fromSkcms(skcms)
        assertEquals(orig, back)
    }

    private fun assertNotEquals(unexpected: Long, actual: Long) {
        if (unexpected == actual) {
            throw AssertionError("Expected values to differ but both = $unexpected")
        }
    }
}
