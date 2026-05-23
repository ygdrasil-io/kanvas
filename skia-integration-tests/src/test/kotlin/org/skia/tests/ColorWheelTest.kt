package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Runner for [ColorWheelGM] (`colorwheel`, 384×256).
 *
 * The GM draws `color_wheel.png` (top-left) and `color_wheel.jpg` (bottom-
 * middle) over a checkerboard background. GIF, WEBP, and AVIF slots are
 * absent from the `:kanvas-skia` resource tree so those quadrants show the
 * underlying checkerboard, diverging from the upstream reference.
 * Similarity floor is loose (30 %) to tolerate the missing-format gap.
 */
class ColorWheelTest {

    @Test
    fun `ColorWheelGM matches colorwheel_png within tolerance`() {
        val gm = ColorWheelGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image colorwheel.png")

        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("ColorWheelGM", comparison)
        if (comparison.similarity < 30.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ColorWheelGM", comparison.similarity)
        assertTrue(accepted, "ColorWheelGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 30.0,
            "ColorWheelGM similarity ${"%.2f".format(comparison.similarity)}% < 30.0% floor",
        )
    }
}
