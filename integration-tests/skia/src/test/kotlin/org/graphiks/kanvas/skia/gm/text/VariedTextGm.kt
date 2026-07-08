package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import kotlin.random.Random

/** Tests varied text rendering with random-colored circles and lines. */
class VariedTextGm : SkiaGm {
    override val name = "variedtext"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 480

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f)
        val rand = Random(42)
        for (i in 0 until 30) {
            val x = rand.nextFloat() * width
            val y = rand.nextFloat() * height
            val r = rand.nextFloat()
            val g = rand.nextFloat()
            val b = rand.nextFloat()
            val size = rand.nextFloat() * 24f + 8f
            canvas.drawCircle(x, y, size / 3f, Paint(color = Color.fromRGBA(r, g, b, 1f)))
            canvas.drawRect(Rect(x + size, y - 2f, x + size + 40f, y + 2f), Paint(color = Color.fromRGBA(r, g, b, 1f)))
        }
    }
}
