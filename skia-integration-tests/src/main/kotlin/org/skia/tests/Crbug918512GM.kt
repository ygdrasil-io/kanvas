package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorCYAN
import org.graphiks.math.SK_ColorGRAY
import org.graphiks.math.SK_ColorTRANSPARENT
import org.graphiks.math.SK_ColorYELLOW
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkLumaColorFilter
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/crbug_918512.cpp` (`DEF_SIMPLE_GM(crbug_918512, ...)`).
 *
 * Reduced from a Chromium PDF-backend regression: stacks two nested
 * `saveLayer`s with the inner layer carrying a `kDstIn` blend + luma
 * colour filter, then fills the inner layer's left half with grey. The
 * GM exercises the layer-restore composite path where the layer paint's
 * `colorFilter` must be applied to the source samples before blending
 * into the parent — Phase G7 wired up `compositeFrom` so that filter
 * runs at layer-restore time.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(crbug_918512, canvas, 256, 256) {
 *     canvas->drawColor(SK_ColorYELLOW);
 *     {
 *         SkAutoCanvasRestore autoCanvasRestore1(canvas, false);
 *         canvas->saveLayer(nullptr, nullptr);
 *         canvas->drawColor(SK_ColorCYAN);
 *         {
 *             SkAutoCanvasRestore autoCanvasRestore2(canvas, false);
 *             SkPaint lumaFilter;
 *             lumaFilter.setBlendMode(SkBlendMode::kDstIn);
 *             lumaFilter.setColorFilter(SkLumaColorFilter::Make());
 *             canvas->saveLayer(nullptr, &lumaFilter);
 *
 *             canvas->drawColor(SK_ColorTRANSPARENT);
 *             SkPaint paint;
 *             paint.setColor(SK_ColorGRAY);
 *             canvas->drawRect(SkRect{0, 0, 128, 256}, paint);
 *         }
 *     }
 * }
 * ```
 */
public class Crbug918512GM : GM() {
    override fun getName(): String = "crbug_918512"
    override fun getISize(): SkISize = SkISize.Make(256, 256)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.drawColor(SK_ColorYELLOW)

        c.saveLayer(null, null)
        c.drawColor(SK_ColorCYAN)

        val lumaFilter = SkPaint().apply {
            blendMode = SkBlendMode.kDstIn
            colorFilter = SkLumaColorFilter.Make()
        }
        c.saveLayer(null, lumaFilter)

        c.drawColor(SK_ColorTRANSPARENT)
        val paint = SkPaint().apply { color = SK_ColorGRAY }
        c.drawRect(SkRect.MakeLTRB(0f, 0f, 128f, 256f), paint)

        c.restore()
        c.restore()
    }
}
