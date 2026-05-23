package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Stub port of Skia's `gm/wacky_yuv_formats.cpp::YUVSplitterGM`
 * (registered as `yuv_splitter`, 1280 x 768, Ganesh/GPU-only).
 *
 * Upstream loads `images/mandrill_256.png`, decomposes it into YUVA
 * planes at 4:4:4 subsampling for each of four YUV colour spaces
 * (Rec.709, Rec.601, JPEG, BT.2020) via
 * `sk_gpu_test::MakeYUVAPlanesAsA8`, reassembles them into a GPU
 * `SkImage` with `SkImages::TextureFromYUVAPixmaps`, and draws both
 * the reconstructed image and a scaled difference against the
 * original. The primary purpose is to validate the
 * `SkColorMatrix_RGB2YUV` round-trip on the GPU path.
 *
 * `:kanvas-skia` does not implement the GPU YUVA texture path; the
 * APIs needed are:
 *   - `sk_gpu_test::MakeYUVAPlanesAsA8` (test helper — splits an
 *     `SkImage` into A8 planes in a given `SkYUVColorSpace`)
 *   - `SkImages::TextureFromYUVAPixmaps` (uploads YUVA planes to GPU
 *     and performs on-GPU YUV-to-RGB conversion)
 *   - `SkShaders::Blend(SkBlendMode::kDifference, …)` for the diff
 *     visualisation
 *
 * TODO("STUB.YUVA_PIXMAPS")
 */
public class YUVSplitterGM : GM() {

    override fun getName(): String = "yuv_splitter"

    override fun getISize(): SkISize = SkISize.Make(1280, 768)

    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.YUVA_PIXMAPS")
    }
}
