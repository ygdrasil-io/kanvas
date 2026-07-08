package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/** Tests simple clip operations with rect clip and circular path clip. */
class SimpleclipGm : SkiaGm {
    override val name = "simpleclip"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f, 1f)
        canvas.save()
        canvas.clipRect(Rect.fromLTRB(30f, 30f, 200f, 200f))
        canvas.drawRect(Rect.fromXYWH(0f, 0f, 256f, 256f),
            Paint(color = Color.fromRGBA(0f, 0f, 1f, 0.3f)))
        canvas.drawCircle(128f, 100f, 60f, Paint(color = Color.RED))
        canvas.drawCircle(100f, 150f, 50f, Paint(color = Color.fromRGBA(0f, 1f, 0f, 0.7f)))
        canvas.restore()
        canvas.save()
        val clipPath = Path { }.apply { addCircle(180f, 80f, 50f) }
        canvas.clipPath(clipPath)
        canvas.drawRect(Rect.fromXYWH(120f, 20f, 120f, 120f),
            Paint(color = Color.fromRGBA(1f, 1f, 0f, 0.5f)))
        canvas.restore()
    }
}
