package org.skia.tools

import org.skia.foundation.SkImage
import org.skia.foundation.SkYUVAInfo
import org.skia.foundation.SkYUVAPixmaps
import org.skia.gpu.YUVUtils.YUVSubsampling

/**
 * Mirrors Skia's `tools/gpu/YUVUtils.h` test helpers — split an RGB(A)
 * [SkImage] into per-plane A8 [SkImage]s under a chosen
 * [SkYUVColorSpace] + chroma [YUVSubsampling], suitable for feeding
 * back into [org.skia.foundation.SkImages.YUVA] (the raster equivalent
 * of upstream's `SkImages::TextureFromYUVAPixmaps`).
 *
 * `:kanvas-skia` does not yet implement the YUV plane-splitting math
 * (the cross-channel mixer matrices vary per [SkYUVColorSpace] +
 * full / limited-range flag). The surface here is flag-planting so GMs
 * that exercise the upstream test helper (`gm/wacky_yuv_formats.cpp::YUVSplitterGM`)
 * compile against a real call site rather than against a placeholder
 * `// TODO: missing API` comment.
 *
 * Mirrors the upstream signature :
 * ```cpp
 * std::tuple<std::array<sk_sp<SkImage>, SkYUVAInfo::kMaxPlanes>,
 *            SkYUVAInfo>
 *   sk_gpu_test::MakeYUVAPlanesAsA8(SkImage*, SkYUVColorSpace,
 *                                   SkYUVAInfo::Subsampling,
 *                                   GrRecordingContext*);
 * ```
 */
public object SkGpuTestUtils {
    /**
     * Container for the per-plane A8 [SkImage] array + the
     * [SkYUVAInfo] describing the plane layout. Mirrors the
     * `std::tuple<std::array<...>, SkYUVAInfo>` upstream return.
     */
    public data class YUVAPlanesAsA8(
        public val planes: List<SkImage>,
        public val info: SkYUVAInfo,
    )

    /**
     * Split [src] into A8 luma + chroma planes under [colorSpace] with
     * [subsampling] chroma decimation. The fourth plane is the alpha
     * channel iff [src] is not opaque (otherwise 3 planes).
     *
     * **TODO: STUB.YUVA_PIXMAPS** — split kernel + `SkColorMatrix_RGB2YUV`
     * matrices not yet wired through `:kanvas-skia`. Implementers should
     * land the same `sk_gpu_test::MakeYUVAPlanesAsA8` math + write the
     * per-channel A8 bitmaps via [org.skia.foundation.SkImage.MakeFromBitmap].
     */
    public fun MakeYUVAPlanesAsA8(
        src: SkImage,
        colorSpace: SkYUVAInfo.YUVColorSpace,
        subsampling: YUVSubsampling = YUVSubsampling.k444,
    ): YUVAPlanesAsA8 =
        TODO("STUB.YUVA_PIXMAPS: SkGpuTestUtils.MakeYUVAPlanesAsA8")
}
