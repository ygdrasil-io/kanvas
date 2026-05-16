package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkStroker
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/strokes.cpp::inner_join_geometry` (DEF_SIMPLE_GM,
 * 1000 × 700).
 *
 * 8 acute-angle line-triangles laid out 4 × 2, each stroked at 100 px
 * (`kStroke_Style`, default `kMiter_Join`) overlaid with a red 0-width
 * skeleton showing the stroker's emitted outline. Originally exposed
 * `skbug.com/40043052` — missing inner join geometry on highly-acute
 * corners.
 *
 * Upstream uses `skpathutils::FillPathWithPaint(path, paint)` to extract
 * the stroker's outline as a fillable path for the skeleton overlay.
 * We don't expose that helper, but the stroker is reachable directly
 * via [SkStroker.fromPaint] + [SkStroker.stroke]. The result is
 * functionally equivalent : a `SkPath` containing the outline contours
 * that, when filled, would render the same as filling the original
 * path under the stroke paint.
 */
public class InnerJoinGeometryGM : GM() {

    override fun getName(): String = "inner_join_geometry"
    override fun getISize(): SkISize = SkISize.Make(1000, 700)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

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

        val pathPaint = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            isAntiAlias = true
            strokeWidth = 100f
        }
        val skeletonPaint = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            isAntiAlias = true
            strokeWidth = 0f
            color = SK_ColorRED
        }

        c.translate(0f, 50f)
        for ((i, tri) in triangles.withIndex()) {
            val path = SkPath.Polygon(tri, isClosed = false)
            c.drawPath(path, pathPaint)

            // Skeleton — substitute for FillPathWithPaint via direct
            // SkStroker invocation. Same stroker that drawPath uses
            // internally, so the outline matches our wide-stroke render
            // by construction.
            val outline = SkStroker.fromPaint(pathPaint).stroke(path)
            c.drawPath(outline, skeletonPaint)

            c.translate(200f, 0f)
            if ((i + 1) % 4 == 0) {
                c.translate(-800f, 200f)
            }
        }
    }
}
