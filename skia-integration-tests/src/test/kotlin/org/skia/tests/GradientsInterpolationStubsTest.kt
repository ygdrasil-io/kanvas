package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Disabled test stubs for `gm/gradients.cpp` GMs that require the remaining
 * non-RGB pieces of `SkGradient::Interpolation`.
 *
 * Affected GMs:
 *  - `gradients_color_space`          — 14 color spaces (sRGB…Rec2020)
 *  - `gradients_color_space_tilemode` — OKLCH × 4 tile modes
 *  - `gradients_color_space_many_stops` — OKLCH + 200 stops (GPU texture fallback)
 *  - `gradients_powerless_hue_OKLCH` — powerless-hue in OKLCH
 *  - `gradients_powerless_hue_HWB`   — powerless-hue in HWB
 *
 * Each GM's `onDraw` contains a `TODO("STUB.GRADIENT_INTERPOLATION")` that
 * will throw `NotImplementedError` if accidentally called, ensuring no
 * silent empty-canvas passes occur.
 */
@Disabled("STUB.GRADIENT_INTERPOLATION: perceptual/powerless hue GMs are not complete (except LCH/HSL)")
class GradientsInterpolationStubsTest {

    @Test
    fun `gradients_color_space GM stub`() {
        GradientsColorSpaceGM()
    }

    @Test
    fun `gradients_color_space_tilemode GM stub`() {
        GradientsColorSpaceTilemodeGM()
    }

    @Test
    fun `gradients_color_space_many_stops GM stub`() {
        GradientsColorSpaceManyStopsGM()
    }

    @Test
    fun `gradients_powerless_hue_OKLCH GM stub`() {
        GradientsPowerlessHueOklchGM()
    }

    @Test
    fun `gradients_powerless_hue_HWB GM stub`() {
        GradientsPowerlessHueHwbGM()
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
