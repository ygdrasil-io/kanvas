/**
 * Port of Skia's `gm/pictureshader.cpp::DEF_SIMPLE_GM(pictureshader_persp, …)`.
 * Records a picture with shapes, then exercises [Picture.asShader] with
 * kDecal tiling under a perspective-like skew transform. Two views:
 * kDirect (clip + drawPicture) and kPictureShader (asShader + drawRect).
 * @see https://github.com/google/skia/blob/main/gm/pictureshader.cpp
 */
package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

class PictureShaderPerspGm : SkiaGm {
    override val name = "pictureshader_persp"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 215
    override val height = 110

    private var fPicture: Picture? = null

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        if (fPicture == null) {
            val recorder = PictureRecorder()
            val rc = recorder.beginRecording(Rect.fromLTRB(0f, 0f, 100f, 100f))
            drawTileContent(rc)
            fPicture = recorder.finishRecordingAsPicture()
        }
        val picture = fPicture ?: return

        // Perspective-like skew transform (approximation of SkM44 perspective).
        val skew = Matrix33.makeAll(
            2f, 0f, 0f,
            0f, 2f, 0f,
            0.001f, 0.003f, 1f,
        )

        canvas.drawColor(0f, 0f, 0f)
        canvas.translate(5f, 5f)

        for (strategy in DrawStrategy.entries) {
            canvas.save()
            val outline = Paint(
                color = Color.WHITE,
                style = PaintStyle.STROKE,
                strokeWidth = 1f,
            )
            canvas.drawRect(Rect.fromLTRB(-1f, -1f, 101f, 101f), outline)
            canvas.clipRect(Rect.fromLTRB(0f, 0f, 100f, 100f))
            canvas.concat(skew)
            drawPicture(canvas, picture, strategy)
            canvas.restore()
            canvas.translate(105f, 0f)
        }
    }

    private fun drawTileContent(c: Canvas) {
        val paint = Paint(antiAlias = true, color = Color.GREEN)
        c.drawRect(Rect.fromXYWH(10f, 10f, 40f, 40f), paint)
        val fill = Paint(
            antiAlias = true,
            color = Color.fromRGBA(0f, 0f, 1f, 0.5f),
        )
        val circle = Path { }
        circle.addCircle(70f, 70f, 20f)
        c.drawPath(circle, fill)
    }

    private fun drawPicture(canvas: GmCanvas, picture: Picture, strategy: DrawStrategy) {
        val bounds = Rect.fromLTRB(0f, 0f, 50f, 50f)
        when (strategy) {
            DrawStrategy.kDirect -> {
                canvas.clipRect(bounds)
                canvas.drawPicture(picture)
            }
            DrawStrategy.kPictureShader -> {
                val shader = picture.asShader(
                    tileX = TileMode.DECAL,
                    tileY = TileMode.DECAL,
                    sampling = SamplingOptions.LINEAR,
                    tile = bounds,
                )
                val paint = Paint(shader = shader)
                canvas.drawRect(Rect.fromLTRB(0f, 0f, 50f, 50f), paint)
            }
        }
    }

    private enum class DrawStrategy { kDirect, kPictureShader }
}
