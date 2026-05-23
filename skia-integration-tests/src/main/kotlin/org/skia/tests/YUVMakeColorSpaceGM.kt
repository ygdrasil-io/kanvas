package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Stub port of Skia's `gm/yuvtorgbeffect.cpp::YUVMakeColorSpaceGM`
 * (registered as `yuv_make_color_space`, 1100 x 750).
 *
 * Upstream constructs an `SkImage` from YUVA pixmaps in one
 * `SkColorSpace`, then re-tags it into a wider gamut via
 * `SkImage.makeColorSpace(newCS)` and lays both versions out
 * side-by-side. The point is to verify that the colour-space
 * re-tag preserves the YUV-to-RGB matrix transformation and
 * only re-encodes the RGB output.
 *
 * `:kanvas-skia` does not implement
 * `SkImage.MakeFromYUVAPixmaps` (see [WackyYUVFormatsGM]) nor
 * `SkImage.makeColorSpace` for YUVA-backed images. The full
 * path is part of the GPU plan.
 *
 * TODO: missing API -- `SkImage.MakeFromYUVAPixmaps` +
 * `SkImage.makeColorSpace`. Flag-planting stub: empty draw,
 * fixed size.
 */
public class YUVMakeColorSpaceGM : GM() {

    override fun getName(): String = "yuv_make_color_space"
    override fun getISize(): SkISize = SkISize.Make(1100, 750)

    override fun onDraw(canvas: SkCanvas?) {
        // TODO: missing API -- YUVA SkImage + makeColorSpace.
    }
}
