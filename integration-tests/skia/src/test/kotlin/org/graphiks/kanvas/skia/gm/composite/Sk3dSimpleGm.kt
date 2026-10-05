/**
 * Port of Skia's `gm/3d.cpp::sk3d_simple` — 300×300.
 * Draws a red rect directly and a Skia-alpha blue rect through
 * PictureRecorder + drawPicture, both under Skia's perspective camera.
 * @see https://github.com/google/skia/blob/8019e2e0629f3516b9d829737de2553b1d0ecb4a/gm/3d.cpp
 */
package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point3F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix4x4F32
import org.graphiks.math.vector.Vector3F32
import kotlin.math.PI
import kotlin.math.tan

class Sk3dSimpleGm : SkiaGm {
    override val name = "sk3d_simple"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.TRIVIAL
    override val minSimilarity = 0.0
    override val width = 300
    override val height = 300

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val angle = (PI / 4.0).toFloat()
        val viewport = Matrix4x4F32.scale(150f, 150f, 1f)
        val inverseViewport = requireNotNull(viewport.invert())
        val camera = Matrix4x4F32.lookAt(
            eye = Point3F32(0f, 0f, (1.0 / tan((angle / 2f).toDouble()) - 1.0).toFloat()),
            center = Point3F32.Origin,
            up = Vector3F32.UnitY,
        )
        val model = Matrix4x4F32.rotate(Vector3F32.UnitY, (PI / 6.0).toFloat())
        val ctm = (viewport * Matrix4x4F32.perspective(0.05f, 4f, angle) * camera * model * inverseViewport).asM33()
        val rect = RectF32.ofLTRB(-100f, -100f, 100f, 100f)

        canvas.save()
        canvas.concat(ctm)
        canvas.translate(150f, 150f)
        canvas.drawRect(rect, Paint(color = ColorARGB.Red, antiAlias = false))
        canvas.restore()

        val recorder = PictureRecorder()
        val recCanvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 300f, 300f))
        recCanvas.save()
        recCanvas.concat(ctm)
        recCanvas.translate(150f, 150f)
        recCanvas.drawRect(rect, Paint(color = ColorARGB.of(136, 0, 0, 255), antiAlias = false))
        recCanvas.restore()
        val pic = recorder.finishRecordingAsPicture()
        canvas.drawPicture(pic)
    }
}
