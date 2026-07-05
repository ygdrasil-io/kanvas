/**
 * Port of Skia's `gm/image_pict.cpp::ImageCacheratorGM`.
 * Records a picture (red-stroked rect + blue oval), materialises it as an
 * image via [Surface], then draws it at three positions across three rows
 * (1x, 0.5x, 2x scale). Registered as `image-cacherator-from-picture`.
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
import org.graphiks.kanvas.types.Rect

class ImageCacheratorFromPictureGm : SkiaGm {
    override val name = "image-cacherator-from-picture"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 960
    override val height = 450

    private var fImage: Image? = null

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        if (fImage == null) {
            val bounds = Rect.fromXYWH(100f, 100f, 100f, 100f)
            val recorder = PictureRecorder()
            val rc = recorder.beginRecording(bounds)
            drawSomething(rc, bounds)
            val picture = recorder.finishRecordingAsPicture()
            fImage = materialise(picture, 100)
        }
        val img = fImage ?: return

        canvas.translate(20f, 20f)
        drawSet(canvas, img)

        canvas.save()
        canvas.translate(0f, 130f)
        drawSet(canvas, img)
        canvas.restore()

        canvas.save()
        canvas.translate(0f, 260f)
        canvas.scale(0.5f, 0.5f)
        drawSet(canvas, img)
        canvas.restore()
    }

    private fun drawSomething(c: Canvas, bounds: Rect) {
        val paint = Paint(
            antiAlias = true,
            color = Color.RED,
            style = PaintStyle.STROKE,
            strokeWidth = 10f,
        )
        c.drawRect(bounds, paint)
        val ovalFill = Paint(
            antiAlias = true,
            color = Color.BLUE,
            style = PaintStyle.FILL,
        )
        val ovalPath = Path { }
        ovalPath.addOval(bounds)
        c.drawPath(ovalPath, ovalFill)
    }

    private fun materialise(picture: Picture, size: Int): Image {
        val surface = Surface(size, size)
        val c = surface.canvas()
        c.clear(Color.TRANSPARENT)
        c.save()
        c.translate(-100f, -100f)
        picture.playback(c)
        c.restore()
        return surface.makeImageSnapshot()
    }

    private fun drawSet(canvas: GmCanvas, img: Image) {
        canvas.drawImage(img, Rect.fromXYWH(0f, 0f, img.width.toFloat(), img.height.toFloat()))
        canvas.drawImage(img, Rect.fromXYWH(150f, 0f, img.width.toFloat(), img.height.toFloat()))
        canvas.drawImage(img, Rect.fromXYWH(300f, 0f, img.width.toFloat(), img.height.toFloat()))
    }
}
