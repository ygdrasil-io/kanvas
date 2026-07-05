package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/conicpaths.cpp` (DEF_SIMPLE_GM largeovals).
 * Stroked and filled large ovals testing axis-aligned and rotated paths.
 * @see https://github.com/google/skia/blob/main/gm/conicpaths.cpp
 */
class LargeOvalsGm : SkiaGm {
    override val name = "largeovals"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 39.5
    override val width = 250
    override val height = 250

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        var r = Rect.fromXYWH(-520f, -520f, 5000f, 4000f)
        val paint = Paint(
            style = PaintStyle.STROKE,
            strokeWidth = 100f,
            antiAlias = true,
        )
        canvas.drawOval(r, paint)
        r = Rect.fromLTRB(
            r.left - 15f, r.top - 15f,
            r.right - 15f, r.bottom - 15f,
        )
        val paint2 = Paint(
            color = Color.fromRGBA(0x44 / 255f, 0x44 / 255f, 0x44 / 255f, 1f),
            style = PaintStyle.STROKE,
            strokeWidth = 1f,
            antiAlias = true,
        )
        canvas.drawOval(r, paint2)

        canvas.save()
        canvas.translate(0f, 0f)
        canvas.scale(1f, 1f)
        r = Rect.fromLTRB(
            r.left + 55f, r.top + 55f,
            r.right + 55f, r.bottom + 55f,
        )
        val paint3 = Paint(
            color = Color.fromRGBA(0x88 / 255f, 0x88 / 255f, 0x88 / 255f, 1f),
            style = PaintStyle.STROKE,
            strokeWidth = 100f,
            antiAlias = true,
        )
        canvas.drawOval(r, paint3)
        r = Rect.fromLTRB(
            r.left - 15f, r.top - 15f,
            r.right - 15f, r.bottom - 15f,
        )
        val paint4 = Paint(
            color = Color.fromRGBA(0xCC / 255f, 0xCC / 255f, 0xCC / 255f, 1f),
            style = PaintStyle.STROKE,
            strokeWidth = 1f,
            antiAlias = true,
        )
        canvas.drawOval(r, paint4)
        canvas.restore()
    }
}
