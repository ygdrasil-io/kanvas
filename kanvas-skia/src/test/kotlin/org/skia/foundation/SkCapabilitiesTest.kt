package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * R1 stub-coverage tests for [SkCapabilities] — raster backend
 * exposes minimal capabilities.
 */
class SkCapabilitiesTest {

    @Test
    fun `RasterBackend exposes SkSL k100 and no extension flags`() {
        val caps = SkCapabilities.RasterBackend()
        assertEquals(SkCapabilities.SkSLVersion.k100, caps.skslVersion)
        assertFalse(caps.fSupportSampleLocations)
        assertFalse(caps.fDualSourceBlendingSupport)
    }

    @Test
    fun `RasterBackend returns the same singleton`() {
        assertSame(SkCapabilities.RasterBackend(), SkCapabilities.RasterBackend())
    }
}
