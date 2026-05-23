package org.skia.tests

import org.graphiks.math.SkIRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.core.SkCanvas
import org.skia.core.SrcRectConstraint
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkImages
import org.skia.foundation.SkMipmapMode
import org.skia.foundation.SkPixmap
import org.skia.foundation.SkSamplingOptions
import org.skia.tools.ToolUtils

/**
 * Port of Skia's
 * [`gm/crosscontextimage.cpp`](https://github.com/google/skia/blob/main/gm/crosscontextimage.cpp)
 * — exercises cross-context GPU texture images (Ganesh `GrDirectContext`
 * upload path).
 *
 * Upstream (`DEF_SIMPLE_GPU_GM_CAN_FAIL`, 3×256+40 × 256+128+30) builds
 * three [SkImage] variants from a single decoded `mandrill_256.png` source:
 *
 *  | index | factory                                             | notes                       |
 *  |-------|-----------------------------------------------------|-----------------------------|
 *  |   0   | `SkImages::DeferredFromEncodedData`                 | lazy raster decode          |
 *  |   1   | `SkImages::CrossContextTextureFromPixmap(…, false)` | GPU texture, no mip-maps    |
 *  |   2   | `SkImages::CrossContextTextureFromPixmap(…, true)`  | GPU texture, with mip-maps  |
 *
 * For each image the GM draws three variants in a column:
 *  - full image at top-left (`drawImage`),
 *  - 128×128 subset starting at (64,64) (`makeSubset` + `drawImage`),
 *  - full image scaled to 128×128 with bilinear + mip-map sampling
 *    (`drawImageRect` with `SkFilterMode::kLinear / SkMipmapMode::kLinear`).
 *
 * ## Port status — **INTRACTABLE.GPU_ONLY**
 *
 * `SkImages::CrossContextTextureFromPixmap` requires a live `GrDirectContext`
 * (Ganesh GPU context). The upstream `DEF_SIMPLE_GPU_GM_CAN_FAIL` macro
 * skips the GM entirely when no direct context is available (returns
 * `DrawResult::kSkip`). `:kanvas-skia` is a raster-only backend — there is
 * no `GrDirectContext`, no GPU texture upload pipeline, and no semaphore /
 * cross-context sync primitive.
 *
 * [SkImages.CrossContextTextureFromPixmap] is therefore a
 * `TODO("STUB.CROSS_CONTEXT_IMAGE")` in the live [SkImages] object. The
 * [onDraw] body **does** call it (the body is a faithful upstream port) and
 * will throw [NotImplementedError] at runtime, which is the intended
 * behaviour: [CrossContextImageTest] is `@Disabled("STUB.CROSS_CONTEXT_IMAGE")`.
 *
 * Upstream C++ (`gm/crosscontextimage.cpp`):
 * ```cpp
 * DEF_SIMPLE_GPU_GM_CAN_FAIL(cross_context_image, rContext, canvas, errorMsg,
 *                            3 * 256 + 40, 256 + 128 + 30) {
 *     sk_sp<SkData> encodedData = GetResourceAsData("images/mandrill_256.png");
 *     auto dContext = rContext->asDirectContext();
 *     sk_sp<SkImage> images[3];
 *     images[0] = SkImages::DeferredFromEncodedData(encodedData);
 *     SkBitmap bmp; SkPixmap pixmap;
 *     SkAssertResult(images[0]->asLegacyBitmap(&bmp) && bmp.peekPixels(&pixmap));
 *     images[1] = SkImages::CrossContextTextureFromPixmap(dContext, pixmap, false);
 *     images[2] = SkImages::CrossContextTextureFromPixmap(dContext, pixmap, true);
 *     canvas->translate(10, 10);
 *     for (size_t i = 0; i < std::size(images); ++i) {
 *         canvas->save();
 *         canvas->drawImage(images[i], 0, 0);
 *         canvas->translate(0, 256 + 10);
 *         auto subset = images[i]->makeSubset(
 *                 dContext->asRecorder(), SkIRect::MakeXYWH(64, 64, 128, 128), {});
 *         canvas->drawImage(subset, 0, 0);
 *         canvas->translate(128, 0);
 *         canvas->drawImageRect(images[i], SkRect::MakeWH(128, 128),
 *                               SkSamplingOptions(SkFilterMode::kLinear,
 *                                                 SkMipmapMode::kLinear));
 *         canvas->restore();
 *         canvas->translate(256 + 10, 0);
 *     }
 * }
 * ```
 */
public class CrossContextImageGM : GM() {

    override fun getName(): String = "cross_context_image"
    override fun getISize(): SkISize = SkISize.Make(3 * 256 + 40, 256 + 128 + 30)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Load the source image from resources (mirrors upstream's
        // `SkImages::DeferredFromEncodedData(GetResourceAsData(…))`).
        val image0: SkImage = ToolUtils.GetResourceAsImage("images/mandrill_256.png") ?: return

        // Extract a pixmap from the decoded image so we can pass it to the
        // CrossContextTextureFromPixmap factories (mirrors upstream's
        // `asLegacyBitmap` + `peekPixels` dance).
        val bmp = SkBitmap(image0.width, image0.height)
        for (y in 0 until image0.height) {
            for (x in 0 until image0.width) {
                bmp.setPixel(x, y, image0.peekPixel(x, y))
            }
        }
        val pixmap = SkPixmap()
        bmp.peekPixels(pixmap)

        // images[1]: GPU cross-context texture, no mip-maps.
        // images[2]: GPU cross-context texture, with mip-maps.
        // Both call TODO("STUB.CROSS_CONTEXT_IMAGE") — this GM is @Disabled.
        val image1: SkImage? = SkImages.CrossContextTextureFromPixmap(pixmap, false)
        val image2: SkImage? = SkImages.CrossContextTextureFromPixmap(pixmap, true)

        val images: Array<SkImage?> = arrayOf(image0, image1, image2)

        c.translate(10f, 10f)

        for (image in images) {
            if (image == null) continue
            c.save()

            // Row 1: full image.
            c.drawImage(image, 0f, 0f)
            c.translate(0f, 256f + 10f)

            // Row 2 left: 128×128 subset from (64,64).
            val subset = image.makeSubset(SkIRect.MakeXYWH(64, 64, 128, 128))
            if (subset != null) {
                c.drawImage(subset, 0f, 0f)
            }
            c.translate(128f, 0f)

            // Row 2 right: full image scaled to 128×128, bilinear + mip-linear.
            // Upstream calls drawImageRect(image, SkRect::MakeWH(128, 128), SkSamplingOptions(…))
            // with no src rect — supply the full image bounds to match Skia's no-src-rect behaviour.
            c.drawImageRect(
                image,
                SkRect.MakeIWH(image.width, image.height),
                SkRect.MakeWH(128f, 128f),
                SkSamplingOptions(SkFilterMode.kLinear, SkMipmapMode.kLinear),
                null,
                SrcRectConstraint.kFast,
            )

            c.restore()
            c.translate(256f + 10f, 0f)
        }
    }
}
