package org.skia.tests

import org.graphiks.math.SkISize
import org.skia.core.SkCanvas
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkSurfaces
import org.skia.tools.SkRandom

/**
 * Port of Skia's
 * [`gm/yuv420_odd_dim.cpp::yuv420_odd_dim`](https://github.com/google/skia/blob/main/gm/yuv420_odd_dim.cpp)
 * (registered as `yuv420_odd_dim`, 50 × 50; `kScale=10`, `kImageDim={5,5}`).
 *
 * ## Purpose
 *
 * Tests that the GPU YUV image code path handles **odd-sized images with
 * 4:2:0 chroma subsampling** correctly (skbug.com reference in upstream).
 *
 * ## Upstream algorithm
 *
 * 1. Allocate a 5×5 RGBA bitmap filled with random opaque pixel values
 *    (via `SkRandom`).
 * 2. JPEG-encode it with `JpegEncoder::Downsample::k420` (4:2:0) —
 *    this is the key step that creates a YUV420-encoded stream with odd
 *    chroma-plane dimensions.
 * 3. Decode and upload via `sk_gpu_test::LazyYUVImage::Make(data)` +
 *    `imageHelper->refImage(rContext/recorder, kFromPixmaps)` — the
 *    GPU YUV upload path under test.
 * 4. Draw the image to a same-sized offscreen surface, then scale that
 *    snapshot up by `kScale=10` (nearest-neighbour) onto the main canvas.
 *
 * ## Bucket : INTRACTABLE (GPU-only)
 *
 * Upstream explicitly returns `DrawResult::kSkip` when `!rContext && !recorder`
 * — this GM exists **solely** to exercise the GPU YUV planar image upload
 * path. `:kanvas-skia` is a raster backend; there is no equivalent of
 * `LazyYUVImage::refImage(GrRecordingContext*, ...)` or
 * `LazyYUVImage::refImage(Recorder*, ...)`.
 *
 * The [onDraw] body below wires every upstream call site so that
 * `grep TODO()` surfaces the exact gaps:
 *  - `STUB.LAZY_YUV_IMAGE` — `sk_gpu_test::LazyYUVImage::Make(SkData*)` +
 *    `refImage(context, Type::kFromPixmaps)` — GPU texture upload from
 *    JPEG data. No raster equivalent.
 *  - `STUB.CANVAS_GPU_SURFACE` — `canvas->getSurface()` (GPU surface check)
 *    / `canvas->recordingContext()` (Ganesh context handle).
 *
 * As soon as a GPU backend lands in `:kanvas-skia`, these stubs can be
 * replaced with real call sites and [Yuv420OddDimTest] can drop its
 * `@Disabled`.
 *
 * Upstream cpp: `gm/yuv420_odd_dim.cpp`, lines 34–112.
 */
public class Yuv420OddDimGM : GM() {

    companion object {
        private const val K_SCALE = 10
        private val K_IMAGE_DIM = SkISize.Make(5, 5)
    }

    override fun getName(): String = "yuv420_odd_dim"

    override fun getISize(): SkISize =
        SkISize.Make(K_SCALE * K_IMAGE_DIM.width, K_SCALE * K_IMAGE_DIM.height)

    /**
     * Mirrors upstream's `make_image(GrRecordingContext*, Recorder*)`.
     *
     * Fills a 5×5 RGBA bitmap with random opaque colours (same seed as
     * upstream's `SkRandom random`), then JPEG-encodes it with 4:2:0
     * downsampling.
     *
     * The final step — `LazyYUVImage::Make(data)->refImage(context, kFromPixmaps)` —
     * is **`TODO("STUB.LAZY_YUV_IMAGE")`** because uploading a decoded JPEG
     * as a GPU-backed YUV planar image requires a `GrRecordingContext` /
     * Graphite `Recorder`, neither of which exist in the raster backend.
     *
     * Returns `null` to indicate the GPU resource is unavailable, consistent
     * with upstream's null-image guard in `onDraw` when the GPU context has
     * been abandoned.
     */
    private fun makeImage(): SkImage? {
        // Step 1: fill a 5×5 bitmap with random opaque pixels (mirrors upstream).
        val bmp = SkBitmap(K_IMAGE_DIM.width, K_IMAGE_DIM.height)
        val random = SkRandom()
        for (y in 0 until bmp.height) {
            for (x in 0 until bmp.width) {
                val next = random.nextU() or 0xFF000000.toInt()
                bmp.setPixel(x, y, next)
            }
        }

        // Step 2: JPEG-encode with k420 downsampling (mirrors upstream
        // `JpegEncoder::Options{.fDownsample=k420, .fQuality=100}`).
        // JpegEncoder exists in :kanvas-skia but encodes via ImageIO which
        // always honours standard subsampling — the resulting byte stream is
        // a valid JPEG-with-YUV420.
        // (Encoding step succeeds on the raster side; the GPU upload below is
        //  the actual blocker.)
        // val options = JpegEncoder.Options(quality = 100, downsample = JpegEncoder.Downsample.k420)
        // val data: ByteArray? = JpegEncoder.encode(bmp, options)
        // if (data == null) return null

        // Step 3: GPU upload via LazyYUVImage — raster backend has no equivalent.
        // TODO("STUB.LAZY_YUV_IMAGE: sk_gpu_test::LazyYUVImage::Make(data)->refImage(rContext, kFromPixmaps)")
        // val imageHelper = LazyYUVImage.Make(data)
        // return imageHelper?.refImage(rContext, LazyYUVImage.Type.kFromPixmaps)

        // In the raster backend there is no GPU context — return null so
        // onDraw follows the same kSkip early-out path as upstream.
        return null
    }

    /**
     * Mirrors upstream `DEF_SIMPLE_GM_CAN_FAIL(yuv420_odd_dim, …)`.
     *
     * Upstream returns `DrawResult::kSkip` immediately when there is no
     * GPU recording context (`!rContext && !recorder`). The raster backend
     * always falls into that branch, so [onDraw] is effectively a no-op.
     * The body is preserved verbatim (with stubs) so the structure stays
     * auditable.
     */
    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Upstream: auto rContext = canvas->recordingContext();
        // Upstream: if (!rContext && !recorder) return DrawResult::kSkip;
        // TODO("STUB.CANVAS_GPU_SURFACE: canvas->recordingContext() / canvas->recorder()")
        // Raster backend has no recording context — GPU-only GM, skip.
        val image: SkImage? = makeImage()
        if (image == null) {
            // Mirrors upstream's abandoned-context / failed-image guard.
            // In the raster backend makeImage() always returns null.
            return
        }

        // Upstream creates an offscreen surface matching the image dimensions.
        // If `canvas->getSurface()` returns non-null (GPU canvas), use
        // `origSurface->makeSurface(image->width(), image->height())`.
        // Otherwise fall back to a raster surface via `SkSurfaces::Raster`.
        val info = SkImageInfo.Make(image.width, image.height, SkColorType.kRGBA_8888, SkAlphaType.kPremul)
        val surface = SkSurfaces.Raster(info) ?: return
        // TODO("STUB.CANVAS_GPU_SURFACE: origSurface->makeSurface(w,h) for GPU canvas")

        // Draw the YUV image onto the offscreen surface.
        surface.canvas.drawImage(image, 0f, 0f)

        // Scale up by kScale using nearest-neighbour (upstream: canvas->scale(kScale, kScale)).
        c.scale(K_SCALE.toFloat(), K_SCALE.toFloat())
        c.drawImage(surface.makeImageSnapshot(), 0f, 0f)
    }
}
