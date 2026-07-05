package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

class DrawImageSetAlphaOnlyGm : SkiaGm {
    override val name = "draw_image_set_alpha_only"
    override val renderFamily = RenderFamily.COMPOSITE
    override val minSimilarity = 0.0
    override val width = kM * kTileW
    override val height = 2 * kN * kTileH

    private data class Tile(val image: Image, val src: Rect, val dst: Rect, val alpha: Float, val isAlphaOnly: Boolean)

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        drawCheckerboard(canvas, 25)
        val tiles = buildTiles()

        for (tile in tiles) {
            val alpha = (kM - ((tile.dst.left / kTileW).toInt() % kM)) / kM.toFloat()
            val paint = if (tile.isAlphaOnly) {
                Paint(color = Color.fromRGBA(0.2f, 0.8f, 0.4f, alpha * tile.alpha))
            } else {
                Paint(color = Color.fromRGBA(1f, 1f, 1f, alpha * tile.alpha))
            }
            canvas.drawImageRect(tile.image, tile.src, tile.dst, paint)
        }

        canvas.translate(0f, (kN * kTileH).toFloat())

        for (tile in tiles) {
            val alpha = (kM - ((tile.dst.left / kTileW).toInt() % kM)) / kM.toFloat()
            val paint = if (tile.isAlphaOnly) {
                Paint(color = Color.fromRGBA(0.2f, 0.8f, 0.4f, alpha * tile.alpha))
            } else {
                Paint(color = Color.fromRGBA(1f, 1f, 1f, alpha * tile.alpha))
            }
            canvas.drawImageRect(tile.image, tile.src, tile.dst, paint)
        }
    }

    private fun buildTiles(): List<Tile> {
        val tiles = mutableListOf<Tile>()
        for (y in 0 until kN) {
            for (x in 0 until kM) {
                val isAlphaOnly = y % 2 == 0
                val image = if (isAlphaOnly) {
                    makeAlpha8Tile(kTileW + 2, kTileH + 2, x, y)
                } else {
                    makeColorTile(kTileW + 2, kTileH + 2, x, y)
                }
                val srcL = 1f; val srcT = 1f
                val src = Rect(srcL, srcT, srcL + kTileW, srcT + kTileH)
                val dst = Rect(
                    (x * kTileW).toFloat(), (y * kTileH).toFloat(),
                    ((x + 1) * kTileW).toFloat(), ((y + 1) * kTileH).toFloat(),
                )
                tiles.add(Tile(image, src, dst, 1f, isAlphaOnly))
            }
        }
        return tiles
    }

    private fun makeAlpha8Tile(w: Int, h: Int, tx: Int, ty: Int): Image {
        val pixels = ByteArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val diag = ((x + y) / 5) % 2
                pixels[y * w + x] = if (diag == 0) 0xFF.toByte() else 0x40.toByte()
            }
        }
        return Image.fromPixels(w, h, pixels, ColorType.ALPHA_8, "alpha8_${tx}_$ty")
    }

    private fun makeColorTile(w: Int, h: Int, tx: Int, ty: Int): Image {
        val pixels = ByteArray(w * h * 4)
        val r = ((tx * 85) and 0xFF).toByte()
        val g = ((ty * 85) and 0xFF).toByte()
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = (y * w + x) * 4
                pixels[i] = r; pixels[i + 1] = g
                pixels[i + 2] = 0x80.toByte(); pixels[i + 3] = 0xFF.toByte()
            }
        }
        return Image.fromPixels(w, h, pixels, sourceId = "color_${tx}_$ty")
    }

    private fun drawCheckerboard(canvas: GmCanvas, size: Int) {
        val lightGray = Paint(color = Color.fromRGBA(0.5f, 0.5f, 0.5f, 1f))
        val darkGray = Paint(color = Color.fromRGBA(0.25f, 0.25f, 0.25f, 1f))
        val totalW = kM * kTileW
        val totalH = 2 * kN * kTileH
        var y = 0
        while (y < totalH) {
            var x = 0
            while (x < totalW) {
                val parity = ((x / size) + (y / size)) % 2
                canvas.drawRect(
                    Rect(x.toFloat(), y.toFloat(), (x + size).toFloat(), (y + size).toFloat()),
                    if (parity == 0) lightGray else darkGray,
                )
                x += size
            }
            y += size
        }
    }

    private companion object {
        const val kM = 4
        const val kN = 4
        const val kTileW = 50
        const val kTileH = 50
    }
}
