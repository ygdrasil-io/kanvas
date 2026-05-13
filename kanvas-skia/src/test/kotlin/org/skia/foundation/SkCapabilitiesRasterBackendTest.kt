package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * R-suivi.5 — verify [SkCapabilities.RasterBackend] reports the
 * complete set of feature flags expected from the raster backend.
 *
 * All GPU-related features must be `false`. SkSL is always `k100`.
 * `fFloatIs32Bits` is always `true` on the JVM (IEEE-754).
 */
class SkCapabilitiesRasterBackendTest {

    @Test
    fun `RasterBackend reports SkSL k100`() {
        val caps = SkCapabilities.RasterBackend()
        assertEquals(SkCapabilities.SkSLVersion.k100, caps.skslVersion)
    }

    @Test
    fun `RasterBackend turns off every GPU feature flag`() {
        val caps = SkCapabilities.RasterBackend()
        assertFalse(caps.fSupportSampleLocations, "fSupportSampleLocations")
        assertFalse(caps.fMSAAResolvesAutomatically, "fMSAAResolvesAutomatically")
        assertFalse(caps.fDualSourceBlendingSupport, "fDualSourceBlendingSupport")
        assertFalse(caps.fFBFetchSupport, "fFBFetchSupport")
        assertFalse(caps.fShaderDerivativeSupport, "fShaderDerivativeSupport")
        assertFalse(caps.fIntegerSupport, "fIntegerSupport")
        assertFalse(caps.fFlatInterpolationSupport, "fFlatInterpolationSupport")
        assertFalse(caps.fCanUseFragCoord, "fCanUseFragCoord")
        assertFalse(caps.fInverseHyperbolicSupport, "fInverseHyperbolicSupport")
        assertFalse(caps.fNonsquareMatrixSupport, "fNonsquareMatrixSupport")
        assertFalse(caps.fExternalTextureSupport, "fExternalTextureSupport")
    }

    @Test
    fun `RasterBackend reports 32-bit float and infinity (JVM is IEEE-754)`() {
        val caps = SkCapabilities.RasterBackend()
        assertTrue(caps.fFloatIs32Bits, "JVM is IEEE-754 32-bit")
        assertTrue(caps.fInfinitySupport, "Java has Float.POSITIVE_INFINITY")
    }

    @Test
    fun `RasterBackend returns the same singleton`() {
        assertSame(SkCapabilities.RasterBackend(), SkCapabilities.RasterBackend())
    }

    @Test
    fun `SkSLVersion enum exposes k100 k300 k330`() {
        val values = SkCapabilities.SkSLVersion.entries.map { it.name }.toSet()
        assertTrue("k100" in values)
        assertTrue("k300" in values)
        assertTrue("k330" in values)
    }
}
