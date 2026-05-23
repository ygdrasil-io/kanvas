package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SkImageInfo
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/surface.cpp::simple_snap_image` (256 × 256).
 *
 * Basic test that a surface filled with red produces a red snapshot.
 *
 * Expected result: solid red 256 × 256.
 *
 * C++ body (`DEF_SURFACE_TESTS(simple_snap_image, canvas, 256, 256)`):
 * ```cpp
 * sk_sp<SkSurface> surf = make(info);
 * surf->getCanvas()->clear(SK_ColorRED);
 * sk_sp<SkImage> image = surf->makeImageSnapshot();
 * canvas->drawImage(std::move(image), 0, 0);
 * ```
 */
public class SimpleSnapImageGM : GM() {

    override fun getName(): String = "simple_snap_image"
    override fun getISize(): SkISize = SkISize.Make(256, 256)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val info = SkImageInfo.MakeN32Premul(256, 256)
        val surf = SkSurface.MakeRaster(info)

        surf.canvas.clear(SK_ColorRED)
        val image = surf.makeImageSnapshot()
        // Expect: solid red
        c.drawImage(image, 0f, 0f)
    }
}
