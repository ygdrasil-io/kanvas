package org.skia.tests

import org.graphiks.math.SkISize
import org.skia.core.SkCanvas

/**
 * Port of Skia's `gm/imagefromyuvtextures.cpp::ImageFromYUV(Source::kTextures)`
 * (registered as `image_from_yuv_textures`, 1950 x 800).
 *
 * The upstream GM:
 * 1. Loads `images/mandrill_128.png` and converts it to RGBA 8888.
 * 2. Uses `SkYUVAPixmapInfo` (4-plane Y_U_V_A / k420 / kJPEG_Full) and
 *    `SkColorMatrix_RGB2YUV` to populate Y, U, V, A planes.
 * 3. Wraps the planes in a `sk_gpu_test::LazyYUVImage` (GPU-backed, with
 *    mipmaps) via `LazyYUVImage::kFromTextures`.
 * 4. Draws 36 variants (3 draw-functions × 3 scales × 3 sampling modes +
 *    per-draw reference image) inside `onGpuSetup` / `onDraw`.
 *
 * The entire pipeline depends on GPU-texture-backed YUV images
 * (`SkImage::MakeFromYUVAPixmaps` / `LazyYUVImage::kFromTextures`) which
 * `:kanvas-skia` does not yet implement (raster backend only).
 *
 * Bucket: INTRACTABLE — requires GPU + YUVA pixmap texture upload.
 *
 * TODO: STUB.YUVA_PIXMAPS — SkImage.MakeFromYUVAPixmaps + GPU texture YUV path
 *       not implemented in the kanvas-skia raster backend.
 */
public class ImageFromYUVTexturesGM : GM() {

    override fun getName(): String = "image_from_yuv_textures"

    override fun getISize(): SkISize = SkISize.Make(1950, 800)

    override fun onDraw(canvas: SkCanvas?) {
        // GPU-only GM — requires LazyYUVImage::kFromTextures (Ganesh/Graphite).
        // The upstream GM exits early on any non-GPU context via onGpuSetup
        // returning DrawResult::kSkip.
        TODO("STUB.YUVA_PIXMAPS")
    }
}
