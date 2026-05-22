package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Stub port of Skia's `gm/wacky_yuv_formats.cpp::WackyYUVFormatsGM`
 * (registered as `wacky_yuv_formats[_limited]`, 1880 x 1430).
 *
 * Upstream synthesises a synthetic YUV multi-plane image (one
 * variant per supported [SkYUVAInfo.PlaneConfig] x
 * [SkYUVAInfo.Subsampling] combination), uploads it via
 * `SkImage.MakeFromYUVAPixmaps`, and lays out a grid of
 * `drawImage` calls. The point is to verify that the GPU YUV-
 * to-RGB conversion path picks up the right matrix coefficient
 * table per [SkYUVColorSpace] (Rec.601/709/2020) and the right
 * full-vs-limited range conversion.
 *
 * `:kanvas-skia` does not implement the YUV-multi-plane image
 * path at all -- only single-plane RGB images go through the
 * `SkImage.MakeFromPixels` helper. The full YUVA plumbing
 * (PlaneConfig + Subsampling + Pixmap dispatch) is a GPU-plan
 * item.
 *
 * TODO: missing API -- `SkImage.MakeFromYUVAPixmaps`,
 * `SkYUVAInfo`, `SkYUVAPixmapInfo`, `SkYUVAPixmaps`. Flag-planting
 * stub: empty draw, fixed size.
 */
public class WackyYUVFormatsGM(
    private val useLimitedRange: Boolean = false,
) : GM() {

    override fun getName(): String =
        "wacky_yuv_formats" + if (useLimitedRange) "_limited" else ""

    override fun getISize(): SkISize = SkISize.Make(1880, 1430)

    override fun onDraw(canvas: SkCanvas?) {
        // TODO: missing API -- SkImage.MakeFromYUVAPixmaps.
    }
}
