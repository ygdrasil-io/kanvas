package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkLattice
import org.skia.core.SkSurface
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.graphiks.math.SK_ColorMAGENTA
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/lattice.cpp` `DEF_SIMPLE_GM_BG(lattice_alpha, canvas, 120, 120, SK_ColorWHITE)`.
 *
 * Exercises code paths that incorporate the paint colour when drawing a
 * lattice from an **alpha-only** (kA8) image.  The source is a 100×100
 * circle drawn into an A8 surface; the lattice uses 4 divs in each
 * direction (at 20, 40, 60, 80) with no rectType overrides.  The paint
 * is set to `SK_ColorMAGENTA`, so the filled pixels inherit that colour,
 * demonstrating that the paint tint is applied when the image has no
 * colour channels.
 *
 * Background colour: SK_ColorWHITE (mirroring the `DEF_SIMPLE_GM_BG` macro).
 */
public class LatticeAlphaGM : GM() {

    init { setBGColor(SK_ColorWHITE) }

    override fun getName(): String = "lattice_alpha"
    override fun getISize(): SkISize = SkISize.Make(120, 120)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val surface = SkSurface.MakeRaster(SkImageInfo.MakeA8(100, 100))
        surface.canvas.clear(0)
        surface.canvas.drawCircle(50f, 50f, 50f, SkPaint())
        val image = surface.makeImageSnapshot()

        val divs = intArrayOf(20, 40, 60, 80)

        val lattice = SkLattice(
            xDivs = divs,
            yDivs = divs,
            rectTypes = null,
            colors = null,
            bounds = null,
        )

        val paint = SkPaint()
        paint.color = SK_ColorMAGENTA
        c.drawImageLattice(image, lattice, SkRect.MakeWH(120f, 120f),
            SkFilterMode.kNearest, paint)
    }
}
