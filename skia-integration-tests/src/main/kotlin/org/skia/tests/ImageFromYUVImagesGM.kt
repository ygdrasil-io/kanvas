package org.skia.tests

import org.graphiks.math.SkISize
import org.skia.core.SkCanvas

/**
 * Port of Skia's `gm/imagefromyuvtextures.cpp::ImageFromYUV(Source::kImages)`
 * (registered as `image_from_yuv_images`, 1950 x 800).
 *
 * Identical structure to [ImageFromYUVTexturesGM] but uses
 * `LazyYUVImage::kFromImages` instead of `kFromTextures` to materialise the
 * GPU-backed YUV image. In the upstream C++ the difference controls whether
 * the YUVA planes are uploaded as backend textures (Ganesh path) or as
 * Graphite-native images.
 *
 * Note: upstream `onGpuSetup` returns `DrawResult::kSkip` for this variant
 * when running under a Ganesh `GrDirectContext` (only Graphite supports it).
 *
 * The entire pipeline depends on GPU-texture-backed YUV images
 * (`SkImage::MakeFromYUVAPixmaps` / `LazyYUVImage::kFromImages`) which
 * `:kanvas-skia` does not yet implement (raster backend only).
 *
 * Bucket: INTRACTABLE — requires GPU + YUVA pixmap image upload.
 *
 * TODO: STUB.YUVA_PIXMAPS — SkImage.MakeFromYUVAPixmaps + GPU image YUV path
 *       not implemented in the kanvas-skia raster backend.
 */
public class ImageFromYUVImagesGM : GM() {

    override fun getName(): String = "image_from_yuv_images"

    override fun getISize(): SkISize = SkISize.Make(1950, 800)

    override fun onDraw(canvas: SkCanvas?) {
        // GPU-only GM — requires LazyYUVImage::kFromImages (Graphite only).
        // The upstream GM exits early on any non-GPU context via onGpuSetup
        // returning DrawResult::kSkip.
        TODO("STUB.YUVA_PIXMAPS")
    }
}
