/**
 * Port of Skia's `gm/animated_image_orientation.cpp` — flight variant.
 * Simplified: records frames via PictureRecorder to exercise the picture
 * snapshot path without animated-image/codec dependencies.
 * @see https://github.com/google/skia/blob/main/gm/animated_image_orientation.cpp
 */
package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

class FlightAnimatedImageGm : SkiaGm {
    override val name = "flight_animated_image"
    override val renderFamily = RenderFamily.IMAGE
    override val minSimilarity = 0.0
    override val width = 304
    override val height = 304

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val cellSize = 100f
        val translate = (cellSize * 1.25f + 2f).toInt()

        for (usePic in booleanArrayOf(true, false)) {
            for (scale in floatArrayOf(1.25f, 1.0f, 0.75f, 0.5f)) {
                canvas.save()
                for (frame in 0 until 2) {
                    if (usePic) {
                        val recorder = PictureRecorder()
                        val rc = recorder.beginRecording(Rect.fromLTRB(0f, 0f, cellSize * scale, cellSize * scale))
                        val tmp = GmCanvas(rc, (cellSize * scale).toInt(), (cellSize * scale).toInt())
                        drawFrame(tmp, frame, scale)
                        val pic = recorder.finishRecordingAsPicture()
                        canvas.drawPicture(pic)
                    } else {
                        drawFrame(canvas, frame, scale)
                    }
                    canvas.translate(translate.toFloat(), 0f)
                }
                canvas.restore()
                canvas.translate(0f, translate.toFloat())
            }
        }
    }

    private fun drawFrame(canvas: GmCanvas, frame: Int, scale: Float) {
        val colors = listOf(Color.BLUE, Color(0xFFFF00FFu), Color(0xFF00FFFFu))
        val color = colors[frame % colors.size]
        val r = Rect.fromLTRB(10f * scale, 10f * scale, 90f * scale, 90f * scale)
        canvas.drawRect(r, Paint(color = color, antiAlias = true))
        canvas.drawCircle(50f * scale, 50f * scale, 40f * scale, Paint(
            color = color,
            style = PaintStyle.STROKE,
            strokeWidth = 4f * scale,
        ))
    }
}
