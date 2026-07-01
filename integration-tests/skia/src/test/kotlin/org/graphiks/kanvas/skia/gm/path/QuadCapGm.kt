package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import kotlin.math.PI
import kotlin.math.sqrt

/**
 * Port of Skia's `gm/strokes.cpp::quadcap` (DEF_SIMPLE_GM, 200 × 200).
 * Two AA-stroked quadratic Béziers comparing extended butt-cap vs. round-cap.
 * @see https://github.com/google/skia/blob/main/gm/strokes.cpp
 */
class QuadCapGm : SkiaGm {
    override val name = "quadcap"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 94.8
    override val width = 200
    override val height = 200

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(
            antiAlias = true,
            style = PaintStyle.STROKE,
            strokeWidth = 0f,
        )
        val pts = arrayOf(
            105.738571f to 13.126318f,
            105.738571f to 13.126318f,
            123.753784f to 1f,
        )
        val tx0 = pts[1].first - pts[2].first
        val ty0 = pts[1].second - pts[2].second
        val len = sqrt((tx0 * tx0 + ty0 * ty0).toDouble()).toFloat()
        val tx = tx0 / len
        val ty = ty0 / len
        val capOutset = (PI / 8.0).toFloat()

        val pts2 = arrayOf(
            (pts[0].first + tx * capOutset) to (pts[0].second + ty * capOutset),
            (pts[1].first + tx * capOutset) to (pts[1].second + ty * capOutset),
            (pts[2].first - tx * capOutset) to (pts[2].second - ty * capOutset),
        )

        val ext = Path {
            moveTo(pts2[0].first, pts2[0].second)
            quadTo(pts2[1].first, pts2[1].second, pts2[2].first, pts2[2].second)
        }
        canvas.drawPath(ext, paint)

        val orig = Path {
            moveTo(pts[0].first, pts[0].second)
            quadTo(pts[1].first, pts[1].second, pts[2].first, pts[2].second)
        }
        canvas.translate(30f, 0f)
        canvas.drawPath(orig, paint.copy(strokeCap = StrokeCap.ROUND))
    }
}
