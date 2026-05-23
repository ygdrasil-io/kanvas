package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint

/**
 * Port of Skia's `DEF_SIMPLE_GM(rotate_imagefilter, canvas, 500, 500)`
 * in `gm/imagefilterstransformed.cpp` (registered name `"rotate_imagefilter"`,
 * 500 × 500).
 *
 * Draws 3 rows of 3 rects each, cycling through filters
 * (null, Blur(6,0), Blend(SrcOver)), showing:
 *  - a plain rect at left
 *  - a 30° rotated rect at centre (antiAlias=false)
 *  - a 30° rotated rect at right (antiAlias=true)
 *
 * C++ original (collapsed):
 * ```cpp
 * DEF_SIMPLE_GM(rotate_imagefilter, canvas, 500, 500) {
 *     SkPaint paint;
 *     const SkRect r = SkRect::MakeXYWH(50, 50, 100, 100);
 *     sk_sp<SkImageFilter> filters[] = {
 *         nullptr,
 *         SkImageFilters::Blur(6, 0, nullptr),
 *         SkImageFilters::Blend(SkBlendMode::kSrcOver, nullptr),
 *     };
 *     for (auto& filter : filters) {
 *         paint.setAntiAlias(false); paint.setImageFilter(filter);
 *         canvas->save();
 *         canvas->drawRect(r, paint);
 *         canvas->translate(150, 0); canvas->save();
 *         canvas->rotate(30, 100, 100); canvas->drawRect(r, paint); canvas->restore();
 *         paint.setAntiAlias(true);
 *         canvas->translate(150, 0); canvas->save();
 *         canvas->rotate(30, 100, 100); canvas->drawRect(r, paint); canvas->restore();
 *         canvas->restore();
 *         canvas->translate(0, 150);
 *     }
 * }
 * ```
 */
public class RotateImageFilterGM : GM() {

    override fun getName(): String = "rotate_imagefilter"
    override fun getISize(): SkISize = SkISize.Make(500, 500)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val r = SkRect.MakeXYWH(50f, 50f, 100f, 100f)

        val filters = listOf(
            null,
            SkImageFilters.Blur(6f, 0f, null),
            SkImageFilters.Blend(SkBlendMode.kSrcOver),
        )

        for (filter in filters) {
            val paint = SkPaint().apply {
                isAntiAlias = false
                imageFilter = filter
            }

            c.save()

            // left: plain rect
            c.drawRect(r, paint)

            // centre: rotated, no AA
            c.translate(150f, 0f)
            c.save()
            c.rotate(30f, 100f, 100f)
            c.drawRect(r, paint)
            c.restore()

            // right: rotated, AA
            paint.isAntiAlias = true
            c.translate(150f, 0f)
            c.save()
            c.rotate(30f, 100f, 100f)
            c.drawRect(r, paint)
            c.restore()

            c.restore()
            c.translate(0f, 150f)
        }
    }
}
