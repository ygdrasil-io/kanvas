package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.skia.toImageForGm
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32

/**
 * Port of Skia's `gm/bitmaprect.cpp` (`DrawBitmapRect3`).
 * Probes drawImageRect with a partial source rect: a 3x3 bitmap drawn
 * with srcR = (0.5, 0.5, 2.5, 2.5) into a 200x100 device rect.
 * @see https://github.com/google/skia/blob/main/gm/bitmaprect.cpp
 */
class DrawBitmapRect3Gm : SkiaGm {
    override val name = "3x3bitmaprect"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 480

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val bitmap = make3x3Bitmap()
        val image = bitmap.toImageForGm()
        val srcR = RectF32.ofLTRB(0.5f, 0.5f, 2.5f, 2.5f)
        val dstR = RectF32.ofLTRB(100f, 100f, 300f, 200f)
        canvas.drawImageRect(image, srcR, dstR)
    }

    private fun make3x3Bitmap(): Bitmap {
        val bitmap = Bitmap(3, 3)
        val YELLOW = ColorARGB.fromRGBA(1f, 1f, 0f)
        val GRAY = ColorARGB.fromRGBA(0.5f, 0.5f, 0.5f)
        val CYAN = ColorARGB.fromRGBA(0f, 1f, 1f)
        val MAGENTA = ColorARGB.fromRGBA(1f, 0f, 1f)
        val data = arrayOf(
            arrayOf(ColorARGB.Red,    ColorARGB.White, ColorARGB.Blue),
            arrayOf(ColorARGB.Green,  ColorARGB.Black, CYAN),
            arrayOf(YELLOW, GRAY,  MAGENTA),
        )
        for (x in 0 until 3) {
            for (y in 0 until 3) {
                bitmap.setPixel(x, y, data[x][y])
            }
        }
        return bitmap
    }
}
