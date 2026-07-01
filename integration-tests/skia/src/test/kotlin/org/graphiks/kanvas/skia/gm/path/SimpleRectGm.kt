package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import kotlin.random.Random

/**
 * Port of SimpleRectGM.
 *
 * Draws 10,000 small filled rectangles at random positions with
 * deterministic random colors. The original used `SkRandom` and
 * `colorToRGB565` for pixel-exact reference matching; we use
 * `kotlin.random.Random` directly so new references must be generated.
 */
class SimpleRectGm : SkiaGm {
    override val name = "simplerect"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 800
    override val height = 800

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.translate(1f, 1f)
        val min = -20f
        val max = 800f
        val size = 20f
        val rand = Random(0)
        repeat(10_000) {
            val raw = rand.nextInt()
            val colorInt = raw or (0xFF000000.toInt())
            val a = ((colorInt ushr 24) and 0xFF) / 255f
            val r = ((colorInt ushr 16) and 0xFF) / 255f
            val g = ((colorInt ushr 8) and 0xFF) / 255f
            val b = (colorInt and 0xFF) / 255f
            val paint = Paint(color = Color.fromRGBA(r, g, b, a))
            val x = rand.nextFloat() * (max - min) + min
            val y = rand.nextFloat() * (max - min) + min
            val w = rand.nextFloat() * size
            val h = rand.nextFloat() * size
            canvas.drawRect(Rect.fromXYWH(x, y, w, h), paint)
        }
    }
}
