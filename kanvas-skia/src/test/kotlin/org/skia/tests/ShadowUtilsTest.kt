package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ShadowUtilsTest {

    @Test
    fun `ShadowUtilsGM debug-color no-occluders matches shadow_utils_png within tolerance`() {
        runVariant(ShadowUtilsGM.Mode.DebugColorNoOccluders, "ShadowUtils_DebugColorNoOccluders", floor = 74.9)
    }

    @Test
    fun `ShadowUtilsGM debug-color with occluders matches shadow_utils_occl_png within tolerance`() {
        runVariant(ShadowUtilsGM.Mode.DebugColorOccluders, "ShadowUtils_DebugColorOccluders", floor = 75.5)
    }

    @Test
    fun `ShadowUtilsGM grayscale matches shadow_utils_gray_png within tolerance`() {
        runVariant(ShadowUtilsGM.Mode.Grayscale, "ShadowUtils_Grayscale", floor = 90.2)
    }

    private fun runVariant(mode: ShadowUtilsGM.Mode, scoreKey: String, floor: Double) {
        val gm = ShadowUtilsGM(mode)
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")

        // SkShadowUtils analytic mesh + falloff bezel diverges from
        // upstream's GPU reference; the convex shadow shapes overlap
        // structurally but pixel-level alpha varies.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 16)
        TestReport.recordDetailed(scoreKey, comparison)
        if (comparison.similarity < floor) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore(scoreKey, comparison.similarity)
        assertTrue(accepted, "$scoreKey regressed below ratchet")
    }
}
