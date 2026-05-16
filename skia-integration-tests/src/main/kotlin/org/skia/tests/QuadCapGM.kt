package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkISize
import kotlin.math.PI
import kotlin.math.sqrt

/**
 * Port of Skia's `gm/strokes.cpp::quadcap` (DEF_SIMPLE_GM, 200 × 200).
 *
 * Two AA-stroked quadratic Béziers. The first uses `kButt_Cap` (default)
 * and is "extended" by `π/8` along its tangent direction at both ends ;
 * the second uses `kRound_Cap` on the original (unextended) quad. Visual
 * comparison verifies the stroker emits matching outline geometry for
 * the two cases — i.e., a round-cap should reach the same pixel
 * boundary as the extended-tangent butt-cap of `π/8`.
 */
public class QuadCapGM : GM() {

    override fun getName(): String = "quadcap"
    override fun getISize(): SkISize = SkISize.Make(200, 200)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val p = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 0f
        }
        val pts = arrayOf(
            105.738571f to 13.126318f,
            105.738571f to 13.126318f,
            123.753784f to 1f,
        )
        // Tangent at endpoint = pts[1] - pts[2]; normalize.
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

        val ext = SkPathBuilder()
            .moveTo(pts2[0].first, pts2[0].second)
            .quadTo(pts2[1].first, pts2[1].second, pts2[2].first, pts2[2].second)
            .detach()
        c.drawPath(ext, p)

        val orig = SkPathBuilder()
            .moveTo(pts[0].first, pts[0].second)
            .quadTo(pts[1].first, pts[1].second, pts[2].first, pts[2].second)
            .detach()
        p.strokeCap = SkPaint.Cap.kRound_Cap
        c.translate(30f, 0f)
        c.drawPath(orig, p)
    }
}
