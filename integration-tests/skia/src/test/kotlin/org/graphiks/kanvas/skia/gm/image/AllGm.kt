package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import kotlin.random.Random

/** Tests "all" image rendering with random-colored pixel grid blocks. */
class AllGm : SkiaGm {
    override val name = "all"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val rand = Random(0)
        for (y in 0 until height step 16) {
            for (x in 0 until width step 16) {
                val c = Color.fromRGBA(rand.nextFloat(), rand.nextFloat(), rand.nextFloat(), 1f)
                canvas.drawRect(Rect(x.toFloat(), y.toFloat(), (x + 15).toFloat(), (y + 15).toFloat()), Paint(color = c))
            }
        }
    }
}
