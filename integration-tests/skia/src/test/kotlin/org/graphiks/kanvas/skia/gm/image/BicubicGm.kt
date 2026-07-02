package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.canvas.drawLine
import org.graphiks.kanvas.canvas.drawCircle
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

class BicubicGm : SkiaGm {
    override val name = "bicubic"
    override val renderFamily = RenderFamily.IMAGE
    override val minSimilarity = 0.0
    override val width = 300
    override val height = 320

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0f, 0f, 0f)

        val img = makeImage()

        canvas.scale(40f, 8f)
        for (i in 0 until 3) {
            canvas.drawImage(img, Rect(0f, 0f, 7f, 7f))
            canvas.translate(0f, 8f)
        }

        val r = Rect(0f, 0f, 7f, 7f)
        for (i in 0 until 2) {
            canvas.drawRect(r, Paint(shader = Shader.Image(img, TileMode.CLAMP, TileMode.CLAMP)))
            canvas.translate(0f, 8f)
        }
    }

    private fun makeImage(): Image {
        val surf = Surface(7, 7)
        surf.canvas {
            drawColor(Color.BLACK)
            val paint = Paint(color = Color.WHITE, style = org.graphiks.kanvas.paint.PaintStyle.STROKE)
            drawLine(3.5f, 0f, 3.5f, 8f, paint)
        }
        return surf.makeImageSnapshot()
    }
}
