package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Placeholder port of upstream Skia `gm/animated_image.cpp::AnimatedImageGM`.
 *
 * Original tracks animated images (GIF/WebP/AVIF) via `SkAnimatedImage`
 * with on-canvas updates per requested `update()` interval. Requires
 * `SkAnimatedImage` + `SkCodec` that aren't in `:kanvas-skia` yet.
 *
 * TODO: missing API — `SkAnimatedImage`, `SkCodec`, `decodeNextFrame`.
 * Flag-planting stub.
 */
public class AnimatedImageGM : GM() {
    override fun getName(): String = "AnimatedImage"
    override fun getISize(): SkISize = SkISize.Make(500, 500)

    override fun onDraw(canvas: SkCanvas?) {
        // TODO: missing API — SkAnimatedImage frame decode + draw pipeline.
    }
}
