package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color

/**
 * Port of Skia's `gm/strokefill.cpp::bug339297` (DEF_SIMPLE_GM, 640 x 480).
 *
 * A giant cubic-shaped near-horizontal sliver at y = -10 365 663 with a
 * `translate(258, 10 365 663)` that brings it back into device range.
 * Drawn twice : black filled then red 1-px stroked. Stresses path-bounds
 * arithmetic and CTM-aware float precision when source coords sit
 * 10^7 orders of magnitude away from the visible window.
 * @see https://github.com/google/skia/blob/main/gm/strokefill.cpp
 */
class Bug339297Gm : SkiaGm {
    override val name = "bug339297"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 480

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val path = Path {
            moveTo(-469515f, -10354890f)
            cubicTo(771919.62f, -10411179f, 2013360.1f, -10243774f, 3195542.8f, -9860664f)
            lineTo(3195550f, -9860655f)
            lineTo(3195539f, -9860652f)
            lineTo(3195539f, -9860652f)
            lineTo(3195539f, -9860652f)
            cubicTo(2013358.1f, -10243761f, 771919.25f, -10411166f, -469513.84f, -10354877f)
            lineTo(-469515f, -10354890f)
            close()
        }

        canvas.translate(258f, 10365663f)

        var paint = Paint(
            antiAlias = true,
            color = Color.BLACK,
            style = PaintStyle.FILL,
        )
        canvas.drawPath(path, paint)

        paint = paint.copy(
            color = Color.RED,
            style = PaintStyle.STROKE,
            strokeWidth = 1f,
        )
        canvas.drawPath(path, paint)
    }
}
