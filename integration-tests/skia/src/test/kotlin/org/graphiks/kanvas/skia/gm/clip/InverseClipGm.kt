package org.graphiks.kanvas.skia.gm.clip

import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

class InverseClipGm : SkiaGm {
    override val name = "inverseclip"
    override val renderFamily = RenderFamily.CLIP
    override val minSimilarity = 0.0
    override val width = 400
    override val height = 400

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val clip = Path {
            moveTo(195.448f, 31f)
            cubicTo(97.9925f, 31f, 18.99f, 105.23f, 18.99f, 196.797f)
            cubicTo(18.99f, 288.365f, 97.9925f, 362.595f, 195.448f, 362.595f)
            cubicTo(292.905f, 362.595f, 371.905f, 288.365f, 371.905f, 196.797f)
            cubicTo(371.905f, 105.23f, 292.905f, 31f, 195.448f, 31f)
            close()
        }.apply { fillType = FillType.INVERSE_WINDING }
        canvas.clipRect(Rect(0f, 0f, 400f, 400f))
        canvas.drawRect(Rect(0f, 0f, 400f, 400f), Paint(color = Color.BLUE))
        canvas.drawPath(clip, Paint(color = Color.WHITE))
    }
}
