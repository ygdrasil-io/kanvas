package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathFillType
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/pathmaskcache.cpp::PathMaskCache` (650 × 950).
 *
 * Tests the path-coverage mask caching by drawing each of two paths
 * three times (identity matrix, non-uniform `scale(0.5, 2)`, and
 * `rotate(60°, centre)`), with each draw repeated at a slightly
 * shifted sub-pixel translation so the mask cache must regenerate
 * the coverage per phase. Two paths :
 *  - a hand-crafted curve mixing line + 3 conic segments,
 *  - an even-odd `(circle ∪ rect)` shape.
 *
 * Each invocation pairs a baseline draw at the integer-translated
 * position with a re-draw at `+0.002` offset (sub-pixel shift to
 * exercise the cache on the same logical bbox).
 */
public class PathMaskCacheGM : GM() {

    override fun getName(): String = "path_mask_cache"
    override fun getISize(): SkISize = SkISize.Make(650, 950)

    private fun drawPathSet(c: SkCanvas, path: SkPath, m: SkMatrix): Float {
        val pad = 5f
        val paint = SkPaint().apply { isAntiAlias = true }
        val srcBounds = path.computeBounds()
        var bounds = m.mapRect(srcBounds)
        bounds = SkRect.MakeLTRB(
            kotlin.math.floor(bounds.left), kotlin.math.floor(bounds.top),
            kotlin.math.ceil(bounds.right), kotlin.math.ceil(bounds.bottom),
        )
        c.save()
        c.translate(-bounds.left, -bounds.top)
        c.save()
        c.concat(m)
        c.drawPath(path, paint)
        c.restore()
        c.translate(bounds.width() + pad + 0.002f, 0f)
        c.save()
        c.concat(m)
        c.drawPath(path, paint)
        c.restore()
        c.restore()
        return bounds.bottom + pad
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val paths = arrayOf(
            SkPathBuilder()
                .moveTo(0f, 0f)
                .lineTo(98f, 100f)
                .lineTo(100f, 100f)
                .conicTo(150f, 50f, 100f, 0f, 0.6f)
                .conicTo(148f, 50f, 100f, 100f, 0.6f)
                .conicTo(50f, 30f, 0f, 100f, 0.9f)
                .detach(),
            SkPathBuilder()
                .setFillType(SkPathFillType.kEvenOdd)
                .addCircle(30f, 30f, 30f)
                .addRect(SkRect.MakeXYWH(45f, 45f, 50f, 60f))
                .detach(),
        )

        c.translate(5f, 5f)

        for (path in paths) {
            var ty = drawPathSet(c, path, SkMatrix.Identity)
            c.translate(0f, ty)

            val nonUniformScale = SkMatrix.MakeScale(0.5f, 2f)
            ty = drawPathSet(c, path, nonUniformScale)
            c.translate(0f, ty)

            val bounds = path.computeBounds()
            val rotate = SkMatrix.MakeRotate(60f, bounds.centerX(), bounds.centerY())
            ty = drawPathSet(c, path, rotate)
            c.translate(0f, ty)
        }
    }
}
