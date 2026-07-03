package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

class PictureImageGeneratorGm : SkiaGm {
    override val name = "pictureimagegenerator"
    override val renderFamily = RenderFamily.IMAGE
    override val minSimilarity = 0.0
    override val width = 1160
    override val height = 860

    private var picture: Picture? = null

    private fun lazyInit() {
        if (picture == null) {
            val rect = Rect.fromXYWH(0f, 0f, PICTURE_W, PICTURE_H)
            val recorder = PictureRecorder()
            val canvas = recorder.beginRecording(rect)
            drawSimplifiedLogo(canvas, rect)
            picture = recorder.finishRecordingAsPicture()
        }
    }

    private fun drawSimplifiedLogo(canvas: Canvas, viewBox: Rect) {
        var paint = Paint(antiAlias = true)
        val cx = viewBox.center.x
        val cy = viewBox.center.y
        val maxR = minOf(viewBox.width, viewBox.height) * 0.45f
        var r = maxR
        var i = 0
        while (r > 4f) {
            paint = paint.copy(
                color = if ((i and 1) == 0) Color.BLACK
                else Color.fromRGBA(0xCCf / 255f, 0x41f / 255f, 0x41f / 255f)
            )
            canvas.drawRect(
                Rect.fromLTRB(cx - r, cy - r * 0.5f, cx + r, cy + r * 0.5f),
                paint,
            )
            r *= 0.7f
            i++
        }
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        lazyInit()
        val pic = picture ?: return

        val configs = arrayOf(
            Config(200, 100, 1f, 1f, 1f),
            Config(200, 200, 1f, 1f, 1f),
            Config(200, 200, 1f, 2f, 1f),
            Config(400, 200, 2f, 2f, 1f),

            Config(200, 100, 1f, 1f, 0.9f),
            Config(200, 200, 1f, 1f, 0.75f),
            Config(200, 200, 1f, 2f, 0.5f),
            Config(400, 200, 2f, 2f, 0.25f),

            Config(200, 200, 0.5f, 1f, 1f),
            Config(200, 200, 1f, 0.5f, 1f),
            Config(200, 200, 0.5f, 0.5f, 1f),
            Config(200, 200, 2f, 2f, 1f),

            Config(200, 100, -1f, 1f, 1f),
            Config(200, 100, 1f, -1f, 1f),
            Config(200, 100, -1f, -1f, 1f),
            Config(200, 100, -1f, -1f, 0.5f),
        )

        val drawsPerRow = 4
        val drawSize = 250f

        for (i in configs.indices) {
            val cfg = configs[i]
            var m = Matrix33.scale(cfg.scaleX, cfg.scaleY)
            if (cfg.scaleX < 0f) m = m * Matrix33.translate(cfg.width.toFloat(), 0f)
            if (cfg.scaleY < 0f) m = m * Matrix33.translate(0f, cfg.height.toFloat())

            val opacityPaint = if (cfg.opacity < 1f) {
                Paint(color = Color.fromRGBA(1f, 1f, 1f, cfg.opacity))
            } else {
                null
            }

            val sw = cfg.width
            val sh = cfg.height
            val surface = Surface(sw, sh)
            val sc = surface.canvas()
            sc.clear(Color.TRANSPARENT)
            if (opacityPaint != null) sc.saveLayer(null, opacityPaint)
            sc.setMatrix(m)
            pic.playback(sc)
            if (opacityPaint != null) sc.restore()
            val image = surface.makeImageSnapshot()

            val x = drawSize * (i % drawsPerRow)
            val y = drawSize * (i / drawsPerRow)

            val bg = Paint(color = Color.fromRGBA(0xF0f / 255f, 0xF0f / 255f, 0xF0f / 255f))
            canvas.drawRect(
                Rect.fromXYWH(x, y, image.width.toFloat(), image.height.toFloat()),
                bg,
            )
            canvas.drawImage(
                image,
                Rect.fromXYWH(x, y, sw.toFloat(), sh.toFloat()),
            )
        }
    }

    private data class Config(
        val width: Int,
        val height: Int,
        val scaleX: Float,
        val scaleY: Float,
        val opacity: Float,
    )

    private companion object {
        const val PICTURE_W: Float = 200f
        const val PICTURE_H: Float = 100f
    }
}
