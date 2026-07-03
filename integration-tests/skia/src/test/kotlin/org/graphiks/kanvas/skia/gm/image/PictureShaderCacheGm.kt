package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

class PictureShaderCacheGm : SkiaGm {
    override val name = "pictureshadercache"
    override val renderFamily = RenderFamily.IMAGE
    override val minSimilarity = 0.0
    override val width = 100
    override val height = 100

    private val tileSize = 100f
    private var picture: Picture? = null

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        if (picture == null) {
            val recorder = PictureRecorder()
            val pictureCanvas = recorder.beginRecording(Rect.fromLTRB(0f, 0f, tileSize, tileSize))
            drawTile(pictureCanvas)
            picture = recorder.finishRecordingAsPicture()
        }

        val shader = picture!!.asShader(
            tileX = TileMode.REPEAT,
            tileY = TileMode.REPEAT,
            sampling = SamplingOptions.NEAREST,
        )

        // Surface colour-space cache-discrimination test omitted — Kanvas has no non-SRGB raster surface.
        val paint = Paint(shader = shader)
        canvas.drawRect(Rect.fromLTRB(0f, 0f, tileSize, tileSize), paint)
    }

    private fun drawTile(canvas: Canvas) {
        var paint = Paint(color = Color.GREEN, antiAlias = true)

        val circle = Path { }.apply { addCircle(tileSize / 4f, tileSize / 4f, tileSize / 4f) }
        canvas.drawPath(circle, paint)
        canvas.drawRect(
            Rect.fromXYWH(tileSize / 2f, tileSize / 2f, tileSize / 2f, tileSize / 2f),
            paint,
        )

        paint = paint.copy(color = Color.RED)
        canvas.drawPath(Path { moveTo(tileSize / 2f, tileSize * 1f / 3f); lineTo(tileSize / 2f, tileSize * 2f / 3f) }, paint)
        canvas.drawPath(Path { moveTo(tileSize * 1f / 3f, tileSize / 2f); lineTo(tileSize * 2f / 3f, tileSize / 2f) }, paint)
    }
}
