package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ScalarPI
import org.graphiks.math.SkISize
import org.graphiks.math.SkM44
import org.graphiks.math.SkRect
import org.graphiks.math.SkV3
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkFont
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `DEF_SIMPLE_GM(imagefilter_transformed_image, canvas, 256, 256)`
 * in `gm/imagefilterstransformed.cpp` (registered name `"imagefilter_transformed_image"`,
 * 256 × 256).
 *
 * Tests [SkImageFilters.Image] under tricky matrices (mirrors and perspective).
 * For each of two matrices (mirror + perspective) draws two columns that should match:
 *  - left column  : canvas transform applied via `canvas.concat(m)`, no filter transform
 *  - right column : filter wraps the matrix via `MatrixTransform(m.asM33(), ...)`
 *
 * C++ original (collapsed):
 * ```cpp
 * DEF_SIMPLE_GM(imagefilter_transformed_image, canvas, 256, 256) {
 *     sk_sp<SkImage> image = ToolUtils::GetResourceAsImage("images/color_wheel.png");
 *     sk_sp<SkImageFilter> imageFilter = SkImageFilters::Image(image, SkFilterMode::kLinear);
 *     const SkRect imageRect = SkRect::MakeIWH(image->width(), image->height());
 *     SkM44 m1 = SkM44::Translate(0.9f * image->width(), 0.1f * image->height()) *
 *                SkM44::Scale(-.8f, .8f);
 *     SkM44 m2 = SkM44::RectToRect({-1,-1,1,1}, imageRect) *
 *                SkM44::Perspective(0.01f, 100.f, SK_ScalarPI / 3.f) *
 *                SkM44::Translate(0.f, 0.f, -2.f) *
 *                SkM44::Rotate({0,1,0}, SK_ScalarPI / 6.f) *
 *                SkM44::RectToRect(imageRect, {-1,-1,1,1});
 *     canvas->drawString("Columns should match", 5.f, 15.f, font, SkPaint());
 *     canvas->translate(0.f, 10.f);
 *     SkSamplingOptions sampling(SkFilterMode::kLinear);
 *     for (auto m : {m1, m2}) {
 *         canvas->save();
 *         for (bool canvasTransform : {false, true}) {
 *             canvas->save(); canvas->clipRect(imageRect);
 *             sk_sp<SkImageFilter> finalFilter;
 *             if (canvasTransform) { canvas->concat(m); finalFilter = imageFilter; }
 *             else { finalFilter = SkImageFilters::MatrixTransform(m.asM33(), sampling, imageFilter); }
 *             SkPaint paint; paint.setImageFilter(std::move(finalFilter));
 *             canvas->drawPaint(paint);
 *             canvas->restore();
 *             canvas->translate(image->width(), 0.f);
 *         }
 *         canvas->restore();
 *         canvas->translate(0.f, image->height());
 *     }
 * }
 * ```
 */
public class ImageFilterTransformedImageGM : GM() {

    private var fImage: SkImage? = null

    override fun getName(): String = "imagefilter_transformed_image"
    override fun getISize(): SkISize = SkISize.Make(256, 256)

    override fun onOnceBeforeDraw() {
        fImage = ToolUtils.GetResourceAsImage("images/color_wheel.png")
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val image = fImage ?: return

        val imageFilter = SkImageFilters.Image(image, SkSamplingOptions(SkFilterMode.kLinear))
        val imageRect = SkRect.MakeIWH(image.width, image.height)
        val w = image.width.toFloat()
        val h = image.height.toFloat()

        // m1 : mirror (flip-x with translate so it stays in view)
        val m1 = SkM44.translate(0.9f * w, 0.1f * h) *
                 SkM44.scale(-0.8f, 0.8f)

        // m2 : perspective rotation around Y axis
        val m2 = SkM44.rectToRect(SkRect.MakeLTRB(-1f, -1f, 1f, 1f), imageRect) *
                 SkM44.perspective(0.01f, 100f, SK_ScalarPI / 3f) *
                 SkM44.translate(0f, 0f, -2f) *
                 SkM44.rotate(SkV3(0f, 1f, 0f), SK_ScalarPI / 6f) *
                 SkM44.rectToRect(imageRect, SkRect.MakeLTRB(-1f, -1f, 1f, 1f))

        val font: SkFont = ToolUtils.DefaultPortableFont()
        c.drawString("Columns should match", 5f, 15f, font, SkPaint())
        c.translate(0f, 10f)

        val sampling = SkSamplingOptions(SkFilterMode.kLinear)
        for (m in listOf(m1, m2)) {
            c.save()
            for (canvasTransform in listOf(false, true)) {
                c.save()
                c.clipRect(imageRect)

                val finalFilter = if (canvasTransform) {
                    c.concat(m)
                    imageFilter
                } else {
                    val m33 = m.asM33()
                    if (m33 != null) {
                        SkImageFilters.MatrixTransform(m33, sampling, imageFilter)
                    } else {
                        imageFilter
                    }
                }

                val paint = SkPaint()
                paint.imageFilter = finalFilter
                c.drawPaint(paint)
                c.restore()
                c.translate(w, 0f)
            }
            c.restore()
            c.translate(0f, h)
        }
    }
}
