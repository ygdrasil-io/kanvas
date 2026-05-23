package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Placeholder for Skia's `gm/gradients.cpp::gradients_color_space_tilemode`
 * (`DEF_SIMPLE_GM_BG`, 360 × 105, gray background).
 *
 * Tests CSS gradient color space interpolation (OKLCH) in combination with
 * all four tile modes (Clamp, Repeat, Mirror, Decal). The gradient intentionally
 * sits inside `[20, 120]` of a 350-wide rect so tile modes are visible.
 *
 * **API gap** : requires `SkGradient::Interpolation::ColorSpace::kOKLCH` and
 * the `SkGradient` struct overload of `SkShaders::LinearGradient`.
 */
public class GradientsColorSpaceTilemodeGM : GM() {

    override fun getName(): String = "gradients_color_space_tilemode"
    override fun getISize(): SkISize = SkISize.Make(360, 105)

    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.GRADIENT_INTERPOLATION: SkGradient.Interpolation.ColorSpace::kOKLCH not exposed in :cpu-raster")
    }
}
