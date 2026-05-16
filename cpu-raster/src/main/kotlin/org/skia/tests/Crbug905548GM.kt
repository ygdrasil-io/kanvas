package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/crbug_905548.cpp::crbug_905548`.
 *
 * Regression test for chrome bug `crbug.com/905548`. Two stacked
 * 100 × 100 panels, each painting a 100 × 100 rect through a chained
 * image-filter pipeline that takes a small offscreen `surface` (a
 * filled circle on a transparent background) as the explicit source.
 *
 *  - Top panel : `Blend(kDstOut, Erode(0,0,Blur(15,15,imageSource)), imageSource)`
 *    — a degenerate erosion (radius 0×0 = identity) of the circle
 *    blurred by σ=15, then `kDstOut`-composited under the original
 *    crisp circle. The original image's coverage is "punched out" of
 *    the blurred halo.
 *  - Bottom panel : `Arithmetic(1, 0, 0, 0, false, eroded, imageSource)`
 *    — `result = 1·src·dst + 0 + 0 + 0` per pixel = `src ∘ dst`.
 *    Multiplies the eroded blur with the original crisp circle.
 *
 * Although `Erode(0, 0, …)` is the identity, the bug pattern probed
 * how the morphology filter kept its tile-mode / bbox metadata when
 * its parameters degenerated.
 *
 * **kanvas-skia adaptation** : upstream C++ sets `paint.imageFilter`
 * directly on a `drawRect(rect, paint)` call. kanvas-skia's
 * [org.skia.core.SkBitmapDevice.drawRect] currently does **not**
 * honour `paint.imageFilter` (only [SkCanvas.saveLayer] / restore and
 * [SkCanvas.drawImageRect] route through the filter pipeline). To
 * keep the GM rendering correctly we lift each filtered rect into
 * its own `saveLayer(paint{imageFilter=F})` + plain
 * `drawRect(rect)` + `restore()` — Skia internally synthesises an
 * equivalent layer when its `drawRect` sees an image-filter paint,
 * so the dance is semantics-preserving.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(crbug_905548, canvas, 100, 200) {
 *     auto surface = canvas->makeSurface(SkImageInfo::MakeN32Premul(100, 100));
 *     if (!surface) {
 *         surface = SkSurfaces::Raster(SkImageInfo::MakeN32Premul(100, 100));
 *     }
 *     surface->getCanvas()->clear(0);
 *     surface->getCanvas()->drawCircle(50, 50, 45, SkPaint());
 *     auto imageSource = SkImageFilters::Image(surface->makeImageSnapshot(), SkFilterMode::kNearest);
 *
 *     auto blurred = SkImageFilters::Blur(15, 15, imageSource);
 *     auto eroded = SkImageFilters::Erode(0, 0, blurred);
 *     auto blended = SkImageFilters::Blend(SkBlendMode::kDstOut, eroded, imageSource, nullptr);
 *
 *     SkPaint paint;
 *     paint.setImageFilter(blended);
 *     canvas->drawRect(SkRect::MakeWH(100, 100), paint);
 *
 *     auto mult = SkImageFilters::Arithmetic(1, 0, 0, 0, false, eroded, imageSource, nullptr);
 *     paint.setImageFilter(mult);
 *     canvas->translate(0, 100);
 *     canvas->drawRect(SkRect::MakeWH(100, 100), paint);
 * }
 * ```
 */
public class Crbug905548GM : GM() {
    override fun getName(): String = "crbug_905548"
    override fun getISize(): SkISize = SkISize.Make(100, 200)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Substitute upstream's `canvas->makeSurface(...)` with a
        // standalone raster surface — kanvas-skia's `SkCanvas` doesn't
        // expose `makeSurface`, but the result is identical for the
        // raster pipeline (no GPU context to inherit).
        val surface = SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(100, 100))
        surface.canvas.clear(0)
        surface.canvas.drawCircle(50f, 50f, 45f, SkPaint())
        val imageSource = SkImageFilters.Image(
            surface.makeImageSnapshot(),
            SkSamplingOptions(SkFilterMode.kNearest),
        )

        val blurred = SkImageFilters.Blur(15f, 15f, imageSource)
        val eroded = SkImageFilters.Erode(0, 0, blurred)
        val blended = SkImageFilters.Blend(SkBlendMode.kDstOut, eroded, imageSource)

        val rectBounds = SkRect.MakeWH(100f, 100f)

        // Top panel : Blend filter via saveLayer dance (see class KDoc).
        val topLayerPaint = SkPaint().apply { imageFilter = blended }
        c.saveLayer(rectBounds, topLayerPaint)
        c.drawRect(rectBounds, SkPaint())
        c.restore()

        // Bottom panel : Arithmetic filter via the same dance.
        val mult = SkImageFilters.Arithmetic(
            k1 = 1f, k2 = 0f, k3 = 0f, k4 = 0f,
            enforcePMColor = false, bg = eroded, fg = imageSource,
        )
        c.translate(0f, 100f)
        val bottomLayerPaint = SkPaint().apply { imageFilter = mult }
        c.saveLayer(rectBounds, bottomLayerPaint)
        c.drawRect(rectBounds, SkPaint())
        c.restore()
    }
}
