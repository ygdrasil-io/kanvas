package org.graphiks.kanvas.skia.gm.color

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/** Tests an "all" palette override with a row of custom ARGB color swatches. */
class AllGm : SkiaGm {
    override val name = "all"
    override val renderFamily = RenderFamily.COLOR
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f, 1f)
        canvas.drawString("All Palette Override", 20f, 40f,
            org.graphiks.kanvas.text.Font(Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!, 24f), Paint(color = Color.BLACK))
        val palette = listOf(
            0xff310b55.toInt(), 0xff510970.toInt(), 0xff76078f.toInt(),
            0xff9606aa.toInt(), 0xffb404c4.toInt(), 0xfffa00ff.toInt(),
            0xffffff00.toInt(), 0xff888888.toInt(),
        )
        for ((i, c) in palette.withIndex()) {
            canvas.drawRect(Rect.fromXYWH(20f + i * 60f, 80f, 50f, 140f),
                Paint(color = Color.fromArgbInt(c)))
        }
    }
}
