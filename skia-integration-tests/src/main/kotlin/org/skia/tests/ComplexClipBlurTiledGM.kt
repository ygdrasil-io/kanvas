package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SkClipOp
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import kotlin.math.ceil

/**
 * Port of Skia's `gm/complexclip_blur_tiled.cpp::ComplexClipBlurTiledGM`
 * (512 × 512).
 *
 * Renders the canvas in 128×128 raster sub-tiles. Each tile :
 *  1. is translated so the tile origin sits at (0, 0) ;
 *  2. opens a `saveLayer` with an `SkImageFilters::Blur(5, 5)` paint ;
 *  3. cuts a rounded-rect hole (20 px inset, 25 px corner radii) out
 *     of the layer's clip via `clipRRect(rrect, kDifference, AA = true)` ;
 *  4. fills the tile rect black — the blur filter then re-spreads the
 *     remaining black border into the cut.
 *
 * The 16 tile images are composited onto the destination canvas via
 * `drawImage`. Upstream uses `ToolUtils::makeSurface(canvas, info)` to
 * keep the tile colour-space matched ; here we use the public
 * [SkSurface.MakeRasterN32Premul] factory which seeds an sRGB surface —
 * good enough to land within the GM's similarity envelope.
 */
public class ComplexClipBlurTiledGM : GM() {

    private val width = 512
    private val height = 512
    private val tileSize = 128f

    override fun getName(): String = "complexclip_blur_tiled"
    override fun getISize(): SkISize = SkISize.Make(width, height)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val blurPaint = SkPaint().apply {
            imageFilter = SkImageFilters.Blur(5.0f, 5.0f, null)
        }
        val bounds = c.getLocalClipBounds()
        val ts = ceil(tileSize).toInt()
        val info = SkImageInfo.MakeN32Premul(ts, ts)

        var y = bounds.top
        while (y < bounds.bottom) {
            var x = bounds.left
            while (x < bounds.right) {
                val tileSurface = SkSurface.MakeRaster(info)
                val tileCanvas = tileSurface.canvas
                tileCanvas.save()
                tileCanvas.translate(-x, -y)
                val rect = SkRect.MakeWH(width.toFloat(), height.toFloat())
                tileCanvas.saveLayer(rect, blurPaint)
                val rrect = SkRRect.MakeRectXY(rect.makeInset(20f, 20f), 25f, 25f)
                tileCanvas.clipRRect(rrect, SkClipOp.kDifference, doAntiAlias = true)
                val fillPaint = SkPaint()
                tileCanvas.drawRect(rect, fillPaint)
                tileCanvas.restore()
                tileCanvas.restore()
                c.drawImage(tileSurface.makeImageSnapshot(), x, y)
                x += tileSize
            }
            y += tileSize
        }
    }
}
