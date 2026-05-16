package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.math.SK_ColorRED
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * Port of Skia's
 * [`gm/localmatriximagefilter.cpp::DEF_SIMPLE_GM(localmatriximagefilter, …, 640, 640)`](https://github.com/google/skia/blob/main/gm/localmatriximagefilter.cpp).
 *
 * Renders a 100×100 red anti-aliased circle through four filter
 * factories (`Blur`, `Dilate`, `Erode`, `Offset`) and three local
 * matrices (`Scale(0.5)`, `Scale(2)`, `Translate(10, 10)`), giving a
 * 4×4 grid : column 0 is the unwrapped filter, columns 1-3 wrap each
 * matrix via `filter.makeWithLocalMatrix(matrix)` (the API added in
 * R-final.2).
 *
 * Each cell shows :
 *  - a stroked rectangle outlining the source bounds (insets by ½ px
 *    so the rect sits on pixel edges),
 *  - the source image (drawn through the `imageFilter` paint),
 *
 * laid out at `image.width * 1.5` spacing horizontally and vertically.
 *
 * Validates that `SkImageFilter.makeWithLocalMatrix` (R-final.2)
 * pre-concatenates the matrix into the canvas CTM seen by the child
 * filter — the morphology / blur / offset pipelines all read their
 * `ctm` argument inside `filterImage` and react to the augmented
 * scale or translation accordingly.
 *
 * C++ original (from `gm/localmatriximagefilter.cpp`) :
 * ```cpp
 * static sk_sp<SkImage> make_image(SkCanvas* rootCanvas) {
 *     SkImageInfo info = SkImageInfo::MakeN32Premul(100, 100);
 *     auto        surface(ToolUtils::makeSurface(rootCanvas, info));
 *     SkPaint paint;
 *     paint.setAntiAlias(true);
 *     paint.setColor(SK_ColorRED);
 *     surface->getCanvas()->drawCircle(50, 50, 50, paint);
 *     return surface->makeImageSnapshot();
 * }
 *
 * DEF_SIMPLE_GM(localmatriximagefilter, canvas, 640, 640) {
 *     sk_sp<SkImage> image0(make_image(canvas));
 *     const ImageFilterFactory factories[] = {
 *         []{ return SkImageFilters::Blur(8, 8, nullptr); },
 *         []{ return SkImageFilters::Dilate(8, 8, nullptr); },
 *         []{ return SkImageFilters::Erode(8, 8, nullptr); },
 *         []{ return SkImageFilters::Offset(8, 8, nullptr); },
 *     };
 *     const SkMatrix matrices[] = {
 *         SkMatrix::Scale(SK_ScalarHalf, SK_ScalarHalf),
 *         SkMatrix::Scale(2, 2),
 *         SkMatrix::Translate(10, 10),
 *     };
 *     const SkScalar spacer = image0->width() * 3.0f / 2;
 *     canvas->translate(40, 40);
 *     for (auto&& factory : factories) {
 *         sk_sp<SkImageFilter> filter(factory());
 *         canvas->save();
 *         show_image(canvas, image0.get(), filter);
 *         for (const auto& matrix : matrices) {
 *             sk_sp<SkImageFilter> localFilter(filter->makeWithLocalMatrix(matrix));
 *             canvas->translate(spacer, 0);
 *             show_image(canvas, image0.get(), std::move(localFilter));
 *         }
 *         canvas->restore();
 *         canvas->translate(0, spacer);
 *     }
 * }
 * ```
 */
public class LocalMatrixImageFilterGM : GM() {

    override fun getName(): String = "localmatriximagefilter"
    override fun getISize(): SkISize = SkISize.Make(640, 640)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val image0 = makeSourceImage()

        val factories: Array<() -> SkImageFilter?> = arrayOf(
            { SkImageFilters.Blur(8f, 8f, null) },
            { SkImageFilters.Dilate(8, 8, null) },
            { SkImageFilters.Erode(8, 8, null) },
            { SkImageFilters.Offset(8f, 8f, null) },
        )

        val matrices = arrayOf(
            SkMatrix.MakeScale(0.5f, 0.5f),
            SkMatrix.MakeScale(2f, 2f),
            SkMatrix.MakeTrans(10f, 10f),
        )

        val spacer = image0.width * 3f / 2f

        c.translate(40f, 40f)
        for (factory in factories) {
            val filter = factory() ?: continue
            c.save()
            showImage(c, image0, filter)
            for (matrix in matrices) {
                val localFilter = filter.makeWithLocalMatrix(matrix)
                c.translate(spacer, 0f)
                showImage(c, image0, localFilter)
            }
            c.restore()
            c.translate(0f, spacer)
        }
    }

    /**
     * Mirrors upstream's `make_image(rootCanvas)` — paints a single
     * anti-aliased red circle filling a 100×100 raster surface, then
     * snapshots the surface to an [SkImage].
     */
    private fun makeSourceImage(): SkImage {
        val info = SkImageInfo.MakeN32Premul(100, 100)
        val surface = SkSurface.MakeRaster(info)
        val paint = SkPaint().apply {
            isAntiAlias = true
            color = SK_ColorRED
        }
        surface.canvas.drawCircle(50f, 50f, 50f, paint)
        return surface.makeImageSnapshot()
    }

    /**
     * Mirrors upstream's `show_image(canvas, image, filter)` — draws
     * a hairline rectangle outlining the image bounds (inset ½ px to
     * sit on pixel edges) followed by the image itself rendered
     * through the [filter] paint.
     */
    private fun showImage(canvas: SkCanvas, image: SkImage, filter: SkImageFilter?) {
        val paint = SkPaint().apply { style = SkPaint.Style.kStroke_Style }
        // SkRect::MakeIWH(w, h).makeOutset(0.5f, 0.5f) — pixel-edge alignment.
        val r = SkRect.MakeWH(image.width.toFloat(), image.height.toFloat())
        val outset = SkRect.MakeLTRB(r.left - 0.5f, r.top - 0.5f, r.right + 0.5f, r.bottom + 0.5f)
        canvas.drawRect(outset, paint)

        val fillPaint = SkPaint().apply {
            style = SkPaint.Style.kFill_Style
            imageFilter = filter
        }
        canvas.drawImage(image, 0f, 0f, paint = fillPaint)
    }
}
