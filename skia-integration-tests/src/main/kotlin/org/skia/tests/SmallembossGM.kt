package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.foundation.SkEmbossMaskFilter
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint

/**
 * Port of Skia's `gm/emboss.cpp::smallemboss` (50 × 50).
 *
 * Draws a tiny 3 × 3 embossed rectangle into an off-screen 50 × 50 surface
 * (obtained via [SkCanvas.makeSurface] or fallback [SkSurface.MakeRaster]),
 * then scale-blits it 30× onto the output canvas so the emboss effect is
 * clearly visible at test-comparison resolution.
 */
public class SmallembossGM : GM() {

    override fun getName(): String = "smallemboss"
    override fun getISize(): SkISize = SkISize.Make(50, 50)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val embossFilter = SkEmbossMaskFilter.Make(3f, SkEmbossMaskFilter.Light(
            direction = floatArrayOf(1f, 1f, 1f),
            ambient = 0,
            specular = 16,
        )) ?: return

        val paint = SkPaint().apply {
            isAntiAlias = true
            color = SK_ColorBLACK
            maskFilter = embossFilter
        }

        val info = SkImageInfo.MakeN32Premul(50, 50)
        // Mirror upstream: prefer the canvas-backed surface, fall back to raster.
        val surface: SkSurface = c.makeSurface(info) ?: SkSurface.MakeRaster(info)

        val canv = surface.canvas
        canv.drawRect(SkRect.MakeXYWH(1f, 1f, 3f, 3f), paint)

        c.scale(30f, 30f)
        val img = surface.makeImageSnapshot()
        c.drawImage(img, 0f, 0f)
    }
}
