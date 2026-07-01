package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/patharcto.cpp::shallow_angle_path_arcto`.
 * Repro for crbug.com/982968 — a curvy triangle whose corners are extremely
 * shallow-angle tangent arcs. The original bug was 32-bit float precision
 * loss in SkPath::arcTo with huge radii (~700k px).
 * @see https://github.com/google/skia/blob/main/gm/patharcto.cpp
 */
class ShallowAnglePathArcToGm : SkiaGm {
    override val name = "shallow_angle_path_arcto"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 300
    override val height = 300

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(style = PaintStyle.STROKE)

        val path = Path {
            moveTo(313.44189096331155f, 106.6009423589212f)
            lineTo(284.3113082008462f, 207.1407719157063f)
            lineTo(255.15053777129728f, 307.6718505416374f)
            lineTo(340.4737465981018f, 252.6907319346971f)
            arcTo(1251.2484277907251f, 1251.2484277907251f, 0f, false, true, 433.54333477716153f, 212.18116363345337f)
            arcTo(198.03116885327813f, 198.03116885327813f, 0f, false, true, 313.44189096331155f, 106.6009423589212f)
        }

        canvas.translate(-200f, -50f)
        canvas.drawPath(path, paint)
    }
}
