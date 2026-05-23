package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Stub port of Skia's `gm/scalepixels.cpp::ScalePixelsGM`
 * (registered as `scale-pixels`, 960 x 720).
 *
 * Upstream allocates a small `SkBitmap`, fills it via
 * `surface->draw`, then calls `SkPixmap::scalePixels` with various
 * sampling options to produce scaled-down + scaled-up variants
 * and lays them out in a grid. The point is to verify the
 * `SkPixmap::scalePixels` resampling path independently of the
 * GPU sampler dispatch.
 *
 * `:kanvas-skia` does not expose `SkPixmap::scalePixels` (only
 * `SkBitmap.scaleTo`, which goes through the canvas draw path,
 * not the pixmap-direct path). Without it the reference grid
 * cannot be reproduced.
 *
 * TODO: missing API -- `SkPixmap::scalePixels` + matching
 * `Surface.readPixels(pixmap)`. Flag-planting stub: empty
 * draw, fixed size.
 */
public class ScalePixelsGM : GM() {

    override fun getName(): String = "scale-pixels"
    override fun getISize(): SkISize = SkISize.Make(960, 720)

    override fun onDraw(canvas: SkCanvas?) {
        // TODO: missing API -- SkPixmap.scalePixels.
    }
}
