package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import kotlin.random.Random

/** Simplified nested path test with random-colored background and a rect-with-hole path. */
class NestedSimpleGm : SkiaGm {
    override val name = "nested"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 269
    override val height = 134

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val rand = Random(0)
        for (y in 0 until height step 10) {
            for (x in 0 until width step 10) {
                val r = rand.nextInt(256) or 0xFF000000.toInt()
                val c = Color.fromRGBA(((r shr 16) and 0xFF) / 255f, ((r shr 8) and 0xFF) / 255f, (r and 0xFF) / 255f, 1f)
                canvas.drawRect(Rect(x.toFloat(), y.toFloat(), (x + 10).toFloat(), (y + 10).toFloat()), Paint(color = c))
            }
        }
        val path = Path { }.apply {
            addRect(Rect(2f, 2f, 42f, 42f))
            addRect(Rect(12f, 12f, 32f, 32f))
        }
        canvas.drawPath(path, Paint(antiAlias = true, color = Color.BLACK))
    }
}
