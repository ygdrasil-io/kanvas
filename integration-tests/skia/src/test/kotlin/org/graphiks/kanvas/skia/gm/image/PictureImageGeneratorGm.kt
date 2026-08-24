/**
 * Port of Skia's `gm/pictureimagegenerator.cpp`.
 * Records a 200x100 picture, then draws 16 variants with varying
 * scale, opacity, and flip matrices in a 4-column grid.
 * Skia's `SkImages::DeferredFromPicture` is replaced with eager [Surface] materialization.
 * @see https://github.com/google/skia/blob/main/gm/pictureimagegenerator.cpp
 */
package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.RectF32

class PictureImageGeneratorGm : SkiaGm {
    override val name = "pictureimagegenerator"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 1160
    override val height = 860

    private var picture: Picture? = null

    private fun lazyInit() {
        if (picture == null) {
            val rect = RectF32.ofOriginSize(0f, 0f, PICTURE_W, PICTURE_H)
            val recorder = PictureRecorder()
            val canvas = recorder.beginRecording(rect)
            drawSimplifiedLogo(canvas, rect)
            picture = recorder.finishRecordingAsPicture()
        }
    }

    private fun drawSimplifiedLogo(canvas: Canvas, viewBox: RectF32) {
        var paint = Paint(antiAlias = true)
        val cx = viewBox.center().x
        val cy = viewBox.center().y
        val maxR = minOf(viewBox.width(), viewBox.height()) * 0.45f
        var r = maxR
        var i = 0
        while (r > 4f) {
            paint = paint.copy(
                color = if ((i and 1) == 0) ColorARGB.Black
                else ColorARGB.fromRGBA(0xCCf / 255f, 0x41f / 255f, 0x41f / 255f)
            )
            canvas.drawRect(
                RectF32.ofLTRB(cx - r, cy - r * 0.5f, cx + r, cy + r * 0.5f),
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
            var m = Matrix3x3F32.scaling(cfg.sx, cfg.sy)
            if (cfg.sx < 0f) m = m * Matrix3x3F32.translation(cfg.width.toFloat(), 0f)
            if (cfg.sy < 0f) m = m * Matrix3x3F32.translation(0f, cfg.height.toFloat())

            val opacityPaint = if (cfg.opacity < 1f) {
                Paint(color = ColorARGB.fromRGBA(1f, 1f, 1f, cfg.opacity))
            } else {
                null
            }

            val sw = cfg.width
            val sh = cfg.height
            val surface = Surface(sw, sh)
            val sc = surface.canvas()
            sc.clear(ColorARGB.Transparent)
            if (opacityPaint != null) sc.saveLayer(null, opacityPaint)
            sc.setMatrix(m)
            pic.playback(sc)
            if (opacityPaint != null) sc.restore()
            val image = surface.makeImageSnapshot()

            val x = drawSize * (i % drawsPerRow)
            val y = drawSize * (i / drawsPerRow)

            val bg = Paint(color = ColorARGB.fromRGBA(0xF0f / 255f, 0xF0f / 255f, 0xF0f / 255f))
            canvas.drawRect(
                RectF32.ofOriginSize(x, y, image.width.toFloat(), image.height.toFloat()),
                bg,
            )
            canvas.drawImage(
                image,
                RectF32.ofOriginSize(x, y, sw.toFloat(), sh.toFloat()),
            )
        }
    }

    private data class Config(
        val width: Int,
        val height: Int,
        val sx: Float,
        val sy: Float,
        val opacity: Float,
    )

    private companion object {
        const val PICTURE_W: Float = 200f
        const val PICTURE_H: Float = 100f
    }
}
