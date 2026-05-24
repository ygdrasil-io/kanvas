package org.skia.tests

import org.graphiks.math.SkIRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.core.SkCanvas
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkMipmapMode
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.skia.foundation.SkYUVAInfo
import org.skia.gpu.YUVUtils.YUVSubsampling
import org.skia.tools.SkGpuTestUtils
import org.skia.tools.ToolUtils

/**
 * Port of Skia's
 * [`gm/yuv420_odd_dim.cpp::yuv420_odd_dim_repeat`](https://github.com/google/skia/blob/main/gm/yuv420_odd_dim.cpp)
 * (registered as `yuv420_odd_dim_repeat`, 1000 × 500).
 *
 * ## Purpose
 *
 * Regression test for crbug.com/1210557: **subsampled YUV planes weren't
 * repeated at the correct frequency** when used as a `kRepeat`-tiled shader
 * — the U/V planes drifted relative to the Y plane when large translations
 * accumulated, producing visible chroma artefacts.
 *
 * ## Upstream algorithm
 *
 * 1. Load `images/mandrill_256.png` and crop to odd dimensions
 *    (`w & 1 ? w : w-1`, same for height — ensures the chroma planes are
 *    fractional and the bug is exercised).
 * 2. Split into YUV A8 planes via
 *    `sk_gpu_test::MakeYUVAPlanesAsA8(image, kJPEG, k420, nullptr)`.
 * 3. Wrap the planes as `SkYUVAPixmaps::FromExternalPixmaps` and build
 *    a `LazyYUVImage` with `skgpu::Mipmapped::kYes`.
 * 4. Upload the lazy image via `lazyYUV->refImage(recorder/rContext, kFromPixmaps)`.
 * 5. Draw a 2×2 grid (mipmap=None/Linear × filter=Nearest/Linear) where
 *    each cell uses `image->makeShader(kRepeat, kRepeat, sampling)` with a
 *    large translation (-240 000 px) applied to the canvas CTM so that
 *    UV-plane repeat-frequency errors accumulate into a visible colour shift.
 *
 * ## Bucket : INTRACTABLE (GPU-only)
 *
 * Upstream returns `DrawResult::kSkip` when `!rContext && !recorder`. All
 * three missing layers are flagged:
 *  - `STUB.YUVA_PIXMAPS` — `SkGpuTestUtils.MakeYUVAPlanesAsA8` (split
 *    kernel + RGB→YUV matrices not yet wired in `:kanvas-skia`).
 *  - `STUB.LAZY_YUV_IMAGE` — `LazyYUVImage::Make(yuvaPixmaps, Mipmapped::kYes)` +
 *    `refImage(recorder, kFromPixmaps)` — GPU texture upload of YUVA planes.
 *  - `STUB.CANVAS_GPU_SURFACE` — `canvas->recordingContext()` /
 *    `canvas->recorder()` (GPU context guards).
 *
 * As soon as a GPU backend lands in `:kanvas-skia`, the stubs resolve into
 * real call sites and [Yuv420OddDimRepeatTest] can drop its `@Disabled`.
 *
 * Upstream cpp: `gm/yuv420_odd_dim.cpp`, lines 114–194.
 */
public class Yuv420OddDimRepeatGM : GM() {

    private var fImage: SkImage? = null

    override fun getName(): String = "yuv420_odd_dim_repeat"

    override fun getISize(): SkISize = SkISize.Make(1000, 500)

    override fun onOnceBeforeDraw() {
        fImage = ToolUtils.GetResourceAsImage("images/mandrill_256.png")
    }

    /**
     * Mirrors upstream `DEF_SIMPLE_GM_CAN_FAIL(yuv420_odd_dim_repeat, …)`.
     *
     * All GPU-dependent steps are stubbed. The body wires every upstream
     * call site in order so that `grep TODO()` surfaces each gap precisely.
     */
    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Upstream: auto rContext = canvas->recordingContext();
        // Upstream: if (!rContext && !recorder) return DrawResult::kSkip;
        // TODO("STUB.CANVAS_GPU_SURFACE: canvas->recordingContext() / canvas->recorder()")
        // Raster backend has no GPU context — GPU-only GM, skip.

        val srcImage = fImage ?: return

        // Step 1: crop to odd dimensions (mirrors `w & 0b1 ? w : w-1`).
        val w = if (srcImage.width  and 0b1 != 0) srcImage.width  else srcImage.width  - 1
        val h = if (srcImage.height and 0b1 != 0) srcImage.height else srcImage.height - 1
        val image = srcImage.makeSubset(SkIRect.MakeWH(w, h)) ?: return

        // Step 2: split into YUVA A8 planes via MakeYUVAPlanesAsA8.
        // TODO("STUB.YUVA_PIXMAPS: SkGpuTestUtils.MakeYUVAPlanesAsA8")
        // The call site is wired so grep surfaces the gap; the body throws
        // at runtime (NotImplementedError from the TODO stub).
        val planesResult = SkGpuTestUtils.MakeYUVAPlanesAsA8(
            src = image,
            colorSpace = SkYUVAInfo.YUVColorSpace.kJPEG_Full_YUV_ColorSpace,
            subsampling = YUVSubsampling.k420,
        )

        // Step 3: SkYUVAPixmaps::FromExternalPixmaps (mirrors upstream peek).
        // val pixmaps = Array(planesResult.info.numPlanes()) { SkPixmap() }
        // for (i in 0 until planesResult.info.numPlanes()) {
        //     planesResult.planes[i].peekPixels(pixmaps[i])
        // }
        // val yuvaPixmaps = SkYUVAPixmaps.FromExternalPixmaps(planesResult.info, pixmaps)

        // Step 4: LazyYUVImage::Make(yuvaPixmaps, Mipmapped::kYes) + refImage.
        // TODO("STUB.LAZY_YUV_IMAGE: LazyYUVImage::Make(yuvaPixmaps, Mipmapped::kYes)->refImage(context, kFromPixmaps)")
        // val lazyYuv = LazyYUVImage.Make(yuvaPixmaps, skgpu.Mipmapped.kYes)
        // val yuvImage = lazyYuv?.refImage(recorder ?: rContext, LazyYUVImage.Type.kFromPixmaps)
        //     ?: run { errMsg = "Could not make YUVA image"; return DrawResult.kFail }
        val yuvImage: org.skia.foundation.SkImage? = null   // GPU-only — always null here.
        if (yuvImage == null) {
            // Mirrors upstream "Could not make YUVA image" fail guard (raster: always hits).
            return
        }

        // Step 5: 2×2 grid — mipmap × filter, with large-translation kRepeat shader.
        var i = 0
        for (mm in listOf(SkMipmapMode.kNone, SkMipmapMode.kLinear)) {
            var j = 0
            for (filter in listOf(SkFilterMode.kNearest, SkFilterMode.kLinear)) {
                c.save()
                c.clipRect(SkRect.MakeXYWH(500f * j, 250f * i, 500f, 250f))
                c.rotate(30f)
                c.scale(0.4f, 0.4f)
                // Large translation accumulates UV-plane repeat-frequency error.
                c.translate(-240000f, -240000f)
                val shader = yuvImage.makeShader(
                    tileX = SkTileMode.kRepeat,
                    tileY = SkTileMode.kRepeat,
                    sampling = SkSamplingOptions(filter, mm),
                )
                val paint = SkPaint()
                paint.shader = shader
                c.drawPaint(paint)
                c.restore()
                ++j
            }
            ++i
        }
    }
}
