package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Port of Skia's `gm/pathmaskcache.cpp::PathMaskCache` (650 × 950).
 *
 * Tests path-coverage mask caching by drawing each of two paths
 * three times (identity, non-uniform scale, rotate), with each draw
 * repeated at a sub-pixel shifted position.
 * @see https://github.com/google/skia/blob/main/gm/pathmaskcache.cpp
 */
class PathMaskCacheGm : SkiaGm {
    override val name = "path_mask_cache"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 650
    override val height = 950

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val curvePath = makeCurvePath()
        val circleRectPath = Path { }.apply {
            fillType = FillType.EVEN_ODD
            addCircle(30f, 30f, 30f)
            addRect(RectF32.ofOriginSize(45f, 45f, 50f, 60f))
        }

        val pathBounds = listOf(
            RectF32(0f, 0f, 150f, 100f),  // curve path bounds
            RectF32(0f, 0f, 95f, 105f),   // circle+rect bounds
        )

        canvas.translate(5f, 5f)

        for ((pathIdx, path) in listOf(curvePath, circleRectPath).withIndex()) {
            val srcBounds = pathBounds[pathIdx]

            var ty = drawPathSet(canvas, path, srcBounds, Matrix3x3F32.Identity)
            canvas.translate(0f, ty)

            val nonUniformScale = Matrix3x3F32.scaling(0.5f, 2f)
            ty = drawPathSet(canvas, path, srcBounds, nonUniformScale)
            canvas.translate(0f, ty)

            ty = drawPathSet(canvas, path, srcBounds, Matrix3x3F32.rotation(60f))
            canvas.translate(0f, ty)
        }
    }

    private fun makeCurvePath(): Path {
        val path = Path { }
        path.moveTo(0f, 0f)
        path.lineTo(98f, 100f)
        path.lineTo(100f, 100f)
        path.quadTo(150f, 50f, 100f, 0f)
        path.quadTo(148f, 50f, 100f, 100f)
        path.quadTo(50f, 30f, 0f, 100f)
        return path
    }

    private fun mappedBounds(srcBounds: RectF32, m: Matrix3x3F32): RectF32 {
        val bl = m.transform(Point2F32(srcBounds.left, srcBounds.top))
        val br = m.transform(Point2F32(srcBounds.right, srcBounds.top))
        val tl = m.transform(Point2F32(srcBounds.left, srcBounds.bottom))
        val tr = m.transform(Point2F32(srcBounds.right, srcBounds.bottom))
        val xs = listOf(bl.x, br.x, tl.x, tr.x)
        val ys = listOf(bl.y, br.y, tl.y, tr.y)
        return RectF32(floor(xs.min()), floor(ys.min()), ceil(xs.max()), ceil(ys.max()))
    }

    private fun drawPathSet(c: GmCanvas, path: Path, srcBounds: RectF32, m: Matrix3x3F32): Float {
        val pad = 5f
        val paint = Paint(antiAlias = true)
        val bounds = mappedBounds(srcBounds, m)

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
}
