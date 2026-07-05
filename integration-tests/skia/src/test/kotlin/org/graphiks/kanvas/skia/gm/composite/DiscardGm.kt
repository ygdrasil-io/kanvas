package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import kotlin.random.Random

/**
 * Port of Skia's `gm/discard.cpp::DiscardGM` (100x100).
 * Repeats 100 (10x10) iterations of: clear a small offscreen surface,
 * randomly pick a colour via drawColor/clear/drawRect, draw the
 * offscreen onto the main canvas at (10*x, 10*y).
 * @see https://github.com/google/skia/blob/main/gm/discard.cpp
 */
class DiscardGm : SkiaGm {
    override val name = "discard"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.SLOW
    override val minSimilarity = 0.0
    override val width = 100
    override val height = 100

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val cw = 10
        val ch = 10
        val surf = Surface(cw, ch)
        val random = Random(42)

        canvas.drawColor(r = 0f, g = 0f, b = 0f)

        for (x in 0 until 10) {
            for (y in 0 until 10) {
                val r = ((random.nextInt(192) + 64) and 0xFF) / 255f
                val g = ((random.nextInt(192) + 64) and 0xFF) / 255f
                val b = ((random.nextInt(192) + 64) and 0xFF) / 255f
                val color = Color.fromRGBA(r, g, b)

                surf.canvas {
                    when (random.nextInt(3)) {
                        0 -> drawColor(color)
                        1 -> clear(color)
                        else -> drawRect(
                            Rect(0f, 0f, cw.toFloat(), ch.toFloat()),
                            Paint(shader = Shader.SolidColor(color)),
                        )
                    }
                }
                val img = surf.makeImageSnapshot()
                canvas.drawImage(img, Rect(10f * x, 10f * y, 10f * x + cw, 10f * y + ch))
            }
        }
    }
}
