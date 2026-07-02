package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/picture.cpp::PictureCullRectGM` (`picture_cull_rect`, 120 × 120).
 * Records a picture whose bounds top-left starts at y = 80, then replays under
 * a clip and translate.
 * @see https://github.com/google/skia/blob/main/gm/picture.cpp
 */
class PictureCullRectGm : SkiaGm {
    override val name = "picture_cull_rect"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 120
    override val height = 120

    private val picture: Picture by lazy { makePicture() }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.clipRect(Rect.fromLTRB(0f, 60f, 120f, 120f))
        canvas.translate(10f, 10f)
        canvas.drawPicture(picture)
    }

    private fun makePicture(): Picture {
        val rec = PictureRecorder()
        val recCanvas = rec.beginRecording(Rect.fromLTRB(0f, 0f, 100f, 100f))
        val paint = Paint(color = Color(0x800000FFu))
        val rect = Rect.fromLTRB(0f, 80f, 100f, 100f)
        recCanvas.drawRect(rect, paint)
        val oval = Path { }
        oval.addOval(rect)
        recCanvas.drawPath(oval, paint)
        return rec.finishRecordingAsPicture()
    }
}
