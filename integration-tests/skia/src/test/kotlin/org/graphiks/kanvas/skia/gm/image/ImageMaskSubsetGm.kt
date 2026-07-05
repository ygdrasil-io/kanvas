package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/imagemasksubset.cpp` ImageMaskSubsetGM.
 * Checks whether subset Images preserve the original color type (A8).
 * Three rows test raster, GPU-fallback, and lazy-backed images.
 * @see https://github.com/google/skia/blob/main/gm/imagemasksubset.cpp
 */
class ImageMaskSubsetGm : SkiaGm {
    override val name = "imagemasksubset"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 480
    override val height = 480

    private val kSize = 100
    private val kSubset = Rect.fromLTRB(25f, 25f, 75f, 75f)
    private val kDest = Rect.fromXYWH(10f, 10f, 100f, 100f)

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(color = Color.fromRGBA(0f, 1f, 0f))

        for (i in 0 until 3) {
            val image = makeMaskImage()

            // Left cell: drawImageRect using src=kSubset, dst=kDest
            canvas.drawImageRect(image, kSubset, kDest, paint)

            // Right cell: offset dst
            val dstRight = Rect(
                kDest.left + kSize * 1.5f, kDest.top,
                kDest.right + kSize * 1.5f, kDest.bottom,
            )
            canvas.drawImageRect(image, kSubset, dstRight, paint)

            canvas.translate(0f, kSize * 1.5f)
        }
    }

    private fun makeMaskImage(): Image {
        val bitmap = Bitmap(kSize, kSize, colorType = ColorType.ALPHA_8)
        for (y in 0 until kSize) {
            for (x in 0 until kSize) {
                val a = if (((x / 5) + (y / 5)) % 2 == 0) 128 else 0
                bitmap.setPixel(x, y, Color.fromRGBA(0f, 0f, 0f, a / 255f))
            }
        }
        return bitmap.toImage()
    }
}
