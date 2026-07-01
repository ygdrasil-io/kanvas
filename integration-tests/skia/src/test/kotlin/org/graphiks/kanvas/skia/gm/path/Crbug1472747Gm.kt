package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/circulararcs.cpp::crbug_1472747` (DEF_SIMPLE_GM,
 * 400 × 400).
 *
 * Manually-stroked circle (inner + outer ovals) at `r = 31000` filled
 * with `kEvenOdd` to produce a thin ring. Each oval is decomposed via
 * two `arcTo(oval, 0, -180)` + `arcTo(oval, -180, -180)` half-arcs —
 * mirroring how Canvas2D emits a `2π` arc. Originally exposed a
 * pre-chopping bug in tessellation path renderers that lost the
 * non-default winding mode.
 * @see https://github.com/google/skia/blob/main/gm/circulararcs.cpp
 */
class Crbug1472747Gm : SkiaGm {
    override val name = "crbug_1472747"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 93.5
    override val width = 400
    override val height = 400

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val radius = 31000f
        val cx = 0f
        val cy = radius + 10f

        val path = Path {
            moveTo(cx + radius, cy)
            arcTo(radius, radius, 0f, false, true, cx - radius, cy)
            arcTo(radius, radius, 0f, false, true, cx + radius, cy)

            moveTo(cx + radius + 5f, cy)
            arcTo(radius + 5f, radius + 5f, 0f, false, true, cx - radius - 5f, cy)
            arcTo(radius + 5f, radius + 5f, 0f, false, true, cx + radius + 5f, cy)
        }
        path.fillType = FillType.EVEN_ODD

        canvas.drawPath(path, Paint(antiAlias = true))
    }
}
