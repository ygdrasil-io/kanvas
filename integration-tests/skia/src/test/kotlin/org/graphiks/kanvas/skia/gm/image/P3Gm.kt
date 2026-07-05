package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

class P3Gm : SkiaGm {
    override val name = "p3"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 450
    override val height = 1300

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val red = Color.RED
        val green = Color.GREEN
        val blue = Color.BLUE

        canvas.drawRect(Rect(10f, 10f, 70f, 70f), Paint(color = red))
        canvas.translate(0f, 80f)

        canvas.drawRect(Rect(10f, 10f, 70f, 70f), Paint(color = red))
        canvas.translate(0f, 80f)

        drawGradientPanel(canvas, red, green)
        canvas.translate(0f, 80f)

        drawGradientPanel(canvas, red, green)
        canvas.translate(0f, 80f)

        drawGradientPanel(canvas, red, green)
        canvas.translate(0f, 80f)

        drawGradientPanel(canvas, red, green)
        canvas.translate(0f, 80f)

        canvas.drawRect(
            Rect(10f, 10f, 70f, 70f),
            Paint(shader = Shader.LinearGradient(
                start = Point(10.5f, 10.5f), end = Point(10.5f, 69.5f),
                stops = listOf(
                    GradientStop(0f, blue),
                    GradientStop(0.5f, green),
                    GradientStop(1f, red),
                ),
            )),
        )
        canvas.translate(0f, 80f)

        val maskPixels = ByteArray(256) { (255 - it).toByte() }
        val mask = Image.fromPixels(16, 16, maskPixels, ColorType.ALPHA_8, "a8_mask")
        val tint = Color.RED

        canvas.drawImage(mask, Rect(10f, 10f, 26f, 26f), Paint(color = tint))
        canvas.translate(0f, 80f)

        canvas.save()
        canvas.translate(10f, 10f)
        canvas.drawRect(Rect(0f, 0f, 16f, 16f), Paint(color = tint, shader = Shader.Image(mask)))
        canvas.restore()
        canvas.translate(0f, 80f)

        canvas.drawImageRect(mask, Rect(0f, 0f, 16f, 16f), Rect(10f, 10f, 70f, 70f), Paint(color = tint))
        canvas.translate(0f, 80f)

        canvas.save()
        canvas.translate(10f, 10f)
        canvas.scale(3.75f, 3.75f)
        canvas.drawRect(Rect(0f, 0f, 16f, 16f), Paint(color = tint, shader = Shader.Image(mask)))
        canvas.restore()
    }

    private fun drawGradientPanel(c: GmCanvas, c0: Color, c1: Color) {
        c.drawRect(
            Rect(10f, 10f, 70f, 70f),
            Paint(shader = Shader.LinearGradient(
                start = Point(10.5f, 10.5f), end = Point(69.5f, 69.5f),
                stops = listOf(GradientStop(0f, c0), GradientStop(1f, c1)),
            )),
        )
    }
}
