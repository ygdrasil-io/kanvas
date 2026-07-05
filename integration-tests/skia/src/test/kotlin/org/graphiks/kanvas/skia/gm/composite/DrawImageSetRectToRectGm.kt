package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect
import kotlin.math.max
import kotlin.math.min

/**
 * Port of Skia's `gm/draw_image_set_rect_to_rect.cpp::DrawImageSetRectToRect`.
 * Tests drawing image rects with rect-to-rect mapping under various matrix transforms.
 * @see https://github.com/google/skia/blob/main/gm/draw_image_set_rect_to_rect.cpp
 */
class DrawImageSetRectToRectGm : SkiaGm {
    override val name = "draw_image_set_rect_to_rect"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 1250
    override val height = 850

    private data class Tile(val src: Rect, val dst: Rect, val alpha: Float)

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        drawCheckerboard(canvas)

        val kW = (kM * kTileW).toFloat()
        val kH = (kN * kTileH).toFloat()
        val backingImage = makeBackingImage(kM * kTileW, kN * kTileH)

        val tiles = mutableListOf<Tile>()
        for (y in 0 until kN) {
            for (x in 0 until kM) {
                tiles.add(Tile(
                    src = Rect(0f, 0f, kTileW.toFloat(), kTileH.toFloat()),
                    dst = Rect(
                        (x * kTileW).toFloat(), (y * kTileH).toFloat(),
                        ((x + 1) * kTileW).toFloat(), ((y + 1) * kTileH).toFloat(),
                    ),
                    alpha = 1f,
                ))
            }
        }

        val matrices = arrayOf(
            Matrix33.identity(),
            Matrix33.translate(kW / 2f, kH / 2f) * Matrix33.rotate(90f) * Matrix33.translate(-kW / 2f, -kH / 2f),
            Matrix33.scale(2f, 0.5f),
            Matrix33.translate(kW, kH) * Matrix33.scale(-1f, -1f),
            Matrix33.scale(2f, 0.5f) * (Matrix33.translate(kW / 2f, kH / 2f) * Matrix33.rotate(90f) * Matrix33.translate(-kW / 2f, -kH / 2f)) * Matrix33.translate(0f, kH) * Matrix33.scale(1f, -1f),
        )

        val kTranslate = max(kW, kH) * 2f + 10f
        canvas.translate(5f, 5f)

        for (frac in floatArrayOf(0f, 0.5f)) {
            canvas.save()
            canvas.translate(frac, frac)
            for (m in matrices.indices) {
                canvas.save()
                canvas.concat(matrices[m])
                for (tile in tiles) {
                    canvas.drawImageRect(backingImage, tile.src, tile.dst)
                }
                canvas.restore()
                canvas.translate(kTranslate, 0f)
            }
            canvas.restore()
            canvas.translate(0f, kTranslate)
        }

        canvas.translate(0f, -2f * kTranslate)

        for (scaleX in floatArrayOf(2f, 0.5f)) {
            val scaleY = if (scaleX > 1f) 0.5f else 2f
            canvas.save()
            for (m in matrices.indices) {
                canvas.save()
                canvas.concat(matrices[m])
                for ((ti, tile) in tiles.withIndex()) {
                    val sd = Rect(
                        tile.dst.left * scaleX, tile.dst.top * scaleY,
                        tile.dst.right * scaleX, tile.dst.bottom * scaleY,
                    )
                    val alpha = if (ti % 3 == 0) 0.4f else 1f
                    canvas.drawImageRect(backingImage, tile.src, sd,
                        Paint(color = Color.fromRGBA(1f, 1f, 1f, alpha)))
                }
                canvas.restore()
                canvas.translate(kTranslate, 0f)
            }
            canvas.restore()
            canvas.translate(0f, kTranslate)
        }
    }

    private fun makeBackingImage(w: Int, h: Int): Image {
        val pixels = ByteArray(w * h * 4)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val tx = x / kTileW; val ty = y / kTileH
                val i = (y * w + x) * 4
                val (r, g, b) = when {
                    tx == 0 && ty == 0 -> Triple(0x44, 0x44, 0xFF)
                    tx == 1 && ty == 0 -> Triple(0xFF, 0xFF, 0xFF)
                    tx == 0 && ty == 1 -> Triple(0xFF, 0x44, 0x44)
                    else -> Triple(0xFF, 0xFF, 0xFF)
                }
                pixels[i] = r.toByte(); pixels[i + 1] = g.toByte()
                pixels[i + 2] = b.toByte(); pixels[i + 3] = 0xFF.toByte()
            }
        }
        return Image.fromPixels(w, h, pixels, sourceId = "rect_to_rect")
    }

    private fun drawCheckerboard(canvas: GmCanvas) {
        val black = Paint(color = Color.BLACK)
        val white = Paint(color = Color.WHITE)
        val tile = 50
        val totalW = 1250; val totalH = 850
        var y = 0
        while (y < totalH) {
            var x = 0
            while (x < totalW) {
                val parity = ((x / tile) + (y / tile)) % 2
                canvas.drawRect(
                    Rect(x.toFloat(), y.toFloat(), (x + tile).toFloat(), (y + tile).toFloat()),
                    if (parity == 0) black else white,
                )
                x += tile
            }
            y += tile
        }
    }

    private companion object {
        const val kM = 2
        const val kN = 2
        const val kTileW = 40
        const val kTileH = 50
    }
}
