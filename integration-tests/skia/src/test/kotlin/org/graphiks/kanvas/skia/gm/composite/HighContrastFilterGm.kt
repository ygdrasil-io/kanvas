package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/highcontrastfilter.cpp::HighContrastFilterGM` (800 x 420).
 * 4 x 2 grid of high-contrast color filter variants.
 * @see https://github.com/google/skia/blob/main/gm/highcontrastfilter.cpp
 */
class HighContrastFilterGm : SkiaGm {
    override val name = "highcontrastfilter"
    override val renderFamily = RenderFamily.COMPOSITE
    override val minSimilarity = 0.0
    override val width = 800
    override val height = 420

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val kSize = 200f

        // 8 configs: grayscale (T/F) x invertStyle x contrast
        data class Config(val grayscale: Boolean)

        for (i in 0 until 8) {
            val x = kSize * (i % 4)
            val y = kSize * (i / 4)
            canvas.save()
            canvas.translate(x, y)
            canvas.scale(kSize, kSize)
            drawScene(canvas)
            drawLabel(canvas, i)
            canvas.restore()
        }
    }

    private fun drawScene(c: GmCanvas) {
        val layerBounds = Rect.fromLTRB(0f, 0f, 1f, 1f)
        val xferPaint = Paint(colorFilter = ColorFilter.HighContrast)
        c.saveLayer(layerBounds, xferPaint)

        val paint = Paint(color = argb(255, 0x66, 0x11, 0x11))
        c.drawRect(Rect.fromLTRB(0.1f, 0.2f, 0.9f, 0.4f), paint)

        val font = Font(typeface, size = kFontScale, antiAlias = false)

        c.drawString("A", 0.15f, 0.35f, font, Paint(color = argb(255, 0xbb, 0x77, 0x77)))
        c.drawRect(Rect.fromLTRB(0.1f, 0.8f, 0.9f, 1.0f), Paint(color = argb(255, 0xcc, 0xcc, 0xff)))
        c.drawString("Z", 0.75f, 0.95f, font, Paint(color = argb(255, 0x88, 0x88, 0xbb)))

        val pts = arrayOf(Point(0f, 0f), Point(1f, 0f))
        val pos = floatArrayOf(0.2f, 0.8f)

        var gradPaint = Paint(
            shader = Shader.LinearGradient(
                start = pts[0], end = pts[1],
                stops = listOf(
                    GradientStop(0.2f, Color.WHITE),
                    GradientStop(0.8f, Color.BLACK),
                ),
                tileMode = TileMode.CLAMP,
            ),
        )
        c.drawRect(Rect.fromLTRB(0.1f, 0.4f, 0.9f, 0.6f), gradPaint)

        gradPaint = Paint(
            shader = Shader.LinearGradient(
                start = pts[0], end = pts[1],
                stops = listOf(
                    GradientStop(0.2f, Color.GREEN),
                    GradientStop(0.8f, Color.WHITE),
                ),
                tileMode = TileMode.CLAMP,
            ),
        )
        c.drawRect(Rect.fromLTRB(0.1f, 0.6f, 0.9f, 0.8f), gradPaint)

        c.restore()
    }

    private fun drawLabel(c: GmCanvas, configIndex: Int) {
        val grayscale = configIndex >= 4
        val invertStyle = when (configIndex % 4) {
            1 -> "InvBright"
            2 -> "InvLight"
            3 -> "InvLight+contrast"
            else -> "NoInvert"
        }
        val grayPrefix = if (grayscale) "Gray " else ""
        val label = "${grayPrefix}${invertStyle}"

        val font = Font(typeface, size = 0.075f)
        val textWidth = font.measureText(label)
        c.drawString(label, 0.5f - textWidth / 2f, 0.16f, font, Paint())
    }

    companion object {
        private const val kFontScale = 0.15f
    }

    private fun argb(a: Int, r: Int, g: Int, b: Int): Color =
        Color.fromRGBA(r / 255f, g / 255f, b / 255f, a / 255f)
}
