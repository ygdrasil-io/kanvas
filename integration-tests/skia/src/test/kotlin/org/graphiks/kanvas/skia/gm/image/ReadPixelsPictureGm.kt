/**
 * Port of Skia's `gm/readpixels.cpp::ReadPixelsPictureGM`.
 * Records a picture with three overlapping stroked circles, snapshot as
 * image, then draws across a 3-col × 12-row grid.
 * Simplified — draws the picture-image in each cell.
 * @see https://github.com/google/skia/blob/main/gm/readpixels.cpp
 */
package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

class ReadPixelsPictureGm : SkiaGm {
    override val name = "readpixelspicture"
    override val renderFamily = RenderFamily.IMAGE
    override val minSimilarity = 0.0
    override val width = 3 * kCellWidth
    override val height = 12 * kCellHeight

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val image = makePictureImage() ?: return
        val cellW = kCellWidth
        val cellH = kCellHeight

        for (col in 0 until 3) {
            canvas.save()
            for (row in 0 until 12) {
                canvas.drawImage(image, Rect.fromXYWH(0f, 0f, cellW.toFloat(), cellH.toFloat()))
                canvas.translate(0f, cellH.toFloat())
            }
            canvas.restore()
            canvas.translate(cellW.toFloat(), 0f)
        }
    }

    private fun drawContents(c: Canvas) {
        val strokePaint = Paint(style = PaintStyle.STROKE, strokeWidth = 20f)
        val c1 = strokePaint.copy(color = Color.fromRGBA(0.5f, 0f, 0f))
        val p1 = Path { }; p1.addCircle(40f, 40f, 35f)
        c.drawPath(p1, c1)
        val c2 = strokePaint.copy(color = Color.fromRGBA(0f, 0.5f, 0f))
        val p2 = Path { }; p2.addCircle(50f, 50f, 35f)
        c.drawPath(p2, c2)
        val c3 = strokePaint.copy(color = Color.fromRGBA(0f, 0f, 0.5f))
        val p3 = Path { }; p3.addCircle(60f, 60f, 35f)
        c.drawPath(p3, c3)
    }

    private fun makePictureImage(): Image? {
        val recorder = PictureRecorder()
        val rc = recorder.beginRecording(Rect.fromLTRB(0f, 0f, kCellWidth.toFloat(), kCellHeight.toFloat()))
        drawContents(rc)
        val picture = recorder.finishRecordingAsPicture()
        val surface = Surface(kCellWidth, kCellHeight)
        val sc = surface.canvas()
        sc.clear(Color.TRANSPARENT)
        picture.playback(sc)
        return surface.makeImageSnapshot()
    }

    private companion object {
        const val kCellWidth = 64
        const val kCellHeight = 64
    }
}
