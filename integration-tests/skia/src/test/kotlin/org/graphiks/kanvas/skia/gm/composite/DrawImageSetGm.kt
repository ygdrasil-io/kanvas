package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import kotlin.math.sqrt

/**
 * Port of Skia's `gm/draw_image_set.cpp::DrawImageSet`.
 * Tests drawing image rects with various matrix transforms, blend modes, and color filters.
 * @see https://github.com/google/skia/blob/main/gm/draw_image_set.cpp
 */
class DrawImageSetGm : SkiaGm {
    override val name = "draw_image_set"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 1000
    override val height = 725

    private data class Tile(val src: RectF32, val dst: RectF32, val alpha: Float)

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val mw = (kM * kTileW).toFloat()
        val nh = (kN * kTileH).toFloat()
        val d = sqrt(mw * mw + nh * nh)

        val backingImage = makeStripedImage(kM * kTileW, kN * kTileH, kTileW, kTileH)

        val tiles = mutableListOf<Tile>()
        for (y in 0 until kN) {
            for (x in 0 until kM) {
                tiles.add(Tile(
                    src = RectF32(0f, 0f, kTileW.toFloat(), kTileH.toFloat()),
                    dst = RectF32(
                        (x * kTileW).toFloat(), (y * kTileH).toFloat(),
                        ((x + 1) * kTileW).toFloat(), ((y + 1) * kTileH).toFloat(),
                    ),
                    alpha = 1f,
                ))
            }
        }

        val matrices = arrayOfNulls<Matrix3x3F32>(4)
        matrices[0] = Matrix3x3F32.translation(d / 3f, 0f) * Matrix3x3F32.rotation(30f)
        matrices[1] = Matrix3x3F32.translation(d, 50f) * Matrix3x3F32.of(
            1f, 0f, 0f, 0f, 1f, 0f, 0.0008f, 0f, 1f,
        )
        matrices[2] = Matrix3x3F32.translation(d, 2.6f * d) *
            Matrix3x3F32.scaling(0.6f, 1.05f) *
            Matrix3x3F32.skewing(0.5f, -1.15f) *
            Matrix3x3F32.rotation(-60f)
        matrices[3] = Matrix3x3F32.translation(100f, d) * Matrix3x3F32.of(
            -1f, 0f, mw, 0f, -0.5f, nh * 0.5f, 0f, 0f, 1f,
        )

        for (filterLinear in booleanArrayOf(false, true)) {
            for (mi in matrices.indices) {
                val mat = matrices[mi] ?: continue
                val redPaint = Paint(color = ColorARGB.Red, antiAlias = true, strokeWidth = 0f)

                for (x in 1 until kM) {
                    val p1 = mat.transform(Point2F32(x * kTileW.toFloat(), 0f))
                    val p2 = mat.transform(Point2F32(x * kTileW.toFloat(), nh))
                    canvas.drawLine(p1.x, p1.y, p2.x, p2.y, redPaint)
                }
                for (y in 1 until kN) {
                    val p1 = mat.transform(Point2F32(0f, y * kTileH.toFloat()))
                    val p2 = mat.transform(Point2F32(mw, y * kTileH.toFloat()))
                    canvas.drawLine(p1.x, p1.y, p2.x, p2.y, redPaint)
                }

                canvas.save()
                canvas.concat(mat)
                for (tile in tiles) {
                    canvas.drawImageRect(backingImage, tile.src, tile.dst)
                }
                canvas.restore()
            }

            val tile0 = tiles[0]
            val insetSrc = RectF32.ofOriginSize(kTileW / 4f, kTileH / 4f, kTileW / 2f, kTileH / 2f)
            val exclusionDst = RectF32.ofOriginSize(d / 4f, 2f * d, 1.5f * kTileW, 1.5f * kTileH)
            val linearDst = RectF32.ofOriginSize(d / 4f + 1.5f * kTileW + 8f, 2f * d, 1.5f * kTileW, 1.5f * kTileH)

            canvas.save()
            canvas.rotate(3f)
            canvas.drawImageRect(backingImage, insetSrc, exclusionDst,
                Paint(blendMode = BlendMode.EXCLUSION))
            canvas.drawImageRect(backingImage, insetSrc, linearDst,
                Paint(blendMode = BlendMode.EXCLUSION, colorFilter = ColorFilter.LinearToSRGB))
            canvas.restore()

            canvas.translate(2f * d, 0f)
        }
    }

    private fun makeStripedImage(w: Int, h: Int, tileW: Int, tileH: Int): Image {
        val pixels = ByteArray(w * h * 4)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val tx = x / tileW; val ty = y / tileH
                val i = (y * w + x) * 4
                val (r, g, b) = when ((tx + ty) % 4) {
                    0 -> Triple(0x00, 0xCC, 0xFF)
                    1 -> Triple(0xFF, 0x00, 0xFF)
                    2 -> Triple(0xFF, 0xFF, 0x00)
                    else -> Triple(0x33, 0x33, 0x33)
                }
                pixels[i] = r.toByte(); pixels[i + 1] = g.toByte()
                pixels[i + 2] = b.toByte(); pixels[i + 3] = 0xFF.toByte()
            }
        }
        return Image.fromPixels(w, h, pixels, sourceId = "draw_image_set")
    }

    private companion object {
        const val kM = 4
        const val kN = 3
        const val kTileW = 30
        const val kTileH = 60
    }
}
