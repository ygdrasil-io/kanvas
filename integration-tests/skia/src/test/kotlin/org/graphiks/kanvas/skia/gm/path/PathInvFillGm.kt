package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.math.geometry.RectF32

/**
 * Port of Skia's `gm/pathfill.cpp::PathInverseFillGM` (450 × 220).
 * Tests inverse-fill with a clip that completely excludes the geometry.
 * @see https://github.com/google/skia/blob/main/gm/pathfill.cpp
 */
class PathInvFillGm : SkiaGm {
    override val name = "pathinvfill"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 15.8
    override val width = 450
    override val height = 220

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val path = Path { }.apply { addCircle(50f, 50f, 40f) }
        path.fillType = FillType.INVERSE_WINDING

        val clipR = RectF32.ofLTRB(0f, 0f, 100f, 200f)
        canvas.translate(10f, 10f)

        for (doclip in 0..1) {
            for (aa in 0..1) {
                val paint = Paint(antiAlias = aa != 0)

                canvas.save()
                canvas.clipRect(clipR)

                val clipPtr: RectF32? = if (doclip != 0) clipR else null

                show(canvas, path, paint, clipPtr, clipR.top, clipR.center().y)
                show(canvas, path, paint, clipPtr, clipR.center().y, clipR.bottom)

                canvas.restore()
                canvas.translate(110f, 0f)
            }
        }
    }

    private fun show(
        canvas: GmCanvas,
        path: Path,
        paint: Paint,
        clip: RectF32?,
        top: Float,
        bottom: Float,
    ) {
        canvas.save()
        if (clip != null) {
            val r = RectF32.ofLTRB(clip.left, top, clip.right, bottom)
            canvas.clipRect(r)
        }
        canvas.drawPath(path, paint)
        canvas.restore()
    }
}
