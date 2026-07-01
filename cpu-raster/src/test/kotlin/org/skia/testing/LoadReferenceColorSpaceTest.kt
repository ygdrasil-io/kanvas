package org.skia.testing

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkColorSpace
import org.skia.foundation.xyzAlmostEqual

/**
 * Phase F7 of MIGRATION_PLAN_COLORSPACE_PORT.md — verify
 * `TestUtils.loadReferenceColorSpace` returns the expected DM Rec.2020
 * profile for a sample of reference PNGs, and that
 * `loadReferenceBitmap` tags its output with that colorspace.
 */
class LoadReferenceColorSpaceTest {

    private val sampledPngs = listOf(
        "bigrect", "thinrects", "clip_strokerect",
    )

    @Test
    fun `every reference PNG carries the DM Rec_2020 profile`() {
        for (name in sampledPngs) {
            val cs = TestUtils.loadReferenceColorSpace(name)
            assertNotNull(cs, "$name should have a parsable iCCP chunk")
            // Phase B/J snap should make the parsed profile structurally
            // equivalent to DM_REFERENCE_COLOR_SPACE: same gamut within
            // xyzAlmostEqual tolerance, same parametric TF within
            // transferFnAlmostEqual tolerance.
            assertTrue(
                xyzAlmostEqual(cs!!.toXYZD50, TestUtils.DM_REFERENCE_COLOR_SPACE.toXYZD50),
                "$name: parsed gamut should match DM Rec.2020"
            )
            // gammaCloseToSRGB() should be false (it's Rec.2020, not sRGB).
            assertTrue(!cs.gammaCloseToSRGB(), "$name: TF should not be sRGB")
            assertTrue(!cs.gammaIsLinear(), "$name: TF should not be linear")
        }
    }

    @Test
    fun `loadReferenceBitmap tags the bitmap with the parsed colorspace`() {
        val bm = TestUtils.loadReferenceBitmap("bigrect")
        assertNotNull(bm)
        // Same gamut as DM_REFERENCE_COLOR_SPACE within tolerance.
        assertTrue(
            xyzAlmostEqual(
                bm!!.colorSpace.toXYZD50,
                TestUtils.DM_REFERENCE_COLOR_SPACE.toXYZD50,
            ),
            "bigrect bitmap should be tagged with the DM Rec.2020 gamut"
        )
    }

    @Test
    fun `loadReferenceColorSpace returns null for missing PNG`() {
        org.junit.jupiter.api.Assertions.assertNull(
            TestUtils.loadReferenceColorSpace("does-not-exist")
        )
    }
}
