package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class GradientsInterpolationSmokeTest {

    @Test
    fun `gradients_color_space_tilemode GM draws`() {
        TestUtils.runGmTest(GradientsColorSpaceTilemodeGM())
    }

    @Test
    fun `gradients_powerless_hue_OKLCH GM draws`() {
        TestUtils.runGmTest(GradientsPowerlessHueOklchGM())
    }

    @Test
    fun `gradients_powerless_hue_HWB GM draws`() {
        TestUtils.runGmTest(GradientsPowerlessHueHwbGM())
    }
}

class GradientsPowerlessHueHslTest {

    @Test
    fun `GradientsPowerlessHueHslGM matches reference within tolerance`() {
        val gm = GradientsPowerlessHueHslGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")

        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 2)
        TestReport.recordDetailed("GradientsPowerlessHueHslGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }

        val accepted = SimilarityTracker.updateScore(
            "GradientsPowerlessHueHslGM",
            comparison.similarity,
        )
        assertTrue(accepted, "GradientsPowerlessHueHslGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 60.0,
            "GradientsPowerlessHueHslGM similarity ${"%.2f".format(comparison.similarity)}% < 60.0% floor",
        )
    }
}

class GradientsPowerlessHueLchTest {

    @Test
    fun `GradientsPowerlessHueLchGM matches reference within tolerance`() {
        val gm = GradientsPowerlessHueLchGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")

        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 2)
        TestReport.recordDetailed("GradientsPowerlessHueLchGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }

        val accepted = SimilarityTracker.updateScore(
            "GradientsPowerlessHueLchGM",
            comparison.similarity,
        )
        assertTrue(accepted, "GradientsPowerlessHueLchGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 60.0,
            "GradientsPowerlessHueLchGM similarity ${"%.2f".format(comparison.similarity)}% < 60.0% floor",
        )
    }
}
