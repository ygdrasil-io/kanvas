package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SkImageInfo
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/surface.cpp::NewSurfaceGM` (300 × 140).
 *
 * Exercises [SkSurfaces.Raster] + [org.skia.core.SkSurface.makeImageSnapshot]
 * + [org.skia.core.SkSurface.makeSurface] :
 *  1. Make a 100×100 raster surface, fill with red.
 *  2. Snapshot to an image, draw at (10, 10).
 *  3. Spawn a sibling surface via `surf.makeSurface(info)`, also fill
 *     red, snapshot, draw at (120, 10) — the gap of `10 + width + 10`
 *     mirrors upstream's `10 + image->width() + 10` offset.
 *
 * Both red squares should be 100×100 px on a white background.
 */
public class NewSurfaceGM : GM() {

    override fun getName(): String = "surfacenew"
    override fun getISize(): SkISize = SkISize.Make(300, 140)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val info = SkImageInfo.MakeN32Premul(100, 100)

        val surf = SkSurface.MakeRaster(info)
        surf.canvas.drawColor(SK_ColorRED)

        val image = surf.makeImageSnapshot()
        c.drawImage(image, 10f, 10f)

        // Upstream uses `surf->makeSurface(info)`. Our SkSurface doesn't
        // expose that factory directly ; reuse [SkCanvas.makeSurface] on
        // surf's canvas, which mirrors upstream's contract.
        val surf2 = surf.canvas.makeSurface(info)!!
        surf2.canvas.drawColor(SK_ColorRED)
        val image2 = surf2.makeImageSnapshot()
        c.drawImage(image2, 10f + image.width.toFloat() + 10f, 10f)
    }
}
