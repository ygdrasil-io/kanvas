package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * Disabled test stubs for `gm/gradients.cpp` GMs that require the remaining
 * non-RGB pieces of `SkGradient::Interpolation`.
 *
 * Affected GMs:
 *  - `gradients_color_space`          — 14 color spaces (sRGB…Rec2020)
 *  - `gradients_hue_method`           — HSL core exists; GM port still needs
 *                                       labels and explicit-position endpoint handling
 *  - `gradients_color_space_tilemode` — OKLCH × 4 tile modes
 *  - `gradients_color_space_many_stops` — OKLCH + 200 stops (GPU texture fallback)
 *  - `gradients_powerless_hue_LCH`   — powerless-hue in LCH
 *  - `gradients_powerless_hue_OKLCH` — powerless-hue in OKLCH
 *  - `gradients_powerless_hue_HSL`   — powerless-hue in HSL
 *  - `gradients_powerless_hue_HWB`   — powerless-hue in HWB
 *
 * Each GM's `onDraw` contains a `TODO("STUB.GRADIENT_INTERPOLATION")` that
 * will throw `NotImplementedError` if accidentally called, ensuring no
 * silent empty-canvas passes occur.
 */
@Disabled("STUB.GRADIENT_INTERPOLATION: perceptual/powerless hue GMs and hue-method GM port are not complete")
class GradientsInterpolationStubsTest {

    @Test
    fun `gradients_color_space GM stub`() {
        GradientsColorSpaceGM()
    }

    @Test
    fun `gradients_hue_method GM stub`() {
        GradientsHueMethodGM()
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
    fun `gradients_powerless_hue_LCH GM stub`() {
        GradientsPowerlessHueLchGM()
    }

    @Test
    fun `gradients_powerless_hue_OKLCH GM stub`() {
        GradientsPowerlessHueOklchGM()
    }

    @Test
    fun `gradients_powerless_hue_HSL GM stub`() {
        GradientsPowerlessHueHslGM()
    }

    @Test
    fun `gradients_powerless_hue_HWB GM stub`() {
        GradientsPowerlessHueHwbGM()
    }
}
