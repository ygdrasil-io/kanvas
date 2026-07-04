package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

class Xfermodes3Gm : SkiaGm {
    override val name = "xfermodes3"
    override val renderFamily = RenderFamily.COMPOSITE
    override val minSimilarity = 0.0
    override val width = 630
    override val height = 1215

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.translate(10f, 20f)
        drawContent(canvas)
    }

    private fun drawContent(canvas: GmCanvas) {
        val font = Font(typeface, size = 12f)

        val bgShader = makeBgShader()
        val bmpShader = makeBmpShader()

        val solidColors = listOf(Color.TRANSPARENT, Color.fromRGBA(0f, 0f, 1f, 1f), Color.fromRGBA(0.5f, 0.5f, 0f, 0.5f))
        val bmpAlphas = listOf(0xFF, 0x80)

        val strokes = listOf(
            PaintStyle.FILL to 0f,
            PaintStyle.STROKE to kSize / 2f,
        )

        var test = 0
        var x = 0f
        var y = 0f
        for ((style, strokeWidth) in strokes) {
            for (mode in BlendMode.entries) {
                canvas.drawString(mode.name, x, y + kSize + 3f + font.size, font, Paint())

                for (cIdx in solidColors.indices) {
                    val modePaint = Paint(
                        blendMode = mode,
                        color = solidColors[cIdx],
                        style = style,
                        strokeWidth = strokeWidth,
                    )
                    drawMode(canvas, x.toInt(), y.toInt(), modePaint, bgShader)
                    test++
                    x += kSize + 10f
                    if (test % kTestsPerRow == 0) {
                        x = 0f
                        y += kSize + 30f
                    }
                }
                for (a in bmpAlphas.indices) {
                    val alpha = if (bmpAlphas[a] == 0xFF) 1f else 0.5f
                    val modePaint = Paint(
                        blendMode = mode,
                        color = Color.fromRGBA(1f, 1f, 1f, alpha),
                        shader = bmpShader,
                        style = style,
                        strokeWidth = strokeWidth,
                    )
                    drawMode(canvas, x.toInt(), y.toInt(), modePaint, bgShader)
                    test++
                    x += kSize + 10f
                    if (test % kTestsPerRow == 0) {
                        x = 0f
                        y += kSize + 30f
                    }
                }
            }
        }
    }

    private fun drawMode(canvas: GmCanvas, x: Int, y: Int, modePaint: Paint, bgShader: Shader) {
        canvas.save()
        canvas.translate(x.toFloat(), y.toFloat())

        val r = Rect(0f, 0f, kSize.toFloat(), kSize.toFloat())

        val surface = Surface(kSize, kSize)
        surface.canvas {
            val bgPaint = Paint(shader = bgShader)
            drawRect(r, bgPaint)
            drawRect(r, modePaint)
        }
        val image = surface.makeImageSnapshot()
        canvas.drawImage(image, r)

        val frame = Paint(style = PaintStyle.STROKE)
        canvas.drawRect(Rect(-0.5f, -0.5f, kSize + 0.5f, kSize + 0.5f), frame)

        canvas.restore()
    }

    private fun makeBgShader(): Shader {
        val bg = Surface(2, 2)
        bg.canvas {
            val dark = Color.fromRGBA(0.259f, 0.255f, 0.259f, 1f)
            val light = Color.fromRGBA(0.839f, 0.827f, 0.839f, 1f)
            clear(dark)
            drawRect(Rect(1f, 0f, 2f, 1f), Paint(color = light))
            drawRect(Rect(0f, 1f, 1f, 2f), Paint(color = light))
        }
        val image = bg.makeImageSnapshot()
        return Shader.WithLocalMatrix(
            Shader.Image(image, TileMode.REPEAT, TileMode.REPEAT),
            Matrix33.scale(kCheckSize.toFloat(), kCheckSize.toFloat()),
        )
    }

    private fun makeBmpShader(): Shader {
        val center = Point(kSize / 2f, kSize / 2f)
        val colors = listOf<GradientStop>(
            GradientStop(0f, Color.TRANSPARENT),
            GradientStop(0.5f, Color.fromRGBA(0.5f, 0f, 0f, 0.5f)),
            GradientStop(0.75f, Color.fromRGBA(0.94f, 0.12f, 0.38f, 0.94f)),
            GradientStop(1f, Color.fromRGBA(1f, 1f, 1f, 1f)),
        )
        return Shader.RadialGradient(center, 3f * kSize / 4f, colors, TileMode.REPEAT)
    }

    private companion object {
        val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!
        const val kCheckSize: Int = 8
        const val kSize: Int = 30
        const val kTestsPerRow: Int = 15
    }
}
