package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkIRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/imageblurclampmode.cpp::ImageBlurClampModeGM`.
 *
 * Draws an offscreen 250×200 source image (blue rect with an inscribed
 * green circle and a red 80×80 corner square), then for each
 * `sigma ∈ {0.6, 3.0, 8.0, 20.0}` renders 3 columns of blurred image
 * draws (x-only, y-only, both axes) using a `kClamp` tile mode on the
 * blur image filter.
 *
 * C++ original:
 * ```cpp
 * class ImageBlurClampModeGM : public GM {
 * public:
 *     ImageBlurClampModeGM() { this->setBGColor(0xFFCCCCCC); }
 *     SkString getName() const override { return SkString("imageblurclampmode"); }
 *     SkISize getISize() override { return SkISize::Make(850, 920); }
 *     void onDraw(SkCanvas* canvas) override {
 *         sk_sp<SkImage> image(make_image(canvas));
 *         sk_sp<SkImageFilter> filter;
 *         canvas->translate(0, 30);
 *         for (auto sigma: { 0.6f, 3.0f, 8.0f, 20.0f }) {
 *             canvas->save();
 *             // x-only / y-only / both
 *             ...
 *             canvas->restore();
 *             canvas->translate(0, image->height() + 20);
 *         }
 *     }
 * };
 * ```
 *
 * Helper `draw_image`:
 * ```cpp
 * static void draw_image(SkCanvas* canvas, const sk_sp<SkImage> image,
 *                        sk_sp<SkImageFilter> filter) {
 *     SkAutoCanvasRestore acr(canvas, true);
 *     SkPaint paint;
 *     paint.setImageFilter(std::move(filter));
 *     canvas->translate(SkIntToScalar(30), 0);
 *     canvas->clipIRect(image->bounds());
 *     canvas->drawImage(image, 0, 0, SkSamplingOptions(), &paint);
 * }
 * ```
 *
 * `:kanvas-skia` has no `clipIRect`, so we use `clipRect` (non-AA),
 * which snaps to integer bounds — same effect.
 */
public class ImageBlurClampModeGM : GM() {

    init {
        setBGColor(SkColorSetARGB(0xFF, 0xCC, 0xCC, 0xCC))
    }

    override fun getName(): String = "imageblurclampmode"
    override fun getISize(): SkISize = SkISize.Make(850, 920)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val image = makeImage()

        c.translate(0f, 30f)
        val sigmas = floatArrayOf(0.6f, 3.0f, 8.0f, 20.0f)
        val imageBounds = SkIRect.MakeWH(image.width, image.height)
        for (sigma in sigmas) {
            c.save()

            // x-only blur
            drawImage(c, image, SkImageFilters.Blur(sigma, 0.0f, SkTileMode.kClamp, null, imageBounds))
            c.translate(image.width + 20f, 0f)

            // y-only blur
            drawImage(c, image, SkImageFilters.Blur(0.0f, sigma, SkTileMode.kClamp, null, imageBounds))
            c.translate(image.width + 20f, 0f)

            // both directions
            drawImage(c, image, SkImageFilters.Blur(sigma, sigma, SkTileMode.kClamp, null, imageBounds))
            c.translate(image.width + 20f, 0f)

            c.restore()
            c.translate(0f, image.height + 20f)
        }
    }

    private fun drawImage(canvas: SkCanvas, image: SkImage, filter: SkImageFilter?) {
        canvas.save()
        try {
            val paint = SkPaint().apply { imageFilter = filter }
            canvas.translate(30f, 0f)
            canvas.clipRect(SkRect.MakeIWH(image.width, image.height))
            canvas.drawImage(image, 0f, 0f, SkSamplingOptions.Default, paint)
        } finally {
            canvas.restore()
        }
    }

    private fun makeImage(): SkImage {
        val info = SkImageInfo.MakeN32Premul(250, 200)
        val surface = SkSurface.MakeRaster(info)
        val sc = surface.canvas
        val paint = SkPaint().apply { isAntiAlias = true }

        paint.color = SK_ColorBLUE
        sc.drawRect(SkRect.MakeIWH(info.width, info.height), paint)
        paint.color = SK_ColorGREEN
        sc.drawCircle(125f, 100f, 100f, paint)
        paint.color = SK_ColorRED
        sc.drawRect(SkRect.MakeIWH(80, 80), paint)

        return surface.makeImageSnapshot()
    }
}
