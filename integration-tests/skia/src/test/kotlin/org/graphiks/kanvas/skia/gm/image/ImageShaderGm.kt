/**
 * Port of Skia's `gm/image_shader.cpp::ImageShaderGM`.
 * Records a picture (red-stroked rect + blue oval), materialises it as an
 * image, then for each of 4 columns draws the image at (0,0) and a
 * kRepeat-shader-filled circle below.
 * @see https://github.com/google/skia/blob/main/gm/image_shader.cpp
 */
package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

class ImageShaderGm : SkiaGm {
    override val name = "image-shader"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 850
    override val height = 450

    private var fImage: Image? = null

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        if (fImage == null) {
            val bounds = Rect.fromLTRB(0f, 0f, 100f, 100f)
            val recorder = PictureRecorder()
            val rc = recorder.beginRecording(bounds)
            drawSomething(rc, bounds)
            val picture = recorder.finishRecordingAsPicture()

            val surface = Surface(100, 100)
            val sc = surface.canvas()
            sc.clear(Color.TRANSPARENT)
            picture.playback(sc)
            fImage = surface.makeImageSnapshot()
        }
        val img = fImage ?: return

        canvas.translate(20f, 20f)
        for (i in 0 until 4) {
            testImage(canvas, img)
            canvas.translate(120f, 0f)
        }
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

    private fun testImage(canvas: GmCanvas, image: Image) {
        canvas.save()
        canvas.drawImage(image, Rect.fromXYWH(0f, 0f, image.width.toFloat(), image.height.toFloat()))
        canvas.translate(0f, 120f)
        val baseShader = image.makeShader(
            tileModeX = TileMode.REPEAT,
            tileModeY = TileMode.REPEAT,
            sampling = SamplingOptions.NEAREST,
        )
        val shader = Shader.WithLocalMatrix(baseShader, Matrix33.translate(-50f, -50f))
        val paint = Paint(antiAlias = true, shader = shader)
        canvas.drawCircle(50f, 50f, 50f, paint)
        canvas.restore()
    }
}
