package org.graphiks.kanvas.skia.gm.clip

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/croppedrects.cpp` CroppedRectsGM.
 * Exercises three drawing code-paths that pre-crop filled rects
 * by the active clip: drawPaint, drawImageRect, drawPath with stroke.
 * @see https://github.com/google/skia/blob/main/gm/croppedrects.cpp
 */
class CroppedRectsGm : SkiaGm {
    override val name = "croppedrects"
    override val renderFamily = RenderFamily.CLIP
    override val minSimilarity = 0.0
    override val width = 500
    override val height = 500

    private val srcImageClip = Rect.fromLTRB(75f, 75f, 275f, 275f)

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val img = createImage()
        val shader = img.makeShader(TileMode.CLAMP, TileMode.CLAMP)

        canvas.drawColor(r = 1f, g = 1f, b = 1f)

        // (1) drawPaint with clip
        canvas.save()
        canvas.clipRect(srcImageClip)
        canvas.drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()), Paint(shader = shader))
        canvas.restore()

        // (2) drawImageRect with src/dst cropping
        canvas.save()
        val drawRect = Rect.fromXYWH(350f, 100f, 100f, 300f)
        canvas.clipRect(drawRect)
        canvas.drawImageRect(
            img,
            Rect.fromLTRB(
                srcImageClip.left - 0.5f * srcImageClip.width,
                srcImageClip.top - srcImageClip.height,
                srcImageClip.right + 0.5f * srcImageClip.width,
                srcImageClip.bottom + srcImageClip.height,
            ),
            Rect.fromLTRB(
                drawRect.left - 0.5f * drawRect.width,
                drawRect.top - drawRect.height,
                drawRect.right + 0.5f * drawRect.width,
                drawRect.bottom + drawRect.height,
            ),
        )
        canvas.restore()

        // (3) drawPath with stroked-line shader
        canvas.save()
        val cy = srcImageClip.center.y
        val path = Path {
            moveTo(srcImageClip.left - srcImageClip.width, cy)
            lineTo(srcImageClip.right + 3f * srcImageClip.width, cy)
        }
        val strokePaint = Paint(
            style = PaintStyle.STROKE,
            strokeWidth = 2f * srcImageClip.height,
            shader = shader,
        )
        canvas.translate(23f, 301f)
        canvas.scale(300f / srcImageClip.width, 100f / srcImageClip.height)
        canvas.translate(-srcImageClip.left, -srcImageClip.top)
        canvas.clipRect(srcImageClip)
        canvas.drawPath(path, strokePaint)
        canvas.restore()
    }

    private fun createImage(): Image {
        val surface = Surface(500, 500)
        surface.canvas {
            drawColor(Color.RED)

            val greenFill = Paint(color = Color.fromRGBA(0f, 1f, 0f))
            drawRect(srcImageClip, greenFill)

            val strokeWidth = 10f
            val inset = Rect(
                srcImageClip.left + strokeWidth / 2f,
                srcImageClip.top + strokeWidth / 2f,
                srcImageClip.right - strokeWidth / 2f,
                srcImageClip.bottom - strokeWidth / 2f,
            )
            val darkGreen = Paint(
                style = PaintStyle.STROKE,
                strokeWidth = strokeWidth,
                color = Color.fromRGBA(0f, 0x88 / 255f, 0f),
            )
            drawRect(inset, darkGreen)
        }
        return surface.makeImageSnapshot()
    }
}
