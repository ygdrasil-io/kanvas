package org.graphiks.kanvas.skia.gm.gradient

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

class GradientsPowerlessHueLchGm : SkiaGm {
    override val name = "gradients_powerless_hue_LCH"
    override val renderFamily = RenderFamily.GRADIENT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 415
    override val height = 330

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        drawPowerlessHueGradients(canvas)
    }
}

class GradientsPowerlessHueOklchGm : SkiaGm {
    override val name = "gradients_powerless_hue_OKLCH"
    override val renderFamily = RenderFamily.GRADIENT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 415
    override val height = 330

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        drawPowerlessHueGradients(canvas)
    }
}

class GradientsPowerlessHueHslGm : SkiaGm {
    override val name = "gradients_powerless_hue_HSL"
    override val renderFamily = RenderFamily.GRADIENT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 415
    override val height = 330

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        drawPowerlessHueGradients(canvas)
    }
}

class GradientsPowerlessHueHwbGm : SkiaGm {
    override val name = "gradients_powerless_hue_HWB"
    override val renderFamily = RenderFamily.GRADIENT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 415
    override val height = 330

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        drawPowerlessHueGradients(canvas)
    }
}

private fun drawPowerlessHueGradients(canvas: GmCanvas) {
    drawCheckerboard(canvas)

    fun nextRow() {
        canvas.restore()
        canvas.translate(0f, 25f)
        canvas.save()
    }

    fun gradient(stops: List<GradientStop>) {
        val shader = Shader.LinearGradient(
            Point(0f, 0f), Point(200f, 0f), stops, TileMode.CLAMP,
        )
        canvas.drawRect(Rect(0f, 0f, 200f, 20f), Paint(shader = shader))
        canvas.translate(205f, 0f)
    }

    canvas.translate(5f, 5f)
    canvas.save()

    gradient(listOf(GradientStop(0f, Color.WHITE), GradientStop(1f, Color.BLUE)))
    gradient(listOf(GradientStop(0f, argb(255, 252, 252, 255)), GradientStop(1f, Color.BLUE)))
    nextRow()

    gradient(listOf(GradientStop(0f, Color.BLACK), GradientStop(1f, Color.BLUE)))
    gradient(listOf(GradientStop(0f, argb(255, 0, 0, 3)), GradientStop(1f, Color.BLUE)))
    nextRow()

    gradient(listOf(GradientStop(0f, Color.TRANSPARENT), GradientStop(1f, Color.BLUE)))
    gradient(listOf(GradientStop(0f, Color.TRANSPARENT), GradientStop(1f, Color.BLUE)))
    nextRow()

    gradient(listOf(GradientStop(0f, Color.TRANSPARENT), GradientStop(1f, Color.BLUE)))
    gradient(listOf(GradientStop(0f, Color.TRANSPARENT), GradientStop(1f, Color.BLUE)))
    nextRow()

    gradient(listOf(GradientStop(0f, Color.TRANSPARENT), GradientStop(1f, Color.BLUE)))
    gradient(listOf(GradientStop(0f, Color.TRANSPARENT), GradientStop(1f, Color.BLUE)))
    nextRow()

    gradient(listOf(GradientStop(0f, Color.TRANSPARENT), GradientStop(1f, Color.BLUE)))
    gradient(listOf(GradientStop(0f, Color.TRANSPARENT), GradientStop(1f, Color.BLUE)))
    nextRow()

    gradient(listOf(GradientStop(0f, Color.RED), GradientStop(0.5f, Color.WHITE), GradientStop(1f, Color.BLUE)))
    gradient(listOf(
        GradientStop(0f, Color.RED),
        GradientStop(0.5f, argb(255, 255, 252, 252)),
        GradientStop(0.5f, argb(255, 252, 252, 255)),
        GradientStop(1f, Color.BLUE),
    ))
    nextRow()

    gradient(listOf(GradientStop(0f, Color.RED), GradientStop(0.5f, Color.BLACK), GradientStop(1f, Color.BLUE)))
    gradient(listOf(
        GradientStop(0f, Color.RED),
        GradientStop(0.5f, argb(255, 3, 0, 0)),
        GradientStop(0.5f, argb(255, 0, 0, 3)),
        GradientStop(1f, Color.BLUE),
    ))
    nextRow()

    gradient(listOf(GradientStop(0f, Color.RED), GradientStop(0.5f, Color.TRANSPARENT), GradientStop(1f, Color.BLUE)))
    gradient(listOf(
        GradientStop(0f, Color.RED),
        GradientStop(0.5f, Color.TRANSPARENT),
        GradientStop(0.5f, Color.TRANSPARENT),
        GradientStop(1f, Color.BLUE),
    ))
    nextRow()

    canvas.restore()
}

private fun argb(a: Int, r: Int, g: Int, b: Int): Color =
    Color.fromRGBA(r / 255f, g / 255f, b / 255f, a / 255f)

private fun drawCheckerboard(canvas: GmCanvas) {
    val c1 = Color.fromRGBA(0.6f, 0.6f, 0.6f, 1f)
    val c2 = Color.fromRGBA(0.4f, 0.4f, 0.4f, 1f)
    for (y in 0 until 400 step 8) {
        for (x in 0 until 420 step 8) {
            val dark = ((x / 8) + (y / 8)) % 2 == 0
            canvas.drawRect(
                Rect(x.toFloat(), y.toFloat(), (x + 8).toFloat(), (y + 8).toFloat()),
                Paint(color = if (dark) c1 else c2),
            )
        }
    }
}
