package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorBLUE
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/blurs.cpp::DEF_SIMPLE_GM(TiledBlurBigSigma, …, 1024, 768)`.
 *
 * Regression test for [crbug.com/1500021](https://crbug.com/1500021). Simulates
 * Chrome's raster-tile compositing or Viewer's tiled rendering mode by manually
 * applying a 3 × 3 tiled grid (342 × 256 px per tile) to a large image-filtered
 * scene.
 *
 * For each tile `(x, y)`:
 *  1. `save` + `resetMatrix` — strip the CTM so the clip can be set in screen
 *     space independently of any transform hierarchy.
 *  2. `clipRect` to the tile bounds (upstream: `clipIRect`; we use `clipRect`
 *     since `:kanvas-skia` has no `clipIRect` — equivalent for integer rects).
 *  3. `setMatrix(origCTM)` — restore the original local-to-device transform.
 *  4. Composite a black-flood `ColorFilter` over a `SrcOver` blend inside a
 *     `saveLayer` with a large blur (σ = 206) applied as the layer image filter.
 *  5. Draw a blue circle (radius 350, centre 600,150) as the layer content.
 *  6. Restore twice (layer + tile).
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(TiledBlurBigSigma, canvas, 1024, 768) {
 *     static constexpr int kTileWidth = 342;
 *     static constexpr int kTileHeight = 256;
 *
 *     SkM44 origCTM = canvas->getLocalToDevice();
 *
 *     for (int y = 0; y < 3; ++y) {
 *         for (int x = 0; x < 3; ++x) {
 *             canvas->save();
 *                 canvas->resetMatrix();
 *                 canvas->clipIRect(SkIRect::MakeXYWH(x*kTileWidth, y*kTileHeight,
 *                                                     kTileWidth, kTileHeight));
 *                 canvas->setMatrix(origCTM);
 *
 *                 auto flood = SkImageFilters::ColorFilter(SkColorFilters::Blend(
 *                         SK_ColorBLACK, SkBlendMode::kSrc), nullptr);
 *                 auto blend = SkImageFilters::Blend(SkBlendMode::kSrcOver,
 *                                                    std::move(flood), nullptr);
 *                 auto blur = SkImageFilters::Blur(206.f, 206.f, std::move(blend));
 *
 *                 SkPaint p;
 *                 p.setImageFilter(std::move(blur));
 *
 *                 canvas->clipRect({0, 0, 1970, 1223});
 *                 canvas->saveLayer(nullptr, &p);
 *                     SkPaint fill;
 *                     fill.setColor(SK_ColorBLUE);
 *                     canvas->drawCircle(600, 150, 350, fill);
 *                 canvas->restore();
 *             canvas->restore();
 *         }
 *     }
 * }
 * ```
 */
public class TiledBlurBigSigmaGM : GM() {

    override fun getName(): String = "TiledBlurBigSigma"
    override fun getISize(): SkISize = SkISize.Make(1024, 768)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val kTileWidth = 342
        val kTileHeight = 256

        // Capture the original CTM (full 4×4, mirrors `SkM44 origCTM = canvas->getLocalToDevice()`).
        val origCTM = c.getLocalToDevice()

        for (y in 0 until 3) {
            for (x in 0 until 3) {
                c.save()

                // Strip CTM to screen space so the tile clip is in pixel coords.
                c.resetMatrix()

                // Clip to tile bounds. Upstream uses clipIRect (integer rect); we use
                // clipRect (non-AA, integer-snapped) — equivalent for axis-aligned integer rects.
                c.clipRect(
                    SkRect.MakeXYWH(
                        (x * kTileWidth).toFloat(),
                        (y * kTileHeight).toFloat(),
                        kTileWidth.toFloat(),
                        kTileHeight.toFloat(),
                    )
                )

                // Restore the original transform inside the tile clip.
                c.setMatrix(origCTM)

                // Build the filter chain:
                //   flood  = ColorFilter(Blend(BLACK, kSrc), null)  → black flood fill
                //   blend  = Blend(kSrcOver, flood, null)            → SrcOver composite
                //   blur   = Blur(206, 206, blend)                   → big gaussian blur
                val flood = SkImageFilters.ColorFilter(
                    SkColorFilters.Blend(SK_ColorBLACK, SkBlendMode.kSrc),
                    null,
                )
                val blend = SkImageFilters.Blend(SkBlendMode.kSrcOver, flood, null)
                val blur  = SkImageFilters.Blur(206f, 206f, blend)

                val p = SkPaint().apply { imageFilter = blur }

                // Clip to the full source-space scene bounds, then open the filter layer.
                c.clipRect(SkRect.MakeLTRB(0f, 0f, 1970f, 1223f))
                c.saveLayer(null, p)

                val fill = SkPaint().apply { color = SK_ColorBLUE }
                c.drawCircle(600f, 150f, 350f, fill)

                c.restore() // close saveLayer
                c.restore() // close tile save
            }
        }
    }
}
