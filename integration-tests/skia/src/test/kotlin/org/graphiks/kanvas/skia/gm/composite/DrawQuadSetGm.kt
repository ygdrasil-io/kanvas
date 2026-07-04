package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

private const val kTileWidth = 40f
private const val kTileHeight = 30f
private const val kRowCount = 4
private const val kColCount = 3

private val tileSetNames = arrayOf("Local", "Aligned", "Green", "Multicolor")

private fun dqsDrawText(canvas: GmCanvas, text: String, typeface: org.graphiks.kanvas.text.Typeface) {
    val font = Font(typeface, 12f)
    canvas.drawString(text, 0f, 0f, font, Paint())
}

private fun dqsDrawGradientTilesLocal(canvas: GmCanvas) {
    for (i in 0 until kRowCount) {
        for (j in 0 until kColCount) {
            val tile = Rect.fromXYWH(j * kTileWidth, i * kTileHeight, kTileWidth, kTileHeight)
            val color = if ((i * kColCount + j) % 2 == 0) Color.BLUE else Color.WHITE
            canvas.drawRect(tile, Paint(color = color))
        }
    }
}

private fun dqsDrawGradientTilesAligned(canvas: GmCanvas) {
    for (i in 0 until kRowCount) {
        for (j in 0 until kColCount) {
            val tile = Rect.fromXYWH(j * kTileWidth, i * kTileHeight, kTileWidth, kTileHeight)
            canvas.drawRect(tile, Paint(color = Color.BLUE))
        }
    }
}

private fun dqsDrawColorTilesGreen(canvas: GmCanvas) {
    for (i in 0 until kRowCount) {
        for (j in 0 until kColCount) {
            val tile = Rect.fromXYWH(j * kTileWidth, i * kTileHeight, kTileWidth, kTileHeight)
            val color = Color.fromRGBA(51f / 255f, 204f / 255f, 77f / 255f, 1f)
            canvas.drawRect(tile, Paint(color = color))
        }
    }
}

private fun dqsDrawColorTilesMulticolor(canvas: GmCanvas) {
    for (i in 0 until kRowCount) {
        for (j in 0 until kColCount) {
            val tile = Rect.fromXYWH(j * kTileWidth, i * kTileHeight, kTileWidth, kTileHeight)
            val r = (i + 1f) / kRowCount
            val g = (j + 1f) / kColCount
            val color = Color.fromRGBA(r, g, 0.4f, 1f)
            canvas.drawRect(tile, Paint(color = color))
        }
    }
}

class DrawQuadSetGm : SkiaGm {
    override val name = "draw_quad_set"
    override val renderFamily = RenderFamily.COMPOSITE
    override val minSimilarity = 0.0
    override val width = 800
    override val height = 800

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val m0 = Matrix33.identity()
val m1 = Matrix33.translate(5.5f, 20.25f) * Matrix33.scale(0.9f, 0.7f)
val m2 = Matrix33.rotate(20f) * Matrix33.translate(15f, -20f)
val m3 = Matrix33.skew(0.5f, 0.25f)
val m4 = Matrix33.identity()

        val rowMatrices = arrayOf(m0, m1, m2, m3, m4)
        val matrixNames = arrayOf("Identity", "T+S", "Rotate", "Skew", "Perspective")
        val tileSets: Array<(GmCanvas) -> Unit> = arrayOf(
            ::dqsDrawGradientTilesLocal, ::dqsDrawGradientTilesAligned,
            ::dqsDrawColorTilesGreen, ::dqsDrawColorTilesMulticolor,
        )

        canvas.save()
        canvas.translate(110f, 20f)
        for (name in tileSetNames) {
            dqsDrawText(canvas, name, typeface)
            canvas.translate(kColCount * kTileWidth + 30f, 0f)
        }
        canvas.restore()
        canvas.translate(0f, 40f)

        for (i in rowMatrices.indices) {
            canvas.save()
            canvas.translate(10f, 0.5f * kRowCount * kTileHeight)
            dqsDrawText(canvas, matrixNames[i], typeface)
            canvas.translate(100f, -0.5f * kRowCount * kTileHeight)
            for (j in tileSets.indices) {
                canvas.save()
                drawTileBoundaries(canvas, rowMatrices[i])
                canvas.concat(rowMatrices[i])
                tileSets[j](canvas)
                canvas.restore()
                canvas.translate(kColCount * kTileWidth + 30f, 0f)
            }
            canvas.restore()
            canvas.translate(0f, kRowCount * kTileHeight + 20f)
        }
    }

    private fun drawTileBoundaries(canvas: GmCanvas, local: Matrix33) {
        val paint = Paint(color = Color.RED, antiAlias = true, style = PaintStyle.STROKE, strokeWidth = 0f)
        for (x in 1 until kColCount) {
            val p1 = local * Point(x * kTileWidth, 0f)
            val p2 = local * Point(x * kTileWidth, kRowCount * kTileHeight)
            canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint)
        }
        for (y in 1 until kRowCount) {
            val p1 = local * Point(0f, y * kTileHeight)
            val p2 = local * Point(kColCount * kTileWidth, y * kTileHeight)
            canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint)
        }
    }
}
