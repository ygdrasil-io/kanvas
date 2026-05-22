package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Placeholder for `gm/flippity.cpp::FlippityGM` (520 × 760, GPU-only).
 *
 * Renders 16 mirror/flip permutations of a GPU-backed image (texture
 * sampled through orientation-modifying `SkImage::makeOrientedImage`
 * helpers) — useful for verifying texture-coordinate handling under
 * the `kBottomLeft_GrSurfaceOrigin` quirk that some backends use.
 *
 * **kanvas-skia** : GPU-only — the reference `original-888/flippity.png`
 * was rendered through the GPU sink ; the raster sink does not exercise
 * the orientation-twiddling code path. Stub keeps the class registered.
 */
public class FlippityGM : GM() {
    override fun getName(): String = "flippity"
    override fun getISize(): SkISize = SkISize.Make(520, 760)
    override fun onDraw(canvas: SkCanvas?) {
        // TODO : port once GPU-image-with-orientation helpers land.
    }
}
