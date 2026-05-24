package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Placeholder for Skia's `gm/gradients.cpp::gradients_color_space`
 * (`DEF_SIMPLE_GM_BG`, 265 × 355, gray background).
 *
 * Upstream renders 14 linear gradients (Blue → Yellow), each interpolated
 * in a different CSS color space via `SkGradient::Interpolation::ColorSpace`:
 * sRGB, Linear, Lab, OKLab, OKLabGamutMap, LCH, OKLCH, OKLCHGamutMap, HSL,
 * HWB, a98RGB, ProPhotoRGB, DisplayP3, Rec2020. A label column identifies
 * each row.
 *
 * **Implementation gap** : the `SkGradient` aggregate and linear-gradient
 * overload are exposed for RGB working spaces, but the perceptual CSS
 * interpolation spaces still need a dedicated sampler.
 */
public class GradientsColorSpaceGM : GM() {

    override fun getName(): String = "gradients_color_space"
    override fun getISize(): SkISize = SkISize.Make(265, 355)

    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.GRADIENT_INTERPOLATION: perceptual gradient color-space interpolation not implemented")
    }
}
