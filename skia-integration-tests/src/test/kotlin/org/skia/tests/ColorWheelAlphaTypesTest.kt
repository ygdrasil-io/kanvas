package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Runner for [ColorWheelAlphaTypesGM] (`colorwheel_alphatypes`, 256×128).
 *
 * The GM renders two 128×128 magnified crops of `color_wheel.png` side by
 * side. Upstream uses `kPremul` / `kUnpremul` alpha-type overrides at decode
 * time to show the "dark fringe" artefact when filtering in unpremul space;
 * `:kanvas-skia` decodes both with the codec's natural alpha type so both
 * cells are identical. The left cell (premul) matches the upstream reference
 * well; the right cell (unpremul-fringe) diverges. Floor set to 50 %.
 */
class ColorWheelAlphaTypesTest {

    @Test
    fun `ColorWheelAlphaTypesGM matches colorwheel_alphatypes_png within tolerance`() {
        val gm = ColorWheelAlphaTypesGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image colorwheel_alphatypes.png")

        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("ColorWheelAlphaTypesGM", comparison)
        if (comparison.similarity < 50.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ColorWheelAlphaTypesGM", comparison.similarity)
        assertTrue(accepted, "ColorWheelAlphaTypesGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 50.0,
            "ColorWheelAlphaTypesGM similarity ${"%.2f".format(comparison.similarity)}% < 50.0% floor",
        )
    }
}
