package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/bmpfilterqualityrepeat.cpp::BmpFilterQualityRepeat`.
 * @see https://github.com/google/skia/blob/main/gm/bmpfilterqualityrepeat.cpp
 */
class BmpFilterQualityRepeatGm : SkiaGm {
    override val name = "bmp_filter_quality_repeat"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 1000
    override val height = 400

    private val font = Font(
        typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!,
        size = 12f,
    )

    override fun draw(canvas: GmCanvas, width0: Int, height0: Int) {
        canvas.drawRect(Rect.fromLTRB(0f, 0f, 1000f, 400f), Paint(color = Color(0xFFCCBBAAu)))
        val checkerboard = buildCheckerboard()
        drawAll(canvas, checkerboard, 2.5f)
        canvas.translate(0f, 250f)
        canvas.scale(0.5f, 0.5f)
        drawAll(canvas, checkerboard, 1f)
    }

    private fun buildCheckerboard(): Image {
        val pixels = ByteArray(40 * 40 * 4)
        val colors = intArrayOf(
            0xFFFF0000.toInt(), 0xFF008200.toInt(),
            0xFFFF9000.toInt(), 0xFF2000FF.toInt(),
        )
        for (y in 0 until 40) {
            for (x in 0 until 40) {
                val ci = if (x < 20) if (y < 20) 0 else 2 else if (y < 20) 1 else 3
                val c = colors[ci]
                val i = (y * 40 + x) * 4
                pixels[i] = ((c shr 16) and 0xFF).toByte()
                pixels[i + 1] = ((c shr 8) and 0xFF).toByte()
                pixels[i + 2] = (c and 0xFF).toByte()
                pixels[i + 3] = ((c shr 24) and 0xFF).toByte()
            }
        }
        return Image.fromPixels(40, 40, pixels, sourceId = "checkerboard")
    }

    private fun drawAll(canvas: GmCanvas, bmp: Image, scaleX: Float) {
        val rect = Rect.fromLTRB(20f, 60f, 220f, 210f)
        val lm = Matrix33.makeAll(
            scaleX, 0f, 423f,
            0f, 1f, 330f,
        )
        val textPaint = Paint()

        canvas.save()
        val recs = arrayOf(
            "none" to SamplingOptions.NEAREST,
            "low" to SamplingOptions.LINEAR,
            "medium" to SamplingOptions.LINEAR,
            "high" to SamplingOptions.Cubic.Mitchell,
        )
        for ((name, sampling) in recs) {
            val imageShader = bmp.makeShader(TileMode.REPEAT, TileMode.REPEAT, sampling)
            val bmpPaint = Paint(shader = Shader.WithLocalMatrix(imageShader, lm))
            canvas.drawRect(rect, bmpPaint)
            canvas.drawString(name, 20f, 40f, font, textPaint)
            canvas.translate(250f, 0f)
        }
        canvas.restore()
    }
}
