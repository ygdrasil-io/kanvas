package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.geometry.Path

class ShaderPathGm : SkiaGm {
    override val name = "shaderpath"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 820
    override val height = 930

    private val bmp: Image by lazy { makeBm() }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0xDD / 255f, 0xDD / 255f, 0xDD / 255f)

        val bmpPaint = Paint(antiAlias = true, color = Color.fromRGBA(1f, 1f, 1f, 0.5f))
        canvas.drawImage(bmp, Rect(5f, 5f, 80f, 80f), bmpPaint)

        val outlinePaint = Paint(style = PaintStyle.STROKE, strokeWidth = 0f)

        canvas.translate(15f, 15f)
        canvas.scale(2f, 2f)
        canvas.translate(0f, 2.25f)

        val path = Path {
            moveTo(0f, 40f)
            cubicTo(10f, 70f, 20f, 10f, 30f, 40f)
        }

        val tileModes = arrayOf(TileMode.REPEAT, TileMode.MIRROR)

        canvas.save()
        var i = 0
        for (tm0 in tileModes) {
            for (tm1 in tileModes) {
                val localM = Matrix33.translate(5f, 5f) * Matrix33.rotate(20f) * Matrix33.scale(1.15f, 0.85f)
                val shader = Shader.WithLocalMatrix(Shader.Image(bmp, tm0, tm1), localM)
                val fillPaint = Paint(antiAlias = true, shader = shader)
                canvas.drawPath(path, fillPaint)
                canvas.drawPath(path, outlinePaint)
                canvas.translate(50f, 0f)
                i++
                if ((i and 1) == 0) {
                    canvas.restore()
                    canvas.translate(0f, 22.5f)
                    canvas.save()
                }
            }
        }
        canvas.restore()
    }

    private fun makeBm(): Image {
        val surf = Surface(75, 75)
        surf.canvas {
            val w = 75f
            val pos = listOf(0f, 0.5f, 1f)
            val colors0 = listOf(
                GradientStop(0f, Color(0x80F00080u)),
                GradientStop(0.5f, Color(0xF0F08000u)),
                GradientStop(1f, Color(0x800080F0u)),
            )
            val colors1 = listOf(
                GradientStop(0f, Color(0xF08000F0u)),
                GradientStop(0.5f, Color(0x8080F000u)),
                GradientStop(1f, Color(0xF000F080u)),
            )

            val paint = Paint(shader = Shader.LinearGradient(
                start = Point(0f, 0f), end = Point(w, w),
                stops = colors0, tileMode = TileMode.CLAMP,
            ))
            drawRect(Rect(0f, 0f, w, w), paint)

            val paint2 = Paint(shader = Shader.LinearGradient(
                start = Point(w / 2, 0f), end = Point(w / 2, w),
                stops = colors1, tileMode = TileMode.CLAMP,
            ))
            drawRect(Rect(0f, 0f, w, w), paint2)
        }
        return surf.makeImageSnapshot()
    }
}
