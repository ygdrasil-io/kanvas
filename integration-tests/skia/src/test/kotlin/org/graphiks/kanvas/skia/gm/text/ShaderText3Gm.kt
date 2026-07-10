package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
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
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/shadertext3.cpp` ShaderText3GM.
 * Exercises shader-based text fills with REPEAT/MIRROR tile modes
 * and a local matrix (translate, rotate, scale).
 * @see https://github.com/google/skia/blob/main/gm/shadertext3.cpp
 */
class ShaderText3Gm : SkiaGm {
    override val name = "shadertext3"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 820
    override val height = 930

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawRect(
            Rect(0f, 0f, width.toFloat(), height.toFloat()),
            Paint(color = Color(0xFFDDDDDDu)),
        )

        val gradientBase = Shader.LinearGradient(
            start = Point(0f, 0f),
            end = Point(75f, 75f),
            stops = listOf(
                GradientStop(0f, Color(0x80F00080u)),
                GradientStop(0.5f, Color(0xF0F08000u)),
                GradientStop(1f, Color(0x800080F0u)),
            ),
            tileMode = TileMode.REPEAT,
        )

        canvas.drawRect(Rect(5f, 5f, 80f, 80f), Paint(shader = gradientBase, color = Color.fromRGBA(1f, 1f, 1f, 0.5f)))

        val font = Font(typeface, size = kPointSize.toFloat())
        val outlinePaint = Paint(style = PaintStyle.STROKE, strokeWidth = 0f)

        canvas.translate(15f, 15f)
        canvas.scale(2f, 2f)
        canvas.translate(0f, 0.75f * kPointSize)

        val localMatrix = Matrix33.translate(5f, 5f) * Matrix33.rotate(20f) * Matrix33.scale(1.15f, 0.85f)

        canvas.save()
        var i = 0
        for (tmX in tileModes) {
            for (tmY in tileModes) {
                val fillShader = Shader.WithLocalMatrix(
                    Shader.LinearGradient(
                        start = Point(0f, 0f), end = Point(75f, 75f),
                        stops = listOf(
                            GradientStop(0f, Color(0x80F00080u)),
                            GradientStop(0.5f, Color(0xF0F08000u)),
                            GradientStop(1f, Color(0x800080F0u)),
                        ),
                        tileMode = tmX,
                    ),
                    localMatrix,
                )
                val fillPaint = Paint(shader = fillShader)
                canvas.drawString("B", 0f, 0f, font, fillPaint)
                canvas.drawString("B", 0f, 0f, font, outlinePaint)
                val gw = font.measureText("B")
                canvas.translate(gw + 10f, 0f)
                i++
                if (i % 2 == 0) {
                    canvas.restore()
                    canvas.translate(0f, 0.75f * kPointSize)
                    canvas.save()
                }
            }
        }
        canvas.restore()
    }

    private companion object {
        const val kPointSize = 300
        val tileModes = arrayOf(TileMode.REPEAT, TileMode.MIRROR)
    }
}
