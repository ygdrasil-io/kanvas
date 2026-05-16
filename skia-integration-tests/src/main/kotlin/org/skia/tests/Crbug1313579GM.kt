package org.skia.tests

import org.skia.core.SaveLayerRec
import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkIRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/crbug_1313579.cpp::crbug_1313579`.
 *
 * Regression test for `crbug.com/1313579`. SkiaRenderer can wind up
 * specifying near-integer scale-and-translate matrices on the canvas
 * before applying a backdrop blur image filter via `saveLayer()` with
 * an integer clip, crop rect, and `SaveLayerRec` bounds. Round-out
 * is used to determine the bounds of the input image needed in IFs.
 * This could cause an extra row/column of pixels to be included in
 * the blur. When that row/column is significantly different in colour
 * than the intended blur content and the radius is large then clamp
 * mode blur creates a very noticeable colour bleed artefact.
 *
 * The GM clears the canvas to green, concatenates a near-identity
 * `0.999999f` scale + `4.99999f` translate, clips to the [0,100)²
 * "background" rect, fills it white, then opens an empty
 * `saveLayer(SaveLayerRec)` whose `fBackdrop` is a 50σ clamp-mode
 * blur of that white interior. The expected output is the white BG
 * with the (clamped) blurred halo bleeding evenly outwards — **no**
 * green-on-white bleed at the [100, 110) margin.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(crbug_1313579, canvas, 110, 110) {
 *     static constexpr auto kBGRect = SkIRect{0, 0, 100, 100};
 *     sk_sp<SkImageFilter> backdrop_filter =
 *         SkImageFilters::Blur(50.f, 50.f, SkTileMode::kClamp, nullptr, kBGRect);
 *     SkMatrix m;
 *     canvas->clear(SK_ColorGREEN);
 *     m.setAll(0.999999f, 0,         4.99999f,
 *              0,         0.999999f, 4.99999f,
 *              0,         0,         1);
 *     canvas->concat(m);
 *     canvas->clipIRect(kBGRect);
 *     canvas->clear(SK_ColorWHITE);
 *     canvas->saveLayer(SkCanvas::SaveLayerRec(nullptr, nullptr, backdrop_filter.get(), 0));
 *     canvas->restore();
 * }
 * ```
 */
public class Crbug1313579GM : GM() {
    override fun getName(): String = "crbug_1313579"
    override fun getISize(): SkISize = SkISize.Make(110, 110)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val bgRect = SkIRect.MakeLTRB(0, 0, 100, 100)
        val backdropFilter = SkImageFilters.Blur(
            sigmaX = 50f,
            sigmaY = 50f,
            tileMode = SkTileMode.kClamp,
            input = null,
            cropRect = bgRect,
        )

        c.clear(SK_ColorGREEN)
        // Upstream uses `setAll(...)` with persp row [0,0,1] — an affine
        // matrix. Build the equivalent via the SkMatrix copy ctor.
        val m = SkMatrix(
            sx = 0.999999f, kx = 0f, tx = 4.99999f,
            ky = 0f, sy = 0.999999f, ty = 4.99999f,
        )
        c.concat(m)
        c.clipRect(SkRect.Make(bgRect))
        c.clear(SK_ColorWHITE)
        c.saveLayer(SaveLayerRec(bounds = null, paint = null, backdrop = backdropFilter, flags = 0))
        c.restore()
    }
}
