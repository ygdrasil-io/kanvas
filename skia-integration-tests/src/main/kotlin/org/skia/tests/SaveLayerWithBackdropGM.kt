package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Stub port of Skia's `gm/savelayer.cpp::SaveLayerWithBackdropGM`
 * (registered as `savelayer_with_backdrop`, 830 x 550).
 *
 * Upstream verifies `SkCanvas.saveLayer` interaction with a
 * non-null `backdrop` `SkImageFilter` (a blur), under a range of
 * clip / translate / saveLayer-bounds combinations. The
 * `backdrop` slot copies the parent layer's pixels through the
 * filter into the new offscreen layer, so the filter sees the
 * already-rendered scene under it.
 *
 * `:kanvas-skia` supports a *copy-only* backdrop seed (per
 * PR #591 -- "saveLayer -- backdrop slot (copy-only GPU
 * seeding)") but not yet a backdrop that actually runs the
 * blur image-filter on the seeded pixels. The reference image
 * therefore cannot be matched without that filter step.
 *
 * TODO: missing API -- backdrop-image-filter execution
 * (`SkImageFilter::filterImage` on the seeded copy). Flag-planting
 * stub: empty draw, fixed size.
 */
public class SaveLayerWithBackdropGM : GM() {

    override fun getName(): String = "savelayer_with_backdrop"
    override fun getISize(): SkISize = SkISize.Make(830, 550)

    override fun onDraw(canvas: SkCanvas?) {
        // TODO: missing API -- backdrop image-filter execution.
    }
}
