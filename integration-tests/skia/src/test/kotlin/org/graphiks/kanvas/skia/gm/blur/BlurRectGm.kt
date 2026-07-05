package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/blurrect.cpp`.
 * Matrix of blurred rects across blur styles, shaders and clipping.
 * @see https://github.com/google/skia/blob/main/gm/blurrect.cpp
 */
class BlurRectGm : SkiaGm {
    override val name = "blurrects"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 58.2
    override val width = 860
    override val height = 820

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val rect = Rect(0f, 0f, 100f, 50f)
        val scales = floatArrayOf(1f, 0.6f)

        canvas.translate(STROKE_WIDTH * 1.5f, STROKE_WIDTH * 1.5f)

        for (scale in scales) {
            canvas.save()
            for (style in BlurStyle.entries) {
                val paint = Paint(
                    color = Color.BLACK,
                    maskFilter = MaskFilter.Blur(style, blurSigma),
                )
                val radialPaint = Paint(
                    color = Color.BLACK,
                    maskFilter = MaskFilter.Blur(style, blurSigma),
                    shader = makeRadial(rect),
                )

                canvas.save()
                canvas.scale(scale, scale)
                drawProcs(canvas, rect, paint, doClip = false)
                canvas.translate(rect.width * 4f / 3f, 0f)
                drawProcs(canvas, rect, radialPaint, doClip = false)
                canvas.translate(rect.width * 4f / 3f, 0f)
                drawProcs(canvas, rect, paint, doClip = true)
                canvas.translate(rect.width * 4f / 3f, 0f)
                drawProcs(canvas, rect, radialPaint, doClip = true)
                canvas.restore()

                canvas.translate(0f, PROC_COUNT * rect.height * 4f / 3f * scale)
            }
            canvas.restore()
            canvas.translate(4f * rect.width * 4f / 3f * scale, 0f)
        }
    }

    private fun drawProcs(canvas: GmCanvas, rect: Rect, paint: Paint, doClip: Boolean) {
        canvas.save()
        for (proc in 0 until 3) {
            if (doClip) {
                val clipRect = Rect.fromLTRB(
                    rect.left + STROKE_WIDTH / 2f,
                    rect.top + STROKE_WIDTH / 2f,
                    rect.right - STROKE_WIDTH / 2f,
                    rect.bottom - STROKE_WIDTH / 2f,
                )
                canvas.save()
                canvas.clipRect(clipRect)
            }
            when (proc) {
                0 -> canvas.drawRect(rect, paint)
                1 -> canvas.drawPath(makeDonut(rect), paint)
                2 -> drawDonutSkewed(canvas, rect, paint)
            }
            if (doClip) {
                canvas.restore()
            }
            canvas.translate(0f, rect.height * 4f / 3f)
        }
        canvas.restore()
    }

    private fun drawDonutSkewed(canvas: GmCanvas, rect: Rect, paint: Paint) {
        val donut = makeDonut(rect)
        val cx = rect.center.x
        val cy = rect.center.y
        val skewed = donut.transform(
            Matrix33.translate(cx, cy) * Matrix33.skew(0.35f, 0f) * Matrix33.translate(-cx, -cy),
        )
        canvas.drawPath(skewed, paint)
    }

    private fun makeDonut(rect: Rect): Path {
        val outer = rect
        val inner = Rect.fromLTRB(
            rect.left + STROKE_WIDTH,
            rect.top + STROKE_WIDTH,
            rect.right - STROKE_WIDTH,
            rect.bottom - STROKE_WIDTH,
        )
        return Path { }.also { it.fillType = FillType.WINDING }.apply {
            addOval(outer)
            addOval(inner)
        }
    }

    private fun makeRadial(rect: Rect): Shader.RadialGradient {
        val colors = listOf(Color.WHITE, Color.TRANSPARENT, Color.BLACK)
        val positions = floatArrayOf(0f, 0.65f, 1f)
        return Shader.RadialGradient(
            center = rect.center,
            radius = rect.width * 0.5f,
            stops = colors.indices.map { i ->
                GradientStop(positions[i], colors[i])
            },
            tileMode = TileMode.CLAMP,
        )
    }

    private companion object {
        const val STROKE_WIDTH: Float = 20f
        const val PROC_COUNT: Int = 3
        val blurSigma: Float = STROKE_WIDTH / 2f * 0.57735f + 0.5f
    }
}
