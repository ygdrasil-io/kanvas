package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/blurrect.cpp::blur_matrix_rect` (650 x 685).
 * Draws a fixed 14 x 60 rect under 6 affine matrices at 5 blur sigmas.
 * @see https://github.com/google/skia/blob/main/gm/blurrect.cpp
 */
class BlurMatrixRectGm : SkiaGm {
    override val name = "blur_matrix_rect"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 87.0
    override val width = 650
    override val height = 685

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val kRect = Rect.fromXYWH(0f, 0f, 14f, 60f)
        val kSigmas = floatArrayOf(0.5f, 1.2f, 2.3f, 3.9f, 7.4f)

        val cx = kRect.center.x
        val cy = kRect.center.y

        fun rotateAround(deg: Float, px: Float, py: Float): Matrix33 =
            Matrix33.translate(px, py) * Matrix33.rotate(deg) * Matrix33.translate(-px, -py)

        val matrices = mutableListOf<Matrix33>()

        matrices.add(rotateAround(4f, cx, cy))
        matrices.add(rotateAround(63f, cx, cy))
        matrices.add(rotateAround(30f, cx, cy) * Matrix33.scale(1.1f, 0.5f))

        val m3 = rotateAround(147f, cx, cy) * Matrix33.scale(3f, 0.1f)
        matrices.add(m3)

        val mirror = Matrix33.rotate(45f) * Matrix33.scale(1f, -1f) * Matrix33.rotate(-45f)
        matrices.add(mirror * m3)

        matrices.add(rotateAround(197f, cx, cy) * Matrix33.skew(0.3f, -0.5f))

        fun mapRect(m: Matrix33, r: Rect): Rect {
            val p0 = m * Point(r.left, r.top)
            val p1 = m * Point(r.right, r.top)
            val p2 = m * Point(r.right, r.bottom)
            val p3 = m * Point(r.left, r.bottom)
            val l = minOf(p0.x, p1.x, p2.x, p3.x)
            val t = minOf(p0.y, p1.y, p2.y, p3.y)
            val rt = maxOf(p0.x, p1.x, p2.x, p3.x)
            val b = maxOf(p0.y, p1.y, p2.y, p3.y)
            return Rect(l, t, rt, b)
        }

        var bounds = Rect(0f, 0f, 0f, 0f)
        for (m in matrices) {
            val mapped = mapRect(m, kRect)
            bounds = Rect(
                minOf(bounds.left, mapped.left),
                minOf(bounds.top, mapped.top),
                maxOf(bounds.right, mapped.right),
                maxOf(bounds.bottom, mapped.bottom),
            )
        }
        val blurPad = 2f * kSigmas[kSigmas.size - 1]
        bounds = Rect(
            bounds.left - blurPad, bounds.top - blurPad,
            bounds.right + blurPad, bounds.bottom + blurPad,
        )

        canvas.translate(-bounds.left, -bounds.top)

        for (sigma in kSigmas) {
            val paint = Paint(
                maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, sigma),
            )
            canvas.save()
            for (m in matrices) {
                canvas.save()
                canvas.concat(m)
                canvas.drawRect(kRect, paint)
                canvas.restore()
                canvas.translate(0f, bounds.height)
            }
            canvas.restore()
            canvas.translate(bounds.width, 0f)
        }
    }
}
