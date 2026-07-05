package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/lcdblendmodes.cpp::LcdBlendGM` (720 x 750).
 * Four-column matrix of blend-mode samples with text labels.
 * @see https://github.com/google/skia/blob/main/gm/lcdblendmodes.cpp
 */
class LcdBlendGm : SkiaGm {
    override val name = "lcdblendmodes"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = kWidth
    override val height = kHeight

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!
    private val fTextHeight = kPointSize.toFloat()

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        // Background checker
        val checker = Paint(
            shader = checkerboardShader(Color.BLACK, Color.WHITE, 4),
        )
        canvas.drawRect(Rect(0f, 0f, kWidth.toFloat(), kHeight.toFloat()), checker)

        // Render into offscreen surface, then composite
        val surface = Surface(kWidth, kHeight)
        surface.canvas {
            drawColumn(this, argb(255, 0, 0, 0), Color.WHITE, useGrad = false)
            translate(kColWidth.toFloat(), 0f)
            drawColumn(this, Color.WHITE, Color.BLACK, useGrad = false)
            translate(kColWidth.toFloat(), 0f)
            drawColumn(this, Color.GREEN, argb(255, 255, 0, 255), useGrad = false)
            translate(kColWidth.toFloat(), 0f)
            drawColumn(this, Color.fromRGBA(0f, 1f, 1f, 1f), argb(255, 255, 0, 255), useGrad = true)
        }
        val surfImage = surface.makeImageSnapshot()
        canvas.drawImage(surfImage, Rect(0f, 0f, kWidth.toFloat(), kHeight.toFloat()))
    }

    private fun drawColumn(canvas: Canvas, backgroundColor: Color, textColor: Color, useGrad: Boolean) {
        val gModes = listOf(
            BlendMode.CLEAR, BlendMode.SRC, BlendMode.DST,
            BlendMode.SRC_OVER, BlendMode.DST_OVER,
            BlendMode.SRC_IN, BlendMode.DST_IN,
            BlendMode.SRC_OUT, BlendMode.DST_OUT,
            BlendMode.SRC_ATOP, BlendMode.DST_ATOP,
            BlendMode.XOR, BlendMode.PLUS,
            BlendMode.MODULATE, BlendMode.SCREEN,
            BlendMode.OVERLAY, BlendMode.DARKEN,
            BlendMode.LIGHTEN, BlendMode.COLOR_DODGE,
            BlendMode.COLOR_BURN, BlendMode.HARD_LIGHT,
            BlendMode.SOFT_LIGHT, BlendMode.DIFFERENCE,
            BlendMode.EXCLUSION, BlendMode.MULTIPLY,
            BlendMode.HUE, BlendMode.SATURATION,
            BlendMode.COLOR, BlendMode.LUMINOSITY,
        )

        canvas.drawRect(Rect.fromXYWH(0f, 0f, kColWidth.toFloat(), kHeight.toFloat()), Paint(color = backgroundColor))

        var y = fTextHeight
        for (mode in gModes) {
            var paint = Paint(color = textColor, blendMode = mode)
            val font = Font(typeface, size = fTextHeight)
            if (useGrad) {
                val rr = Rect.fromXYWH(0f, y - fTextHeight, kColWidth.toFloat(), fTextHeight)
                paint = paint.copy(shader = makeShader(rr))
            }
            val s = mode.name.lowercase()
            canvas.drawString(s, 0f, y, font, paint)
            y += fTextHeight
        }
    }

    private fun makeShader(bounds: Rect): Shader = Shader.LinearGradient(
        start = Point(bounds.left, bounds.top),
        end = Point(bounds.right, bounds.bottom),
        stops = listOf(
            GradientStop(0f, Color.RED),
            GradientStop(1f, Color.GREEN),
        ),
        tileMode = TileMode.REPEAT,
    )

    private fun checkerboardShader(c1: Color, c2: Color, size: Int): Shader {
        val w = size * 2
        val h = size * 2
        val pixels = ByteArray(w * h * 4)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val dark = (x / size + y / size) % 2 == 0
                val c = if (dark) c1 else c2
                val rgba = colorToBytes(c)
                val idx = (y * w + x) * 4
                pixels[idx] = rgba[2]
                pixels[idx + 1] = rgba[1]
                pixels[idx + 2] = rgba[0]
                pixels[idx + 3] = rgba[3]
            }
        }
        val img = org.graphiks.kanvas.image.Image.fromPixels(w, h, pixels)
        return Shader.Image(img, TileMode.REPEAT, TileMode.REPEAT)
    }

    private fun colorToBytes(c: Color): ByteArray {
        val packed = c.packed.toInt()
        return byteArrayOf(
            ((packed shr 16) and 0xFF).toByte(),
            ((packed shr 8) and 0xFF).toByte(),
            (packed and 0xFF).toByte(),
            ((packed shr 24) and 0xFF).toByte(),
        )
    }

    companion object {
        const val kPointSize = 25
        const val kColWidth = 180
        const val kWidth = kColWidth * 4
        const val kHeight = 750
    }

    private fun argb(a: Int, r: Int, g: Int, b: Int): Color =
        Color.fromRGBA(r / 255f, g / 255f, b / 255f, a / 255f)
}
