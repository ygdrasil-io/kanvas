package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/bitmaprect.cpp` (`DrawBitmapRect3`).
 * Probes drawImageRect with a partial source rect: a 3x3 bitmap drawn
 * with srcR = (0.5, 0.5, 2.5, 2.5) into a 200x100 device rect.
 * @see https://github.com/google/skia/blob/main/gm/bitmaprect.cpp
 */
class DrawBitmapRect3Gm : SkiaGm {
    override val name = "3x3bitmaprect"
    override val renderFamily = RenderFamily.IMAGE
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 480

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val bitmap = make3x3Bitmap()
        val image = bitmap.toImage()
        val srcR = Rect.fromLTRB(0.5f, 0.5f, 2.5f, 2.5f)
        val dstR = Rect.fromLTRB(100f, 100f, 300f, 200f)
        canvas.drawImageRect(image, srcR, dstR)
    }

    private fun make3x3Bitmap(): Bitmap {
        val bitmap = Bitmap(3, 3)
        val YELLOW = Color.fromRGBA(1f, 1f, 0f)
        val GRAY = Color.fromRGBA(0.5f, 0.5f, 0.5f)
        val CYAN = Color.fromRGBA(0f, 1f, 1f)
        val MAGENTA = Color.fromRGBA(1f, 0f, 1f)
        val data = arrayOf(
            arrayOf(Color.RED,    Color.WHITE, Color.BLUE),
            arrayOf(Color.GREEN,  Color.BLACK, CYAN),
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
