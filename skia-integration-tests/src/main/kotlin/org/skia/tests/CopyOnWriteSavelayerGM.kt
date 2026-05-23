package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/surface.cpp::copy_on_write_savelayer` (256 × 256).
 *
 * Draws into a full-screen saveLayer with alpha 0.25. The layer paint is
 * non-opaque, so the COW logic must not discard the existing red pixels —
 * even though the layer's clear colour (blue) is fully opaque, the
 * compositing paint (alpha = 0.25) means the result is blue-over-red blend.
 *
 * Expected result: 25 % blue blended over red ≈ `#FF4000BF` (reddish-purple).
 *
 * C++ body (`DEF_SURFACE_TESTS(copy_on_write_savelayer, canvas, 256, 256)`):
 * ```cpp
 * sk_sp<SkSurface> surf = make(info);
 * surf->getCanvas()->clear(SK_ColorRED);
 * sk_sp<SkImage> image = surf->makeImageSnapshot();   // keeps red alive
 * SkPaint paint; paint.setAlphaf(0.25f);
 * surf->getCanvas()->saveLayer({0,0,256,256}, &paint);
 * surf->getCanvas()->clear(SK_ColorBLUE);
 * surf->getCanvas()->restore();
 * canvas->drawImage(surf->makeImageSnapshot(), 0, 0);
 * ```
 */
public class CopyOnWriteSavelayerGM : GM() {

    override fun getName(): String = "copy_on_write_savelayer"
    override fun getISize(): SkISize = SkISize.Make(256, 256)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val info = SkImageInfo.MakeN32Premul(256, 256)
        val surf = SkSurface.MakeRaster(info)

        surf.canvas.clear(SK_ColorRED)
        // Snapshot keeps red pixels alive through the next draw.
        @Suppress("UNUSED_VARIABLE")
        val image = surf.makeImageSnapshot()

        // Full-screen saveLayer with alpha = 0.25. The layer's clear (blue)
        // is opaque, but the composite paint is not — COW must not discard.
        val layerPaint = SkPaint().apply { alphaf = 0.25f }
        surf.canvas.saveLayer(SkRect.MakeWH(256f, 256f), layerPaint)
        surf.canvas.clear(SK_ColorBLUE)
        surf.canvas.restore()

        // Expect: 25 % blue over red
        c.drawImage(surf.makeImageSnapshot(), 0f, 0f)
    }
}
