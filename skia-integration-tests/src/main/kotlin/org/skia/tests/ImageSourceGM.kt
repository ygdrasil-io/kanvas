package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.math.SK_ColorBLACK
import org.skia.foundation.SkBitmap
import org.skia.math.SkColor
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkFont
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/imagesource.cpp::ImageSourceGM` — exercises
 * [SkImageFilters.Image] in its four call shapes :
 *
 *  1. Unscaled bitmap (full bounds → full bounds, `kNearest`).
 *  2. Unscaled subset (`srcRect → srcRect`, cubic sampler).
 *  3. Scaled subset (`srcRect → dstRect`, cubic).
 *  4. Full bitmap scaled (`bounds → dstRect`, cubic).
 *
 * Each filter is set on a `paint.imageFilter`, then `drawPaint` is
 * routed through a `clipRect(0, 0, 100, 100)` so the filter's source
 * pixels fill that 100×100 region. The four panels translate by 100
 * each, yielding a 500×100-style banner of variations on the 100×100
 * source image.
 *
 * **Source image** : upstream uses `ToolUtils::CreateStringImage(100, 100,
 * 0xFFFFFFFF, 20, 70, 96, "e")` — a 100×100 N32-premul bitmap cleared
 * to transparent, then a single white "e" drawn at (20, 70) with a
 * size-96 portable-typeface font. We inline that helper here as
 * [makeStringImage] since `:kanvas-skia` doesn't yet expose
 * `CreateStringImage` on [ToolUtils].
 *
 * C++ original:
 * ```cpp
 * SkString getName() const override { return SkString("imagesource"); }
 * SkISize getISize() override { return SkISize::Make(500, 150); }
 *
 * void onOnceBeforeDraw() override {
 *     fImage = ToolUtils::CreateStringImage(100, 100, 0xFFFFFFFF, 20, 70, 96, "e");
 * }
 *
 * void onDraw(SkCanvas* canvas) override {
 *     canvas->clear(SK_ColorBLACK);
 *
 *     const SkRect srcRect = SkRect::MakeXYWH(20, 20, 30, 30);
 *     const SkRect dstRect = SkRect::MakeXYWH(0, 10, 60, 60);
 *     const SkRect clipRect = SkRect::MakeXYWH(0, 0, 100, 100);
 *     const SkRect bounds = SkRect::MakeIWH(fImage->width(), fImage->height());
 *     const SkSamplingOptions sampling({1/3.0f, 1/3.0f});
 *
 *     // panel 1: SkImageFilters::Image(fImage, SkFilterMode::kNearest)
 *     // panel 2: SkImageFilters::Image(fImage, srcRect, srcRect, sampling)
 *     // panel 3: SkImageFilters::Image(fImage, srcRect, dstRect, sampling)
 *     // panel 4: SkImageFilters::Image(fImage, bounds, dstRect, sampling)
 *     // each panel: clipRect(clipRect); paint{imageFilter=...}; drawPaint;
 *     //             translate(100, 0).
 * }
 * ```
 */
public class ImageSourceGM : GM() {

    private lateinit var fImage: SkImage

    override fun getName(): String = "imagesource"
    override fun getISize(): SkISize = SkISize.Make(500, 150)

    override fun onOnceBeforeDraw() {
        fImage = makeStringImage(
            w = 100, h = 100, color = 0xFFFFFFFF.toInt(),
            x = 20, y = 70, textSize = 96, str = "e",
        )
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.clear(SK_ColorBLACK)

        val srcRect = SkRect.MakeXYWH(20f, 20f, 30f, 30f)
        val dstRect = SkRect.MakeXYWH(0f, 10f, 60f, 60f)
        val clipRect = SkRect.MakeXYWH(0f, 0f, 100f, 100f)
        val bounds = SkRect.MakeWH(fImage.width.toFloat(), fImage.height.toFloat())
        // Upstream `SkSamplingOptions({1/3.0f, 1/3.0f})` is a Mitchell
        // cubic at `(B, C) = (1/3, 1/3)`. Our SkSamplingOptions accepts
        // an SkCubicResampler directly.
        val sampling = SkSamplingOptions(org.skia.foundation.SkCubicResampler(1f / 3f, 1f / 3f))

        // Panel 1 — full image, nearest sampler.
        fillRectFiltered(
            c, clipRect,
            SkImageFilters.Image(fImage, SkSamplingOptions(SkFilterMode.kNearest)),
        )
        c.translate(100f, 0f)

        // Panel 2 — subset → subset (no scale), cubic.
        fillRectFiltered(
            c, clipRect,
            SkImageFilters.Image(fImage, srcRect, srcRect, sampling),
        )
        c.translate(100f, 0f)

        // Panel 3 — subset scaled to dstRect, cubic.
        fillRectFiltered(
            c, clipRect,
            SkImageFilters.Image(fImage, srcRect, dstRect, sampling),
        )
        c.translate(100f, 0f)

        // Panel 4 — full image scaled to dstRect, cubic.
        fillRectFiltered(
            c, clipRect,
            SkImageFilters.Image(fImage, bounds, dstRect, sampling),
        )
        c.translate(100f, 0f)
    }

    /**
     * Mirrors `fill_rect_filtered(canvas, clipRect, filter)` — wrap the
     * filter on a paint, clip to [clipRect], and `drawPaint` so the
     * filter sources pixels for the clipped region.
     */
    private fun fillRectFiltered(c: SkCanvas, clipRect: SkRect, filter: SkImageFilter?) {
        val paint = SkPaint().apply { imageFilter = filter }
        c.save()
        c.clipRect(clipRect)
        c.drawPaint(paint)
        c.restore()
    }

    /**
     * Mirrors `ToolUtils::CreateStringImage(w, h, color, x, y, textSize,
     * str)` (`tools/fonts/FontToolUtils.cpp:267`) — allocate an
     * N32-premul bitmap, draw a single string at `(x, y)` with a
     * portable-typeface font of [textSize], snapshot to an image.
     */
    private fun makeStringImage(
        w: Int, h: Int, color: SkColor,
        x: Int, y: Int, textSize: Int, str: String,
    ): SkImage {
        val info = SkImageInfo.MakeN32Premul(w, h)
        val surface = SkSurface.MakeRaster(info)
        val canvas = surface.canvas

        canvas.clear(0x00000000)

        val paint = SkPaint().apply { this.color = color }
        val font = SkFont(ToolUtils.DefaultPortableTypeface(), textSize.toFloat())
        canvas.drawString(str, x.toFloat(), y.toFloat(), font, paint)

        return surface.makeImageSnapshot()
    }
}
