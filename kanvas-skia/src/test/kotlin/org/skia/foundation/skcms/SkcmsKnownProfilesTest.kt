package org.skia.foundation.skcms
import org.skia.math.SkcmsMatrix3x3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkColorSpace

/**
 * Phase F6 of MIGRATION_PLAN_COLORSPACE_PORT.md — verify the well-known
 * profile builders and `skcmsApproximatelyEqualProfiles`.
 */
class SkcmsKnownProfilesTest {

    @Test
    fun `skcmsSrgbProfile is shaped like a usable RGB ICC profile`() {
        val p = skcmsSrgbProfile
        assertEquals(SkcmsSignature.RGB.value, p.dataColorSpace)
        assertEquals(SkcmsSignature.XYZ.value, p.pcs)
        assertTrue(p.hasTrc)
        assertTrue(p.hasToXYZD50)
        // 3 identical sRGB parametric TRCs.
        for (i in 0 until 3) {
            val curve = p.trc[i] as SkcmsCurve.Parametric
            assertSame(SkNamedTransferFn.kSRGB, curve.parametric)
        }
        assertSame(SkNamedGamut.kSRGB, p.toXYZD50)
    }

    @Test
    fun `skcmsXyzd50Profile is shaped like the identity profile`() {
        val p = skcmsXyzd50Profile
        assertEquals(SkcmsSignature.RGB.value, p.dataColorSpace)
        assertTrue(p.hasTrc)
        assertTrue(p.hasToXYZD50)
        for (i in 0 until 3) {
            val curve = p.trc[i] as SkcmsCurve.Parametric
            assertSame(SkNamedTransferFn.kLinear, curve.parametric)
        }
        assertSame(SkcmsMatrix3x3.IDENTITY, p.toXYZD50)
    }

    @Test
    fun `skcmsSrgbProfile builds the sRGB SkColorSpace singleton via make`() {
        // Round-trip the known sRGB profile through SkColorSpace.make ⇒
        // we should land back on the canonical singleton thanks to the
        // Phase B snap.
        val cs = SkColorSpace.make(skcmsSrgbProfile)
        assertNotNull(cs)
        assertSame(SkColorSpace.makeSRGB(), cs)
    }

    @Test
    fun `skcmsXyzd50Profile builds an sRGB-linear SkColorSpace via make`() {
        // Linear TRCs + sRGB-gamut would snap to sRGB-linear, but XYZ
        // identity gamut stays as a distinct fresh instance.
        val cs = SkColorSpace.make(skcmsXyzd50Profile)
        assertNotNull(cs)
        assertSame(SkNamedTransferFn.kLinear, cs!!.transferFn)
    }

    @Test
    fun `skcmsSrgbTransferFunction returns kSRGB`() {
        assertSame(SkNamedTransferFn.kSRGB, skcmsSrgbTransferFunction())
    }

    // ----- skcmsApproximatelyEqualProfiles -----

    @Test
    fun `approximate-equal of identical profile is true`() {
        assertTrue(skcmsApproximatelyEqualProfiles(skcmsSrgbProfile, skcmsSrgbProfile))
    }

    @Test
    fun `approximate-equal of sRGB and a structurally equivalent rebuild is true`() {
        // Same TF / same gamut, but distinct profile object.
        val rebuilt = SkcmsICCProfile(
            dataColorSpace = SkcmsSignature.RGB.value,
            pcs = SkcmsSignature.XYZ.value,
            trc = arrayOf(
                SkcmsCurve.Parametric(SkNamedTransferFn.kSRGB),
                SkcmsCurve.Parametric(SkNamedTransferFn.kSRGB),
                SkcmsCurve.Parametric(SkNamedTransferFn.kSRGB),
            ),
            toXYZD50 = SkNamedGamut.kSRGB,
            hasTrc = true,
            hasToXYZD50 = true,
        )
        assertTrue(skcmsApproximatelyEqualProfiles(skcmsSrgbProfile, rebuilt))
        assertTrue(skcmsApproximatelyEqualProfiles(rebuilt, skcmsSrgbProfile))
    }

    @Test
    fun `approximate-equal absorbs sub-tolerance TRC noise`() {
        // sRGB + perturb each TF parameter by 0.0005 (< the 0.001 tolerance).
        val noisyTf = SkNamedTransferFn.kSRGB.copy(
            g = SkNamedTransferFn.kSRGB.g + 0.0005f,
            a = SkNamedTransferFn.kSRGB.a + 0.0005f,
        )
        val noisy = SkcmsICCProfile(
            dataColorSpace = SkcmsSignature.RGB.value,
            pcs = SkcmsSignature.XYZ.value,
            trc = arrayOf(
                SkcmsCurve.Parametric(noisyTf),
                SkcmsCurve.Parametric(noisyTf),
                SkcmsCurve.Parametric(noisyTf),
            ),
            toXYZD50 = SkNamedGamut.kSRGB,
            hasTrc = true,
            hasToXYZD50 = true,
        )
        assertTrue(skcmsApproximatelyEqualProfiles(skcmsSrgbProfile, noisy))
    }

    @Test
    fun `approximate-equal rejects different gamuts`() {
        val rec2020Profile = SkcmsICCProfile(
            dataColorSpace = SkcmsSignature.RGB.value,
            pcs = SkcmsSignature.XYZ.value,
            trc = arrayOf(
                SkcmsCurve.Parametric(SkNamedTransferFn.kSRGB),
                SkcmsCurve.Parametric(SkNamedTransferFn.kSRGB),
                SkcmsCurve.Parametric(SkNamedTransferFn.kSRGB),
            ),
            toXYZD50 = SkNamedGamut.kRec2020,  // different gamut
            hasTrc = true,
            hasToXYZD50 = true,
        )
        assertFalse(skcmsApproximatelyEqualProfiles(skcmsSrgbProfile, rec2020Profile))
    }

    @Test
    fun `approximate-equal rejects different TRCs`() {
        val rec2020TfProfile = SkcmsICCProfile(
            dataColorSpace = SkcmsSignature.RGB.value,
            pcs = SkcmsSignature.XYZ.value,
            trc = arrayOf(
                SkcmsCurve.Parametric(SkNamedTransferFn.kRec2020),
                SkcmsCurve.Parametric(SkNamedTransferFn.kRec2020),
                SkcmsCurve.Parametric(SkNamedTransferFn.kRec2020),
            ),
            toXYZD50 = SkNamedGamut.kSRGB,
            hasTrc = true,
            hasToXYZD50 = true,
        )
        assertFalse(skcmsApproximatelyEqualProfiles(skcmsSrgbProfile, rec2020TfProfile))
    }

    @Test
    fun `approximate-equal rejects mixed Parametric vs Table TRCs`() {
        // Cross-type comparison would require sampling — out of scope
        // for the structural check, return false.
        val tableProfile = SkcmsICCProfile(
            dataColorSpace = SkcmsSignature.RGB.value,
            pcs = SkcmsSignature.XYZ.value,
            trc = arrayOf(
                SkcmsCurve.Table(tableEntries = 2, table8 = byteArrayOf(0, 255.toByte())),
                SkcmsCurve.Table(tableEntries = 2, table8 = byteArrayOf(0, 255.toByte())),
                SkcmsCurve.Table(tableEntries = 2, table8 = byteArrayOf(0, 255.toByte())),
            ),
            toXYZD50 = SkNamedGamut.kSRGB,
            hasTrc = true,
            hasToXYZD50 = true,
        )
        assertFalse(skcmsApproximatelyEqualProfiles(skcmsSrgbProfile, tableProfile))
    }

    @Test
    fun `approximate-equal rejects RGB vs CMYK`() {
        val cmyk = SkcmsICCProfile(
            dataColorSpace = SkcmsSignature.CMYK.value,
            pcs = SkcmsSignature.XYZ.value,
            trc = arrayOf(
                SkcmsCurve.Parametric(SkNamedTransferFn.kSRGB),
                SkcmsCurve.Parametric(SkNamedTransferFn.kSRGB),
                SkcmsCurve.Parametric(SkNamedTransferFn.kSRGB),
            ),
            toXYZD50 = SkNamedGamut.kSRGB,
            hasTrc = true,
            hasToXYZD50 = true,
        )
        assertFalse(skcmsApproximatelyEqualProfiles(skcmsSrgbProfile, cmyk))
    }

    @Test
    fun `approximate-equal rejects profile lacking TRC`() {
        val noTrc = SkcmsICCProfile(
            dataColorSpace = SkcmsSignature.RGB.value,
            pcs = SkcmsSignature.XYZ.value,
            trc = arrayOfNulls(3),
            toXYZD50 = SkNamedGamut.kSRGB,
            hasTrc = false,
            hasToXYZD50 = true,
        )
        assertFalse(skcmsApproximatelyEqualProfiles(skcmsSrgbProfile, noTrc))
    }

    @Test
    fun `approximate-equal rejects profile lacking toXYZD50`() {
        val noMatrix = SkcmsICCProfile(
            dataColorSpace = SkcmsSignature.RGB.value,
            pcs = SkcmsSignature.XYZ.value,
            trc = arrayOf(
                SkcmsCurve.Parametric(SkNamedTransferFn.kSRGB),
                SkcmsCurve.Parametric(SkNamedTransferFn.kSRGB),
                SkcmsCurve.Parametric(SkNamedTransferFn.kSRGB),
            ),
            hasTrc = true,
            hasToXYZD50 = false,
        )
        assertFalse(skcmsApproximatelyEqualProfiles(skcmsSrgbProfile, noMatrix))
    }
}
