package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorGREEN
import org.skia.math.SK_ColorRED
import org.skia.math.SkColor
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/crbug_1156804.cpp::crbug_1156804`.
 *
 * Regression test for chrome bug `crbug.com/1156804`. Exercises a
 * [SkImageFilters.Blur] whose input has been wrapped by
 * [SkImageFilters.Crop] (with `SkTileMode.kClamp`) — the crop is
 * intentionally outset from the painted rect so the blur's input
 * carries a transparent border that the kernel then bleeds against.
 * Without the explicit transparent padding the clamp tile mode would
 * "harden" the rect's edges when the large-σ blur downscales the source.
 *
 * Layout : 250 × 250 canvas, 4 × `drawOne` tiles in a 2 × 2 grid :
 *  - top-left  : sigma=3,  border=1   (small blur, tight border)
 *  - top-right : sigma=3,  border=30  (small blur, slack border)
 *  - bot-left  : sigma=20, border=1   (large blur, tight border — the bug case)
 *  - bot-right : sigma=20, border=30  (large blur, slack border — control)
 *
 * The bug-case tile is drawn in red so a hardened-edge regression is
 * visible against the green companion tiles.
 *
 * **kanvas-skia adaptation** : upstream C++ sets `paint.imageFilter`
 * directly on a `drawRect(rect, paint)` call. kanvas-skia's
 * [org.skia.core.SkBitmapDevice.drawRect] currently does **not**
 * honour `paint.imageFilter` (only [SkCanvas.saveLayer] / restore and
 * [SkCanvas.drawImageRect] route through the filter pipeline). To
 * keep the GM rendering correctly we lift each filtered rect into
 * its own `saveLayer(paint{imageFilter=F})` + plain
 * `drawRect(rect, paint{color})` + `restore()` — Skia internally
 * synthesises an equivalent layer when its `drawRect` sees an
 * image-filter paint, so the dance is semantics-preserving.
 *
 * C++ original:
 * ```cpp
 * static void drawOne(SkCanvas* canvas, SkRect rect, float saveBorder, float sigma, SkColor c) {
 *     SkRect borderRect = rect.makeOutset(saveBorder, saveBorder);
 *     SkPaint p;
 *     p.setColor(c);
 *     p.setImageFilter(
 *         SkImageFilters::Blur(sigma, sigma,
 *             SkImageFilters::Crop(borderRect, SkTileMode::kClamp, nullptr),
 *             borderRect.makeOutset(3 * sigma, 3 * sigma)));
 *     p.setAntiAlias(true);
 *     canvas->drawRect(rect, p);
 * }
 *
 * DEF_SIMPLE_GM(crbug_1156804, canvas, 250, 250) {
 *     drawOne(canvas, SkRect::MakeXYWH( 64,  64, 25, 25),  1,  3, SK_ColorGREEN);
 *     drawOne(canvas, SkRect::MakeXYWH(164,  64, 25, 25), 30,  3, SK_ColorGREEN);
 *     drawOne(canvas, SkRect::MakeXYWH( 64, 164, 25, 25),  1, 20, SK_ColorRED);
 *     drawOne(canvas, SkRect::MakeXYWH(164, 164, 25, 25), 30, 20, SK_ColorGREEN);
 * }
 * ```
 */
public class Crbug1156804GM : GM() {
    override fun getName(): String = "crbug_1156804"
    override fun getISize(): SkISize = SkISize.Make(250, 250)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        drawOne(c, SkRect.MakeXYWH(64f,  64f, 25f, 25f),  1f,  3f, SK_ColorGREEN)
        drawOne(c, SkRect.MakeXYWH(164f, 64f, 25f, 25f), 30f,  3f, SK_ColorGREEN)
        // This one would draw incorrectly because the large sigma causes
        // downscaling of the source and the one-pixel border would make the
        // downscaled image not contain trans-black at the edges. Combined
        // with the clamp mode on the blur filter it would "harden" the edge.
        drawOne(c, SkRect.MakeXYWH(64f, 164f, 25f, 25f),  1f, 20f, SK_ColorRED)
        drawOne(c, SkRect.MakeXYWH(164f, 164f, 25f, 25f), 30f, 20f, SK_ColorGREEN)
    }

    private fun drawOne(
        canvas: SkCanvas,
        rect: SkRect,
        saveBorder: Float,
        sigma: Float,
        color: SkColor,
    ) {
        val borderRect = rect.makeOutset(saveBorder, saveBorder)

        // Inner crop : carries the transparent padding band around `rect`.
        val croppedInput = SkImageFilters.Crop(borderRect, SkTileMode.kClamp, null)
        // Outer crop applied to the blur output, sized to capture the full
        // 3σ halo. Faithful to upstream's `Blur(σ, σ, input, cropRect)`
        // 4-arg call (kanvas-skia's Blur factory has no cropRect parameter,
        // so we apply the crop as a wrapping filter instead).
        val outerCrop = borderRect.makeOutset(3f * sigma, 3f * sigma)
        val blurFilter = SkImageFilters.Blur(sigma, sigma, croppedInput)
        val composedFilter = if (blurFilter != null) SkImageFilters.Crop(outerCrop, blurFilter) else null

        // Layer paint carries the imageFilter ; rect paint carries the
        // colour. See class KDoc for the rationale on the saveLayer dance.
        val layerPaint = SkPaint().apply {
            isAntiAlias = true
            imageFilter = composedFilter
        }
        val rectPaint = SkPaint().apply {
            this.color = color
            isAntiAlias = true
        }
        // Pass `null` as the saveLayer bounds : we want the layer to span
        // the full canvas clip so the filter's Crop(borderRect, kClamp)
        // can sample from the layer-local equivalent of the original
        // device-space borderRect with the offset bookkeeping intact.
        canvas.saveLayer(null, layerPaint)
        canvas.drawRect(rect, rectPaint)
        canvas.restore()
    }
}
