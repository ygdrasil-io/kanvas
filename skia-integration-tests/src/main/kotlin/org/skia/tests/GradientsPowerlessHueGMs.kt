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
 * **Implementation gap** : the interpolation enums are exposed, but the
 * perceptual / hue / premul gradient sampler is still missing.
 */

public class GradientsPowerlessHueLchGM : GM() {
    override fun getName(): String = "gradients_powerless_hue_LCH"
    override fun getISize(): SkISize = SkISize.Make(415, 330)
    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.GRADIENT_INTERPOLATION: LCH powerless-hue interpolation not implemented")
    }
}

public class GradientsPowerlessHueOklchGM : GM() {
    override fun getName(): String = "gradients_powerless_hue_OKLCH"
    override fun getISize(): SkISize = SkISize.Make(415, 330)
    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.GRADIENT_INTERPOLATION: OKLCH powerless-hue interpolation not implemented")
    }
}

public class GradientsPowerlessHueHslGM : GM() {
    override fun getName(): String = "gradients_powerless_hue_HSL"
    override fun getISize(): SkISize = SkISize.Make(415, 330)
    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.GRADIENT_INTERPOLATION: HSL powerless-hue interpolation not implemented")
    }
}

public class GradientsPowerlessHueHwbGM : GM() {
    override fun getName(): String = "gradients_powerless_hue_HWB"
    override fun getISize(): SkISize = SkISize.Make(415, 330)
    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.GRADIENT_INTERPOLATION: HWB powerless-hue interpolation not implemented")
    }
}
