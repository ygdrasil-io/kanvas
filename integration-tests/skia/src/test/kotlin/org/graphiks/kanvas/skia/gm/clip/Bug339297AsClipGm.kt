package org.graphiks.kanvas.skia.gm.clip

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color

/**
 * Port of Skia's `gm/strokefill.cpp::bug339297_as_clip` (640 × 480).
 *
 * Same giant cubic shape as bug339297, but rendered using clipPath/clear
 * instead of fill, then stroked in red. Exercises clipPath with extreme
 * coordinates and AA geometry.
 * @see https://github.com/google/skia/blob/main/gm/strokefill.cpp
 */
class Bug339297AsClipGm : SkiaGm {
    override val name = "bug339297_as_clip"
    override val renderFamily = RenderFamily.CLIP
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 80.0
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

        canvas.save()
        canvas.clipPath(path, antiAlias = true)
        canvas.drawColor(r = 0f, g = 0f, b = 0f)
        canvas.restore()

        val paint = Paint(
            color = Color.RED,
            style = PaintStyle.STROKE,
            strokeWidth = 1f,
            antiAlias = true,
        )
        canvas.drawPath(path, paint)
    }
}
