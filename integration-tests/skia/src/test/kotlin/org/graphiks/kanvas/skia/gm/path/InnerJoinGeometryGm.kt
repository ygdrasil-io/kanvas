package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color

/**
 * Port of Skia's `gm/strokes.cpp::inner_join_geometry` (1000 × 700).
 * 8 acute-angle line-triangles laid out 4 × 2, each stroked at 100 px
 * overlaid with a red skeleton showing the stroker's emitted outline.
 * @see https://github.com/google/skia/blob/main/gm/strokes.cpp
 */
class InnerJoinGeometryGm : SkiaGm {
    override val name = "inner_join_geometry"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 1000
    override val height = 700

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val triangles = arrayOf(
            arrayOf(119f to 71f, 129f to 151f, 230f to 24f),
            arrayOf(200f to 144f, 129f to 151f, 230f to 24f),
            arrayOf(192f to 176f, 224f to 175f, 281f to 103f),
            arrayOf(233f to 205f, 224f to 175f, 281f to 103f),
            arrayOf(121f to 216f, 234f to 189f, 195f to 147f),
            arrayOf(141f to 216f, 254f to 189f, 238f to 250f),
            arrayOf(159f to 202f, 269f to 197f, 289f to 165f),
            arrayOf(159f to 202f, 269f to 197f, 287f to 227f),
        )

        val pathPaint = Paint(
            style = PaintStyle.STROKE,
            antiAlias = true,
            strokeWidth = 100f,
        )
        val skeletonPaint = Paint(
            style = PaintStyle.STROKE,
            antiAlias = true,
            strokeWidth = 0f,
            color = Color.RED,
        )

        canvas.translate(0f, 50f)
        for ((i, tri) in triangles.withIndex()) {
            val path = Path {
                moveTo(tri[0].first, tri[0].second)
                lineTo(tri[1].first, tri[1].second)
                lineTo(tri[2].first, tri[2].second)
            }
            canvas.drawPath(path, pathPaint)

            canvas.drawPath(path, skeletonPaint)

            canvas.translate(200f, 0f)
            if ((i + 1) % 4 == 0) {
                canvas.translate(-800f, 200f)
            }
        }
    }
}
