package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Placeholder for Skia's `gm/gradients.cpp::gradients_hue_method`
 * (`DEF_SIMPLE_GM_BG`, 285 × 155, gray background).
 *
 * Upstream renders 4 linear gradients (Red → Green → Red) in HSL color space,
 * each using a different `HueMethod`: Shorter, Longer, Increasing, Decreasing.
 * Two additional rows exercise the kLonger hue method bug (skbug.com/40044215)
 * with explicit vs. implicit endpoint positions.
 *
 * **Implementation gap** : the `HueMethod` enum is exposed, but HSL/hue
 * interpolation still needs a dedicated gradient sampler.
 */
public class GradientsHueMethodGM : GM() {

    override fun getName(): String = "gradients_hue_method"
    override fun getISize(): SkISize = SkISize.Make(285, 155)

    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.GRADIENT_INTERPOLATION: hue-method gradient interpolation not implemented")
    }
}
