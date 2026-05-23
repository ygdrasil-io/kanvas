package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Placeholder for Skia's `gm/gradients.cpp::gradients_color_space_many_stops`
 * (`DEF_SIMPLE_GM_BG`, 500 × 500, gray background).
 *
 * Tests CSS gradient color space interpolation (OKLCH) with 200 stops.
 * From the upstream comment: "We're mostly interested in making sure that
 * the texture fallback on GPU works correctly."
 *
 * **API gap** : requires `SkGradient::Interpolation::ColorSpace::kOKLCH` and
 * the `SkGradient` struct overload of `SkShaders::LinearGradient`.
 */
public class GradientsColorSpaceManyStopsGM : GM() {

    override fun getName(): String = "gradients_color_space_many_stops"
    override fun getISize(): SkISize = SkISize.Make(500, 500)

    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.GRADIENT_INTERPOLATION: SkGradient.Interpolation.ColorSpace::kOKLCH not exposed in :cpu-raster")
    }
}
