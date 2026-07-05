package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/all_bitmap_configs.cpp :: all_variants_8888`.
 *
 * Draws an 8 × 2 grid of 128×128 discs covering the cross-product of
 * colour type (RGBA_8888, BGRA_8888) and alpha usage (premul, unpremul)
 * over a checkerboard background. Both colour types use the same
 * byte-storage in the new Bitmap API, so the two unpremul columns render
 * identically (both red).
 * @see https://github.com/google/skia/blob/main/gm/all_bitmap_configs.cpp
 */
class AllVariants8888Gm : SkiaGm {
    override val name = "all_variants_8888"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val tolerance = 8
    override val width = 4 * SCALE + 30
    override val height = 2 * SCALE + 10

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        drawCheckerboard(canvas)

        for (alphaType in listOf(true, false)) {
            canvas.save()
            for (colorType in listOf(ColorType.RGBA_8888, ColorType.BGRA_8888)) {
                val bm = makeVariant(colorType, alphaType)
                canvas.drawImage(bm.toImage(), Rect(0f, 0f, SCALE.toFloat(), SCALE.toFloat()))
                canvas.translate((SCALE + 10).toFloat(), 0f)
            }
            canvas.restore()
            canvas.translate(0f, (SCALE + 10).toFloat())
        }
    }

    private fun makeVariant(colorType: ColorType, premul: Boolean): Bitmap {
        val bm = Bitmap(SCALE, SCALE, colorType)
        val r = SCALE / 2f
        for (y in 0 until SCALE) {
            for (x in 0 until SCALE) {
                val dx = x - r
                val dy = y - r
                val inside = (dx * dx + dy * dy) < (r * r)
                val color = if (inside) {
                    if (premul) Color.WHITE
                    else Color.fromRGBA(1f, 0f, 0f, 1f)
                } else {
                    Color.TRANSPARENT
                }
                bm.setPixel(x, y, color)
            }
        }
        return bm
    }

    private fun drawCheckerboard(canvas: GmCanvas) {
        val ltGray = Color.fromRGBA(0.94f, 0.94f, 0.94f)
        val white = Color.WHITE
        val size = 8f
        for (y in 0..((height / size).toInt())) {
            for (x in 0..((width / size).toInt())) {
                val color = if ((x + y) % 2 == 0) ltGray else white
                canvas.drawRect(
                    Rect(x * size, y * size, (x + 1) * size, (y + 1) * size),
                    Paint.fill(color),
                )
            }
        }
    }

    private companion object {
        private const val SCALE = 128
    }
}
