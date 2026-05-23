package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SkImageInfo
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/surface.cpp::simple_snap_image2` (256 × 256).
 *
 * Like `simple_snap_image` but the surface is explicitly dropped (`reset`)
 * before the image is drawn. Validates that the snapshot holds its own
 * pixel copy independent of the surface's lifetime.
 *
 * Expected result: solid red 256 × 256.
 *
 * C++ body (`DEF_SURFACE_TESTS(simple_snap_image2, canvas, 256, 256)`):
 * ```cpp
 * sk_sp<SkSurface> surf = make(info);
 * surf->getCanvas()->clear(SK_ColorRED);
 * sk_sp<SkImage> image = surf->makeImageSnapshot();
 * surf.reset();
 * canvas->drawImage(std::move(image), 0, 0);
 * ```
 */
public class SimpleSnapImage2GM : GM() {

    override fun getName(): String = "simple_snap_image2"
    override fun getISize(): SkISize = SkISize.Make(256, 256)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val info = SkImageInfo.MakeN32Premul(256, 256)
        var surf: SkSurface? = SkSurface.MakeRaster(info)

        surf!!.canvas.clear(SK_ColorRED)
        val image = surf.makeImageSnapshot()
        // Surface dropped — image must hold its own copy.
        surf = null

        // Expect: solid red
        c.drawImage(image, 0f, 0f)
    }
}
