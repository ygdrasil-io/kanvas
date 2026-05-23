package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SkImageInfo
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/surface.cpp::copy_on_write_retain2` (256 × 256).
 *
 * Like `copy_on_write_retain` but draws the snapped image back to the same
 * surface it was snapped from. The COW semantics ensure the blue snapshot
 * survives the subsequent red clear + clip-restricted re-draw.
 *
 * Expected result: left 128 px blue, right 128 px red.
 *
 * C++ body (`DEF_SURFACE_TESTS(copy_on_write_retain2, canvas, 256, 256)`):
 * ```cpp
 * sk_sp<SkSurface> surf = make(info);
 * surf->getCanvas()->clear(SK_ColorBLUE);
 * sk_sp<SkImage> image = surf->makeImageSnapshot();
 * surf->getCanvas()->clear(SK_ColorRED);
 * surf->getCanvas()->clipRect(SkRect::MakeWH(128, 256));
 * surf->getCanvas()->drawImage(image, 0, 0);
 * canvas->drawImage(surf->makeImageSnapshot(), 0, 0);
 * ```
 */
public class CopyOnWriteRetain2GM : GM() {

    override fun getName(): String = "copy_on_write_retain2"
    override fun getISize(): SkISize = SkISize.Make(256, 256)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val info = SkImageInfo.MakeN32Premul(256, 256)
        val surf = SkSurface.MakeRaster(info)

        surf.canvas.clear(SK_ColorBLUE)
        // Snapshot must outlive the next draw.
        val image = surf.makeImageSnapshot()

        surf.canvas.clear(SK_ColorRED)
        // Clip to left half and draw the blue snapshot back — the retained
        // blue covers the left 128 px; the red background fills the right.
        surf.canvas.clipRect(SkRect.MakeWH(128f, 256f))
        surf.canvas.drawImage(image, 0f, 0f)

        // Expect: blue | red
        c.drawImage(surf.makeImageSnapshot(), 0f, 0f)
    }
}
