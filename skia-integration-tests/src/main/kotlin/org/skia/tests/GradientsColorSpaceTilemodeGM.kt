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
 * **Implementation gap** : requires OKLCH interpolation in the gradient
 * sampler. The `SkGradient` struct overload itself exists for RGB spaces.
 */
public class GradientsColorSpaceTilemodeGM : GM() {

    override fun getName(): String = "gradients_color_space_tilemode"
    override fun getISize(): SkISize = SkISize.Make(360, 105)

    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.GRADIENT_INTERPOLATION: OKLCH gradient interpolation not implemented")
    }
}
