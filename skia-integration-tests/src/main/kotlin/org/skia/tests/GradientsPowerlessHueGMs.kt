package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Placeholders for Skia's `gm/gradients.cpp` powerless-hue GM family
 * (`DEF_POWERLESS_HUE_GM(colorSpace)`, 415 × 330 each).
 *
 * Four GMs, one per CSS hue-bearing color space:
 *  - `gradients_powerless_hue_LCH`   — `ColorSpace::kLCH`
 *  - `gradients_powerless_hue_OKLCH` — `ColorSpace::kOKLCH`
 *  - `gradients_powerless_hue_HSL`   — `ColorSpace::kHSL`
 *  - `gradients_powerless_hue_HWB`   — `ColorSpace::kHWB`
 *
 * Each exercises how the gradient interpolator handles "powerless hue"
 * components (white, black, transparent) when interpolating in a hue-
 * bearing color space. Also tests hue propagation with kShorter / kIncreasing
 * / kDecreasing / kLonger hue methods on black-white sequences.
 *
 * **API gap** : requires `SkGradient::Interpolation::ColorSpace` (LCH, OKLCH,
 * HSL, HWB variants), `SkGradient::Interpolation::HueMethod`, and
 * `SkGradient::Interpolation::InPremul`. None of these are exposed in
 * `:cpu-raster`.
 */

public class GradientsPowerlessHueLchGM : GM() {
    override fun getName(): String = "gradients_powerless_hue_LCH"
    override fun getISize(): SkISize = SkISize.Make(415, 330)
    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.GRADIENT_INTERPOLATION: SkGradient.Interpolation.ColorSpace::kLCH not exposed in :cpu-raster")
    }
}

public class GradientsPowerlessHueOklchGM : GM() {
    override fun getName(): String = "gradients_powerless_hue_OKLCH"
    override fun getISize(): SkISize = SkISize.Make(415, 330)
    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.GRADIENT_INTERPOLATION: SkGradient.Interpolation.ColorSpace::kOKLCH not exposed in :cpu-raster")
    }
}

public class GradientsPowerlessHueHslGM : GM() {
    override fun getName(): String = "gradients_powerless_hue_HSL"
    override fun getISize(): SkISize = SkISize.Make(415, 330)
    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.GRADIENT_INTERPOLATION: SkGradient.Interpolation.ColorSpace::kHSL not exposed in :cpu-raster")
    }
}

public class GradientsPowerlessHueHwbGM : GM() {
    override fun getName(): String = "gradients_powerless_hue_HWB"
    override fun getISize(): SkISize = SkISize.Make(415, 330)
    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.GRADIENT_INTERPOLATION: SkGradient.Interpolation.ColorSpace::kHWB not exposed in :cpu-raster")
    }
}
