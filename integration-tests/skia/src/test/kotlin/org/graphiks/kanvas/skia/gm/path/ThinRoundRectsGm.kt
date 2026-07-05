package org.graphiks.kanvas.skia.gm.path

/**
 * Port of Skia's `gm/thinroundrects.cpp`.
 * Tests sub-pixel-width filled round-rects with varying corner radii.
 * @see https://github.com/google/skia/blob/main/gm/thinroundrects.cpp
 */

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.CornerRadii
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect

class ThinRoundRectsGm : SkiaGm {
    override val name = "thinroundrects"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 84.5
    override val width = 240
    override val height = 320

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0f, 0f, 0f, 1f)
        val white = Paint(color = Color.WHITE, antiAlias = true)
        val green = Paint(color = Color.GREEN, antiAlias = true)

        for (i in 0 until 8) {
            canvas.save()
            canvas.translate(i * 0.125f, i * 40f)
            drawVertRects(canvas, white)
            canvas.translate(40f, 0f)
            drawVertRects(canvas, green)
            canvas.restore()

            canvas.save()
            canvas.translate(80f, i * 40f + i * 0.125f)
            drawHorizRects(canvas, white)
            canvas.translate(40f, 0f)
            drawHorizRects(canvas, green)
            canvas.restore()

            canvas.save()
            canvas.translate(160f + i * 0.125f, i * 40f + i * 0.125f)
            drawSquares(canvas, white)
            canvas.translate(40f, 0f)
            drawSquares(canvas, green)
            canvas.restore()
        }
    }

    private fun drawVertRects(c: GmCanvas, p: Paint) {
        val radii = listOf(
            CornerRadii(1f / 32f, 2f / 32f),
            CornerRadii(3f / 32f, 1f / 32f),
            CornerRadii(2f / 32f, 3f / 32f),
            CornerRadii(1f / 32f, 3f / 32f),
        )
        val rects = arrayOf(
            Rect(1f, 1f, 5.0f, 21f),
            Rect(8f, 1f, 10.0f, 21f),
            Rect(13f, 1f, 14.0f, 21f),
            Rect(17f, 1f, 17.5f, 21f),
            Rect(21f, 1f, 21.25f, 21f),
            Rect(25f, 1f, 25.125f, 21f),
            Rect(29f, 1f, 29.0f, 21f),
        )
        for (r in rects) {
            val rrect = RRect(r, radii[0], radii[1], radii[2], radii[3])
            c.drawPath(Path { }.apply { addRRect(rrect) }, p)
        }
    }

    private fun drawHorizRects(c: GmCanvas, p: Paint) {
        val rects = arrayOf(
            Rect(1f, 1f, 21f, 5.0f),
            Rect(1f, 8f, 21f, 10.0f),
            Rect(1f, 13f, 21f, 14.0f),
            Rect(1f, 17f, 21f, 17.5f),
            Rect(1f, 21f, 21f, 21.25f),
            Rect(1f, 25f, 21f, 25.125f),
            Rect(1f, 29f, 21f, 29.0f),
        )
        for (r in rects) {
            val rrect = RRect(r,
                CornerRadii(1f / 32f, 2f / 32f),
                CornerRadii(3f / 32f, 2f / 32f),
                CornerRadii(3f / 32f, 4f / 32f),
                CornerRadii(1f / 32f, 4f / 32f),
            )
            c.drawPath(Path { }.apply { addRRect(rrect) }, p)
        }
    }

    private fun drawSquares(c: GmCanvas, p: Paint) {
        val rects = arrayOf(
            Rect(1f, 1f, 5.0f, 5.0f),
            Rect(8f, 8f, 10.0f, 10.0f),
            Rect(13f, 13f, 14.0f, 14.0f),
            Rect(17f, 17f, 17.5f, 17.5f),
            Rect(21f, 21f, 21.25f, 21.25f),
            Rect(25f, 25f, 25.125f, 25.125f),
            Rect(29f, 29f, 29.0f, 29.0f),
        )
        for (r in rects) {
            val rrect = RRect(r,
                CornerRadii(1f / 32f, 2f / 32f),
                CornerRadii(1f / 32f, 2f / 32f),
                CornerRadii(1f / 32f, 2f / 32f),
                CornerRadii(1f / 32f, 2f / 32f),
            )
            c.drawPath(Path { }.apply { addRRect(rrect) }, p)
        }
    }
}
