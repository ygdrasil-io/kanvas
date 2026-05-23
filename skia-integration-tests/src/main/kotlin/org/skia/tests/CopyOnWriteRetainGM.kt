package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SkImageInfo
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/surface.cpp::copy_on_write_retain` (256 × 256).
 *
 * Exercises copy-on-write semantics when a snapshot image outlives the
 * next draw onto the same surface. After clearing to red and snapshotting,
 * a clip-restricted blue clear leaves the right half still red.
 *
 * Expected result: left 128 px blue, right 128 px red.
 *
 * C++ body (`DEF_SURFACE_TESTS(copy_on_write_retain, canvas, 256, 256)`):
 * ```cpp
 * sk_sp<SkSurface> surf = make(info);
 * surf->getCanvas()->clear(SK_ColorRED);
 * sk_sp<SkImage> image = surf->makeImageSnapshot();
 * surf->getCanvas()->clipRect(SkRect::MakeWH(128, 256));
 * surf->getCanvas()->clear(SK_ColorBLUE);
 * canvas->drawImage(surf->makeImageSnapshot(), 0, 0);
 * ```
 */
public class CopyOnWriteRetainGM : GM() {

    override fun getName(): String = "copy_on_write_retain"
    override fun getISize(): SkISize = SkISize.Make(256, 256)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val info = SkImageInfo.MakeN32Premul(256, 256)
        val surf = SkSurface.MakeRaster(info)

        surf.canvas.clear(SK_ColorRED)
        // Snapshot must outlive the next draw so the surface sees an
        // outstanding image and must decide to retain vs discard pixels.
        @Suppress("UNUSED_VARIABLE")
        val image = surf.makeImageSnapshot()

        // A clip-restricted clear should NOT trigger the discard
        // optimisation — the previous red pixels outside the clip survive.
        surf.canvas.clipRect(SkRect.MakeWH(128f, 256f))
        surf.canvas.clear(SK_ColorBLUE)

        // Expect: blue | red
        c.drawImage(surf.makeImageSnapshot(), 0f, 0f)
    }
}
