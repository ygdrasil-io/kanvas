package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/aarectmodes.cpp`.
 * Tests 12 Porter-Duff blend modes with 4 alpha configurations (opaque-on-opaque,
 * fractional-on-opaque, opaque-on-fractional, fractional-on-fractional).
 * Each cell: checkerboard BG → saveLayer → blue oval (a0) → red rect (a1, mode).
 * @see https://github.com/google/skia/blob/main/gm/aarectmodes.cpp
 */
class AaRectModesGm : SkiaGm {
    override val name = "aarectmodes"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 30.8
    override val width = 640
    override val height = 480

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val cell = 64f
        val modes = listOf(
            BlendMode.CLEAR, BlendMode.SRC, BlendMode.DST,
            BlendMode.SRC_OVER, BlendMode.DST_OVER, BlendMode.SRC_IN,
            BlendMode.DST_IN, BlendMode.SRC_OUT, BlendMode.DST_OUT,
            BlendMode.SRC_ATOP, BlendMode.DST_ATOP, BlendMode.XOR,
        )
        val alphaValues = listOf(0xFF, 0x88, 0x88)

        canvas.translate(4f, 4f)

        for (alpha in 0 until 4) {
            canvas.save()
            canvas.save()

            for (i in modes.indices) {
                if (i == 6) {
                    canvas.restore()
                    canvas.translate(cell * 5f, 0f)
                    canvas.save()
                }

                drawCheckerboard(canvas, cell)

                canvas.saveLayer(Rect(0f, 0f, cell, cell), null)

                val a0 = alphaValues[alpha and 1]
                val a1 = alphaValues[alpha and 2]

                val inset = cell / 10f
                val ovalRect = Rect(inset, inset, cell - inset, cell - inset)
                canvas.drawOval(ovalRect, Paint(
                    color = Color.fromRGBA(0f, 0f, 1f, a0 / 255f),
                    antiAlias = true,
                ))

                val offset = 1f / 3f
                val redRect = Rect.fromLTRB(
                    cell / 4f + offset, cell / 4f + offset,
                    cell * 3f / 4f + offset, cell * 3f / 4f + offset,
                )
                canvas.drawRect(redRect, Paint(
                    color = Color.fromRGBA(1f, 0f, 0f, a1 / 255f),
                    blendMode = modes[i],
                    antiAlias = true,
                ))

                canvas.restore()
                canvas.translate(0f, cell * 5f / 4f)
            }

            canvas.restore()
            canvas.restore()
            canvas.translate(cell * 5f / 4f, 0f)
        }
    }

    private fun drawCheckerboard(canvas: GmCanvas, cell: Float) {
        val tile = 12f
        val white = Paint(Color.WHITE)
        val lightGray = Paint(Color.fromRGBA(0xCE / 255f, 0xCF / 255f, 0xCE / 255f, 1f))
        var y = 0f
        while (y < cell) {
            var x = 0f
            while (x < cell) {
                val parity = (((x / tile).toInt() + (y / tile).toInt()) and 1)
                canvas.drawRect(Rect(x, y, x + tile, y + tile), if (parity == 0) white else lightGray)
                x += tile
            }
            y += tile
        }
    }
}
