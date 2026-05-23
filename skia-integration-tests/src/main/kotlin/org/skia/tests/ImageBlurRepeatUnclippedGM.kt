package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGRAY
import org.graphiks.math.SK_ColorLTGRAY
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SkIRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/imageblurrepeatmode.cpp::DEF_SIMPLE_GM(imageblurrepeatunclipped, …, 256, 128)`.
 *
 * Demonstrates correct repeat-blur behaviour when the canvas is *not* clipped.
 * Before the associated fix (skbug.com/40039025), using `SkTileMode::kRepeat`
 * without an explicit crop rect on `SkImageFilters::Blur` caused three artefacts:
 *  1. Filtered results became semi-transparent when they should have remained opaque.
 *  2. Results were clipped to 3×sigma, which makes sense for `kDecal` but not `kRepeat`.
 *  3. The repeat filter interacted non-intuitively when an expanded clip rect
 *     intersected the draw geometry.
 *
 * The fix: provide the tiling geometry explicitly via `SkImageFilters::Crop` with
 * `SkTileMode::kRepeat`, then pass that as the input to `SkImageFilters::Blur`
 * (without the blur's own repeat tile mode).
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(imageblurrepeatunclipped, canvas, 256, 128) {
 *     auto checkerboard = ToolUtils::create_checkerboard_image(256, 128,
 *                                                              SK_ColorLTGRAY, SK_ColorGRAY, 8);
 *     canvas->drawImage(checkerboard, 0, 0);
 *
 *     SkBitmap bmp;
 *     bmp.allocN32Pixels(100, 20);
 *     bmp.eraseArea(SkIRect::MakeWH(100, 10), SK_ColorRED);
 *     bmp.eraseArea(SkIRect::MakeXYWH(0, 10, 100, 10), SK_ColorBLUE);
 *
 *     auto img = bmp.asImage();
 *     auto filter = SkImageFilters::Blur(0, 10,
 *             SkImageFilters::Crop(SkRect::Make(img->bounds()), SkTileMode::kRepeat, nullptr));
 *     SkPaint paint;
 *     paint.setImageFilter(std::move(filter));
 *
 *     canvas->translate(0, 50);
 *     canvas->save();
 *         canvas->clipIRect(img->bounds().makeOutset(0, 30));
 *         canvas->drawImage(img, 0, 0, SkSamplingOptions(), &paint);
 *     canvas->restore();
 *
 *     canvas->translate(110, 0);
 *     canvas->save();
 *         canvas->clipIRect(SkIRect::MakeXYWH(0, -30, 100, 10));
 *         canvas->drawImage(img, 0, 0, SkSamplingOptions(), &paint);
 *     canvas->restore();
 *
 *     SkPaint line;
 *     line.setStyle(SkPaint::kStroke_Style);
 *     canvas->drawRect(SkRect::MakeXYWH(0, -30, 99, 9), line);
 * }
 * ```
 */
public class ImageBlurRepeatUnclippedGM : GM() {

    override fun getName(): String = "imageblurrepeatunclipped"
    override fun getISize(): SkISize = SkISize.Make(256, 128)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Background checkerboard to reveal translucency.
        val checkerboard = ToolUtils.create_checkerboard_image(256, 128, SK_ColorLTGRAY, SK_ColorGRAY, 8)
        c.drawImage(checkerboard, 0f, 0f)

        // Make an image with one red and one blue band (100 × 20).
        val bmp = SkBitmap(100, 20)
        bmp.eraseArea(SkIRect.MakeWH(100, 10), SK_ColorRED)
        bmp.eraseArea(SkIRect.MakeXYWH(0, 10, 100, 10), SK_ColorBLUE)

        val img = bmp.asImage()

        // Build the filter: Blur(y=10) on top of a kRepeat crop at image bounds.
        // Note: the crop IF is created directly (not via Blur's tileMode overload)
        // because Blur's tileMode factory also adds a kDecal post-crop that is
        // undesired here.
        val imgBoundsRect = SkRect.Make(SkIRect.MakeWH(img.width, img.height))
        val cropFilter = SkImageFilters.Crop(imgBoundsRect, SkTileMode.kRepeat, null)
        val filter = SkImageFilters.Blur(0f, 10f, cropFilter)

        val paint = SkPaint().apply { imageFilter = filter }

        c.translate(0f, 50f)

        // Draw 1: clip shows repeat tiling several times (3×sigma outset).
        c.save()
        c.clipRect(SkRect.Make(SkIRect.MakeWH(img.width, img.height).makeOutset(0, 30)))
        c.drawImage(img, 0f, 0f, SkSamplingOptions.Default, paint)
        c.restore()

        // Draw 2: narrow clip positioned such that the draw would be excluded
        // except that the image filter causes it to intersect the clip.
        c.translate(110f, 0f)
        c.save()
        c.clipRect(SkRect.MakeXYWH(0f, -30f, 100f, 10f))
        c.drawImage(img, 0f, 0f, SkSamplingOptions.Default, paint)
        c.restore()

        // Visualize the clip with a stroke rect.
        val line = SkPaint().apply { style = SkPaint.Style.kStroke_Style }
        c.drawRect(SkRect.MakeXYWH(0f, -30f, 99f, 9f), line)
    }
}
