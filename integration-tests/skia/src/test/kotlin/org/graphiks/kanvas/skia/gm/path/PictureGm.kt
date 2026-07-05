package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/picture.cpp::PictureGM` (`pictures`, 450 × 120).
 * Records a small picture (translucent blue square + red triangle +
 * green triangle + a Plus-blended white square) and replays it 4 times.
 * @see https://github.com/google/skia/blob/main/gm/picture.cpp
 */
class PictureGm : SkiaGm {
    override val name = "pictures"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 450
    override val height = 120

    private val picture: Picture by lazy { makePicture() }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.translate(10f, 10f)

        // 1. Identity (no extra transform).
        canvas.drawPicture(picture)

        // 2. translate(110, 0), no paint.
        canvas.save()
        canvas.translate(110f, 0f)
        canvas.drawPicture(picture)
        canvas.restore()

        // 3. translate(220, 0), default paint.
        canvas.save()
        canvas.translate(220f, 0f)
        canvas.drawPicture(picture, Paint())
        canvas.restore()

        // 4. translate(330, 0), paint with alpha 0.5.
        canvas.save()
        canvas.translate(330f, 0f)
        canvas.saveLayer(Rect.fromLTRB(0f, 0f, 100f, 100f), Paint(color = Color(0x80FFFFFFu)))
        canvas.drawPicture(picture)
        canvas.restore()
        canvas.restore()
    }

    private fun makePicture(): Picture {
        val rec = PictureRecorder()
        val recCanvas = rec.beginRecording(Rect.fromLTRB(0f, 0f, 100f, 100f))

        val paint = Paint(antiAlias = true)

        paint.copy(color = Color(0x800000FFu)).let { recCanvas.drawRect(Rect.fromXYWH(0f, 0f, 100f, 100f), it) }
        paint.copy(color = Color(0x80FF0000u)).let { recCanvas.drawPath(makeTriangle(0f, 0f, 100f, 0f, 100f, 100f), it) }
        paint.copy(color = Color(0x8000FF00u)).let { recCanvas.drawPath(makeTriangle(0f, 0f, 100f, 0f, 0f, 100f), it) }
        paint.copy(color = Color(0x80FFFFFFu), blendMode = BlendMode.PLUS).let { recCanvas.drawRect(Rect.fromLTRB(25f, 25f, 75f, 75f), it) }

        return rec.finishRecordingAsPicture()
    }

    private fun makeTriangle(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float): Path = Path {
        moveTo(x1, y1)
        lineTo(x2, y2)
        lineTo(x3, y3)
        close()
    }
}
