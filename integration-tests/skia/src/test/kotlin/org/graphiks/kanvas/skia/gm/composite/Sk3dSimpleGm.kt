/**
 * Port of Skia's `gm/3d.cpp::sk3d_simple` — 300×300.
 * Draws a red rect directly and a half-transparent blue rect through
 * PictureRecorder + drawPicture, both with a 30° rotation transform.
 * @see https://github.com/google/skia/blob/main/gm/3d.cpp
 */
package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

class Sk3dSimpleGm : SkiaGm {
    override val name = "sk3d_simple"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.TRIVIAL
    override val minSimilarity = 0.0
    override val width = 300
    override val height = 300

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val ctm = Matrix33.translate(150f, 150f) * Matrix33.rotate(30f)
        canvas.save()
        canvas.concat(ctm)
        canvas.drawRect(Rect.fromLTRB(-100f, -100f, 100f, 100f), Paint(color = Color.RED))
        canvas.restore()

        val recorder = PictureRecorder()
        val recCanvas = recorder.beginRecording(Rect.fromLTRB(0f, 0f, 300f, 300f))
        recCanvas.save()
        recCanvas.concat(ctm)
        recCanvas.drawRect(Rect.fromLTRB(-100f, -100f, 100f, 100f), Paint(color = Color.fromRGBA(0f, 0f, 1f, 0.5f)))
        recCanvas.restore()
        val pic = recorder.finishRecordingAsPicture()
        canvas.drawPicture(pic)
    }
}
