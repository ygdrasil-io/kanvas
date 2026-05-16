package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkFont
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/imageresizetiled.cpp::DEF_SIMPLE_GM(imageresizetiled,
 * canvas, 640, 480)`.
 *
 * Tiles the 640×480 canvas into 100-pixel cells. In each cell : clip to the
 * cell, scale by `1/2`, then `saveLayer` with an [SkImageFilters.MatrixTransform]
 * `(2×, 2×)` filter — this draws unfiltered text into the layer, then the
 * filter upscales the layer 2× on restore so the net effect across all tiles
 * is identity scale but the upscaling sampling boundaries are exposed.
 *
 * Inside each layer, four strings are drawn at 100, 200, 300, 400 baselines
 * with a 100-pt portable typeface :
 *   "The quick", "brown fox", "jumped over", "the lazy dog."
 *
 * The reference image shows the same text repeated tiled because each cell
 * scales-down then scales-up the **same** text origin set (0,0). The
 * clipping creates the visual "tile" effect.
 */
public class ImageResizeTiledGM : GM() {

    override fun getName(): String = "imageresizetiled"
    override fun getISize(): SkISize = SkISize.Make(WIDTH, HEIGHT)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val matrix = SkMatrix.MakeScale(RESIZE_FACTOR, RESIZE_FACTOR)
        val paint = SkPaint().apply {
            imageFilter = SkImageFilters.MatrixTransform(matrix, SkSamplingOptions.Default, null)
        }
        val font = SkFont(ToolUtils.DefaultPortableTypeface(), 100f)
        val tileSize = 100f
        val strs = arrayOf("The quick", "brown fox", "jumped over", "the lazy dog.")

        var y = 0f
        while (y < HEIGHT.toFloat()) {
            var x = 0f
            while (x < WIDTH.toFloat()) {
                c.save()
                c.clipRect(SkRect.MakeXYWH(x, y, tileSize, tileSize))
                c.scale(1f / RESIZE_FACTOR, 1f / RESIZE_FACTOR)
                c.saveLayer(null, paint)
                var posY = 0f
                for (s in strs) {
                    posY += 100f
                    c.drawString(s, 0f, posY, font, SkPaint())
                }
                c.restore()
                c.restore()
                x += tileSize
            }
            y += tileSize
        }
    }

    private companion object {
        private const val WIDTH: Int = 640
        private const val HEIGHT: Int = 480
        private const val RESIZE_FACTOR: Float = 2f
    }
}
