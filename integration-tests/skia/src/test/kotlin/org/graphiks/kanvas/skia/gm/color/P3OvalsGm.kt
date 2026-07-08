package org.graphiks.kanvas.skia.gm.color

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/** Tests P3 color-space oval rendering with circles, ovals, and rotated ovals in red. */
class P3OvalsGm : SkiaGm {
    override val name = "p3_ovals"
    override val renderFamily = RenderFamily.COLOR
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 450
    override val height = 320

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val red = Color.fromRGBA(1f, 0f, 0f, 1f)

        canvas.drawCircle(40f, 40f, 30f, Paint(color = red, antiAlias = true))

        canvas.translate(0f, 80f)

        canvas.drawOval(Rect.fromLTRB(20f, 10f, 60f, 70f), Paint(color = red, antiAlias = true))

        canvas.translate(0f, 80f)

        canvas.save()
        canvas.translate(40f, 40f)
        canvas.rotate(45f)
        canvas.drawOval(Rect.fromLTRB(-20f, -30f, 20f, 30f), Paint(color = red, antiAlias = true))
        canvas.restore()
    }
}
