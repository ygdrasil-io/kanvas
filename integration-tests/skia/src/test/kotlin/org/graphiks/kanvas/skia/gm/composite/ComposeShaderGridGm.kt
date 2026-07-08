package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's gm/composeshader.cpp.
 * A 4x4 grid of blend-mode pairs showing composed shaders.
 * @see https://github.com/google/skia/blob/main/gm/composeshader.cpp
 */
class ComposeShaderGridGm : SkiaGm {
    override val name = "composeshader_grid"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 4.5
    override val width = 882
    override val height = 882

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val src = makeSrcShader(CELL_SIZE)
        val dst = makeDstShader(CELL_SIZE)

        val margin: Float = 15f
        val dx: Float = 2f * CELL_SIZE + margin
        val dy: Float = 2f * CELL_SIZE + margin

        canvas.translate(margin, margin)
        canvas.save()
        for (m in 0 until 16) {
            val mode = BlendMode.entries[m]
            drawPair(canvas, src, dst, mode)
            if ((m % 4) == 3) {
                canvas.restore()
                canvas.translate(0f, dy)
                canvas.save()
            } else {
                canvas.translate(dx, 0f)
            }
        }
        canvas.restore()
    }

    private fun makeSrcShader(size: Float): Shader {
        return Shader.LinearGradient(
            start = Point(0f, 0f),
            end = Point(0f, size),
            stops = listOf(
                GradientStop(0f, Color.BLUE),
                GradientStop(1f, Color.TRANSPARENT),
            ),
        )
    }

    private fun makeDstShader(size: Float): Shader {
        return Shader.LinearGradient(
            start = Point(0f, 0f),
            end = Point(size, 0f),
            stops = listOf(
                GradientStop(0f, Color.RED),
                GradientStop(1f, Color.TRANSPARENT),
            ),
        )
    }

    private fun drawPair(canvas: GmCanvas, src: Shader, dst: Shader, mode: BlendMode) {
        canvas.save()
        val gap = 4f
        val outset = gap + 1.5f
        val borderR = Rect(
            -outset,
            -outset,
            2f * CELL_SIZE + gap + outset,
            2f * CELL_SIZE + gap + outset,
        )
        val borderPaint = Paint(style = PaintStyle.STROKE)
        canvas.drawRect(borderR, borderPaint)

        for (y in 0 until 2) {
            val alpha = if (y == 0) 1f else 0.5f
            drawCell(canvas, src, dst, mode, alpha)
            canvas.save()
            canvas.translate(CELL_SIZE + gap, 0f)
            drawComposed(canvas, src, dst, mode, alpha)
            canvas.restore()
            canvas.translate(0f, CELL_SIZE + gap)
        }
        canvas.restore()
    }

    private fun drawCell(
        canvas: GmCanvas,
        src: Shader,
        dst: Shader,
        mode: BlendMode,
        alpha: Float,
    ) {
        val r = Rect(0f, 0f, CELL_SIZE, CELL_SIZE)
        canvas.saveLayer()
        val dstPaint = Paint(
            color = Color.fromRGBA(1f, 0f, 0f, alpha),
            shader = dst,
            blendMode = BlendMode.SRC,
        )
        canvas.drawRect(r, dstPaint)
        val srcPaint = Paint(
            color = Color.fromRGBA(0f, 0f, 1f, alpha),
            shader = src,
            blendMode = mode,
        )
        canvas.drawRect(r, srcPaint)
        canvas.restore()
    }

    private fun drawComposed(
        canvas: GmCanvas,
        src: Shader,
        dst: Shader,
        mode: BlendMode,
        alpha: Float,
    ) {
        val p = Paint(
            color = Color.fromRGBA(1f, 1f, 1f, alpha),
            shader = Shader.Blend(mode, dst, src),
        )
        canvas.drawRect(Rect(0f, 0f, CELL_SIZE, CELL_SIZE), p)
    }

    private companion object {
        const val CELL_SIZE: Float = 100f
    }
}
