package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/yuvtorgbsubset.cpp::YUVtoRGBSubsetEffect`
 * (registered as `yuv_to_rgb_subset_effect`, 1310 × 540, upstream GPU-only).
 *
 * ## What the upstream GM does
 *
 * Tests **subsetting YUV multiplanar images** where U/V planes have lower
 * resolution than Y (4:2:0 subsampling, `skbug.com/40040241`).
 *
 * It synthesises an 8×8 JPEG-full `kY_U_V` YUV image (Y at full res,
 * U/V at 4×4 with inner 2×2 colour blocks), uploads it as a GPU texture
 * via `sk_gpu_test::LazyYUVImage`, and renders a 2-row (kNearest /
 * kLinear) × 5-column grid:
 *
 *  - Column 0 : full image (no subset), `kClamp`.
 *  - Columns 1–4 : subset `SkIRect{2,2,6,6}` with each
 *    [org.skia.foundation.SkTileMode] (kClamp, kRepeat, kMirror, kDecal).
 *
 * Each cell is drawn via an `SkImage` shader (image outset by half its
 * dimensions to reveal tile/wrap behaviour). A black background rectangle
 * precedes each cell so `kDecal` pixels appear black.
 *
 * ## Bucket : INTRACTABLE (GPU-only)
 *
 * The entire draw path requires:
 *  - `sk_gpu_test::LazyYUVImage` — a GPU test helper that uploads
 *    `SkYUVAPixmaps` as multi-plane GPU textures. No CPU-raster equivalent.
 *  - `SkImage::makeSubset(SkRecorder*, const SkIRect&, RequiredProperties)` —
 *    the overload taking a GPU `SkRecorder*`, which produces a GPU-backed
 *    YUV subset image routed through the YUV-to-RGB fragment processor.
 *    The kanvas-skia `SkImage.makeSubset(SkIRect)` raster overload does not
 *    cover this path.
 *
 * Upstream `onGpuSetup` explicitly returns `DrawResult::kSkip` when neither
 * a `GrDirectContext` nor a Graphite `Recorder` is available — i.e. this GM
 * is a no-op on the CPU path.
 *
 * Calling [onDraw] throws `TODO("STUB.LAZY_YUV_IMAGE")`.
 * The matching [YuvToRgbSubsetEffectTest] is `@Disabled`.
 *
 * Upstream cpp : `gm/yuvtorgbsubset.cpp`.
 */
public class YuvToRgbSubsetEffectGM : GM() {

    override fun getName(): String = "yuv_to_rgb_subset_effect"

    override fun getISize(): SkISize = SkISize.Make(1310, 540)

    override fun onDraw(canvas: SkCanvas?) {
        // Upstream onGpuSetup builds the YUV image via:
        //   auto lazyYUV = sk_gpu_test::LazyYUVImage::Make(fPixmaps);
        //   fYUVImage = lazyYUV->refImage(context/recorder, kFromPixmaps);
        //
        // onDraw then iterates kFilters x (kSkTileModeCount+1) cells,
        // calling:
        //   fYUVImage->makeSubset(recorder, kColorRect, {false})
        // for the subsetted columns. The recorder overload of makeSubset
        // routes through the Ganesh/Graphite YUV-to-RGB fragment processor
        // and has no CPU-raster equivalent in kanvas-skia.
        //
        // Without LazyYUVImage and the GPU-recorder makeSubset path there
        // is nothing to draw — matches upstream's kSkip on CPU.
        TODO(
            "STUB.LAZY_YUV_IMAGE: yuv_to_rgb_subset_effect requires " +
                "sk_gpu_test::LazyYUVImage (GPU texture upload) + " +
                "SkImage::makeSubset(SkRecorder*, SkIRect, RequiredProperties) — " +
                "no CPU-raster equivalent in kanvas-skia"
        )
    }
}
