package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

class SrcModeGm : SkiaGm {
    override val name = "srcmode"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 760

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val surface = Surface(width, height)
        surface.canvas {
            drawColor(Color.fromRGBA(1f, 1f, 1f, 1f))
            translate(20f, 20f)
            drawContent(this)
        }
        val image = surface.makeImageSnapshot()
        canvas.drawColor(0f, 0f, 0f, 1f)
        canvas.drawImage(image, Rect(0f, 0f, width.toFloat(), height.toFloat()))
    }

    private fun drawContent(c: Canvas) {
        val font = Font(typeface, size = H / 4f)
        val modes = listOf<BlendMode>(BlendMode.SRC_OVER, BlendMode.SRC, BlendMode.CLEAR)
        val paintProcs = listOf<(Paint) -> Paint>(::identityPaint, ::gradientPaint)

        for (aa in 0..1) {
            val basePaint = if (aa != 0) Paint() else Paint(antiAlias = false)
            c.save()
            for (paintProc in paintProcs) {
                var p = paintProc(basePaint)
                for (mode in modes) {
                    p = p.copy(blendMode = mode)
                    c.save()
                    for (proc in listOf(::drawHair, ::drawThick, ::drawRect, ::drawOval, ::drawText)) {
                        proc(c, p, font)
                        c.translate(0f, H * 5f / 4f)
                    }
                    c.restore()
                    c.translate(W * 5f / 4f, 0f)
                }
            }
            c.restore()
            c.translate(0f, (H * 5f / 4f) * 5f)
        }
    }

    private fun identityPaint(p: Paint): Paint = p.copy(shader = null)

    private fun gradientPaint(p: Paint): Paint = p.copy(
        shader = Shader.LinearGradient(
            start = Point(0f, 0f),
            end = Point(W, H),
            stops = listOf(
                GradientStop(0f, Color.fromRGBA(0f, 1f, 0f, 1f)),
                GradientStop(1f, Color.fromRGBA(0f, 0f, 1f, 1f)),
            ),
            tileMode = TileMode.CLAMP,
        ),
    )

    private fun drawHair(c: Canvas, paint: Paint, @Suppress("UNUSED_PARAMETER") font: Font) {
        val p = paint.copy(style = PaintStyle.STROKE, strokeWidth = 0f)
        val path = Path { moveTo(0f, 0f); lineTo(W, H) }
        c.drawPath(path, p)
    }

    private fun drawThick(c: Canvas, paint: Paint, @Suppress("UNUSED_PARAMETER") font: Font) {
        val p = paint.copy(style = PaintStyle.STROKE, strokeWidth = H / 5f)
        val path = Path { moveTo(0f, 0f); lineTo(W, H) }
        c.drawPath(path, p)
    }

    private fun drawRect(c: Canvas, paint: Paint, @Suppress("UNUSED_PARAMETER") font: Font) {
        c.drawRect(Rect(0f, 0f, W, H), paint)
    }

    private fun drawOval(c: Canvas, paint: Paint, @Suppress("UNUSED_PARAMETER") font: Font) {
        val path = Path { }.apply { addOval(Rect(0f, 0f, W, H)) }
        c.drawPath(path, paint)
    }

    private fun drawText(c: Canvas, paint: Paint, font: Font) {
        c.drawString("Hamburge", 0f, H * 2f / 3f, font, paint)
    }

    private companion object {
        val W: Float = 80f
        val H: Float = 60f
    }
}
