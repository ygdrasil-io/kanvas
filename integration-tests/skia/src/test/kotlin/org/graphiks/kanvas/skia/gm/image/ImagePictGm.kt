/**
 * Port of Skia's `gm/image_pict.cpp`.
 * Records a picture (red-stroked rect + blue oval), materialises two
 * images from it with different matrices, then draws them at 1x/0.25x/2x.
 * [SkImages.DeferredFromPicture] replaced with eager [Surface] materialization.
 * @see https://github.com/google/skia/blob/main/gm/image_pict.cpp
 */
package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

class ImagePictGm : SkiaGm {
    override val name = "image-picture"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 850
    override val height = 450

    private var initialized = false
    private lateinit var fPicture: Picture
    private lateinit var fImage0: Image
    private lateinit var fImage1: Image

    private fun drawSomething(canvas: Canvas, bounds: Rect) {
        val strokePaint = Paint(
            antiAlias = true,
            color = Color.RED,
            style = PaintStyle.STROKE,
            strokeWidth = 10f,
        )
        canvas.drawRect(bounds, strokePaint)

        val ovalPath = Path { }
        ovalPath.addOval(bounds)
        val fillPaint = Paint(
            antiAlias = true,
            color = Color.BLUE,
            style = PaintStyle.FILL,
        )
        canvas.drawPath(ovalPath, fillPaint)
    }

    private fun materialise(picture: Picture, size: Int, matrix: Matrix33): Image {
        val surface = Surface(size, size)
        val c = surface.canvas()
        c.clear(Color.TRANSPARENT)
        c.save()
        try {
            c.setMatrix(matrix)
            picture.playback(c)
        } finally {
            c.restore()
        }
        return surface.makeImageSnapshot()
    }

    private fun drawSet(canvas: GmCanvas) {
        canvas.save()
        canvas.concat(Matrix33.translate(-100f, -100f))
        canvas.drawPicture(fPicture)
        canvas.restore()

        canvas.drawImage(
            fImage0,
            Rect.fromXYWH(150f, 0f, fImage0.width.toFloat(), fImage0.height.toFloat()),
        )
        canvas.drawImage(
            fImage1,
            Rect.fromXYWH(300f, 0f, fImage1.width.toFloat(), fImage1.height.toFloat()),
        )
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        if (!initialized) {
            val bounds = Rect.fromXYWH(100f, 100f, 100f, 100f)
            val recorder = PictureRecorder()
            val rc = recorder.beginRecording(bounds)
            drawSomething(rc, bounds)
            fPicture = recorder.finishRecordingAsPicture()

            val m0 = Matrix33.translate(-100f, -100f)
            fImage0 = materialise(fPicture, 100, m0)

            val m1Pre = Matrix33.translate(-150f, -150f)
            val rot = Matrix33.rotate(45f)
            val tBack = Matrix33.translate(50f, 50f)
            val m1 = tBack * rot * m1Pre
            fImage1 = materialise(fPicture, 100, m1)

            initialized = true
        }

        canvas.translate(20f, 20f)

        drawSet(canvas)

        canvas.save()
        canvas.translate(0f, 130f)
        canvas.scale(0.25f, 0.25f)
        drawSet(canvas)
        canvas.restore()

        canvas.save()
        canvas.translate(0f, 200f)
        canvas.scale(2f, 2f)
        drawSet(canvas)
        canvas.restore()
    }
}
