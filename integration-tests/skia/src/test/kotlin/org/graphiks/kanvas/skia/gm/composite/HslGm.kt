package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/hsl.cpp::DEF_SIMPLE_GM(HSL_duck, canvas, 1110, 620)`.
 * Renders ducky.png through HSL blend modes over a gradient background.
 * @see https://github.com/google/skia/blob/main/gm/hsl.cpp
 */
class HslGm : SkiaGm {
    override val name = "HSL_duck"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 1110
    override val height = 620

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val srcBytes = loadResource("images/ducky.png")
        val image = srcBytes?.let { Image.decode(it) } ?: return

        val dst = makeGrad(image.width.toFloat())
        val r = Rect.fromXYWH(0f, 0f, image.width.toFloat(), image.height.toFloat())

        canvas.translate(10f, 50f)
        canvas.scale(0.5f, 0.5f)

        val recs = listOf(
            BlendMode.HUE to "Hue",
            BlendMode.SATURATION to "Saturation",
            BlendMode.COLOR to "Color",
            BlendMode.LUMINOSITY to "Luminosity",
        )

        val font = Font(typeface, size = 40f)

        // Column labels
        canvas.save()
        for ((_, name) in recs) {
            canvas.drawString(name, 150f, -20f, font, Paint())
            canvas.translate(r.width + 10f, 0f)
        }
        canvas.restore()

        for (srcA in listOf(1.0f, 0.5f)) {
            canvas.save()
            for ((mode, _) in recs) {
                val bgPaint = Paint(shader = dst)
                canvas.drawRect(r, bgPaint)

                val srcPaint = Paint(blendMode = mode, color = Color.fromRGBA(1f, 1f, 1f, srcA))
                canvas.drawImage(image, r, srcPaint)
                canvas.translate(r.width + 10f, 0f)
            }
            val str = "alpha ${"%.1f".format(srcA)}"
            canvas.drawString(str, 10f, r.height / 2f, font, Paint())
            canvas.restore()
            canvas.translate(0f, r.height + 10f)
        }
    }

    private fun makeGrad(width: Float): Shader {
        val colors = listOf(
            argb(255, 0, 204, 204),
            argb(255, 0, 0, 204),
            argb(255, 204, 0, 204),
            argb(255, 204, 0, 0),
            argb(255, 204, 204, 0),
            argb(255, 0, 204, 0),
        )
        val stops = colors.mapIndexed { i, c ->
            GradientStop(i.toFloat() / (colors.size - 1).toFloat(), c)
        }
        return Shader.LinearGradient(
            start = Point(0f, 0f), end = Point(width, 0f),
            stops = stops, tileMode = TileMode.CLAMP,
        )
    }

    private fun loadResource(path: String): ByteArray? {
        return this::class.java.classLoader?.getResourceAsStream(path)?.readBytes()
    }

    private fun argb(a: Int, r: Int, g: Int, b: Int): Color =
        Color.fromRGBA(r / 255f, g / 255f, b / 255f, a / 255f)
}
