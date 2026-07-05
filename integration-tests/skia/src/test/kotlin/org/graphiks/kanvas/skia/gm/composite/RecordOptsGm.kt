/**
 * Port of Skia's `gm/recordopts.cpp::DEF_SIMPLE_GM(recordopts, …)`.
 * Tests that saveLayer → drawRect → restore produces the same result
 * when drawn directly vs. recorded through PictureRecorder + playback.
 * Three sequences: uniform rect, non-uniform bitmap, SVG-opacity style.
 * @see https://github.com/google/skia/blob/main/gm/recordopts.cpp
 */
package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

class RecordOptsGm : SkiaGm {
    override val name = "recordopts"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = (kSize + 1) * 2
    override val height = (kSize + 1) * 15

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0f, 0f, 0f, 0f)

        val sequences: List<(GmCanvas, Color) -> Unit> = listOf(
            { c, color -> drawRectSequence(c, color) },
            { c, color -> drawBitmapSequence(c, color) },
            { c, color -> drawSvgSequence(c, color) },
        )

        val green = Color.fromRGBA(0f, 1f, 0f)
        for (seq in sequences) {
            canvas.save()
            seq(canvas, green)
            canvas.translate((kSize + 1).toFloat(), 0f)

            val recorder = PictureRecorder()
            val rc = recorder.beginRecording(Rect.fromLTRB(0f, 0f, kSize.toFloat(), kSize.toFloat()))
            val tmp = GmCanvas(rc, kSize, kSize)
            seq(tmp, green)
            val pic = recorder.finishRecordingAsPicture()
            canvas.drawPicture(pic)

            canvas.restore()
            canvas.translate(0f, (kSize + 1).toFloat())
        }

        val alphaValues = intArrayOf(50, 51, 100, 150)
        for (alphaMul in alphaValues) {
            for (seq in sequences) {
                canvas.save()
                val alphaColor = Color.fromRGBA(0f, alphaMul / 255f, 0f)
                seq(canvas, alphaColor)
                canvas.translate((kSize + 1).toFloat(), 0f)

                val recorder = PictureRecorder()
                val rc = recorder.beginRecording(Rect.fromLTRB(0f, 0f, kSize.toFloat(), kSize.toFloat()))
                val tmp = GmCanvas(rc, kSize, kSize)
                seq(tmp, alphaColor)
                val pic = recorder.finishRecordingAsPicture()
                canvas.drawPicture(pic)

                canvas.restore()
                canvas.translate(0f, (kSize + 1).toFloat())
            }
        }
    }

    private fun drawRectSequence(c: GmCanvas, color: Color) {
        val rect = Rect.fromLTRB(0f, 0f, kSize.toFloat(), kSize.toFloat())
        val layerPaint = Paint(color = Color.fromRGBA(0f, 0f, 0f, 0.5f))
        c.saveLayer(rect, layerPaint)
        c.drawRect(rect, Paint(color = color))
        c.restore()
    }

    private fun drawBitmapSequence(c: GmCanvas, color: Color) {
        val surface = Surface(kSize, kSize)
        val sc = surface.canvas()
        sc.clear(color)
        sc.drawRect(Rect.fromLTRB(0f, 0f, 7f, 7f), Paint(color = Color.WHITE))
        val image: Image = surface.makeImageSnapshot()

        val rect = Rect.fromLTRB(0f, 0f, kSize.toFloat(), kSize.toFloat())
        val layerPaint = Paint(color = Color.fromRGBA(0f, 0f, 0f, 129f / 255f))
        c.saveLayer(rect, layerPaint)
        c.drawImage(image, rect)
        c.restore()
    }

    private fun drawSvgSequence(c: GmCanvas, color: Color) {
        val rect = Rect.fromLTRB(0f, 0f, kSize.toFloat(), kSize.toFloat())
        val layerPaint = Paint(color = Color.fromRGBA(0f, 0f, 0f, 130f / 255f))
        c.saveLayer(rect, layerPaint)
        c.save()
        c.clipRect(rect)
        c.drawRect(rect, Paint(color = color))
        c.restore()
        c.restore()
    }

    private companion object {
        const val kSize = 50
    }
}
