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
 * **API gap** : requires `SkGradient::Interpolation::ColorSpace` enum and
 * the overloaded `SkShaders::LinearGradient(pts, SkGradient{…, interpolation})`
 * factory. Neither is exposed in `:cpu-raster` — the current [SkLinearGradient]
 * only supports the default sRGB interpolation path.
 */
public class GradientsColorSpaceGM : GM() {

    override fun getName(): String = "gradients_color_space"
    override fun getISize(): SkISize = SkISize.Make(265, 355)

    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.GRADIENT_INTERPOLATION: SkGradient.Interpolation.ColorSpace not exposed in :cpu-raster")
    }
}
