package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.CornerRadiiF32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32

/**
 * Port of Skia's `gm/rrect.cpp` (RRectBlurGM).
 * Draws rounded rects through mask-filter blur.
 * @see https://github.com/google/skia/blob/main/gm/rrect.cpp
 */
class RRectBlurGm : SkiaGm {
    override val name = "rrect_blurs"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.TRIVIAL
    override val minSimilarity = 0.0
    override val width = kWidth
    override val height = kHeight

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0x44 / 255f, 0x44 / 255f, 0x44 / 255f, 1f)

        drawBlurryRrect(
            canvas,
            cellY = 0,
            mf = MaskFilter.Blur(BlurStyle.NORMAL, 1.0f),
            color = ColorARGB.White,
            rr = RRectF32.of(RectF32(0f, 0f, 50f, 50f), CornerRadiiF32.of(10f, 15f)),
        )

        drawBlurryRrect(
            canvas,
            cellY = 100,
            mf = MaskFilter.Blur(BlurStyle.NORMAL, 0.5f),
            color = ColorARGB.fromRGBA(1f, 1f, 0f, 1f),
            rr = RRectF32.of(RectF32(0f, 0f, 60f, 80f), CornerRadiiF32.of(3.1f, 1.5f)),
        )

        val ninePatch = RRectF32.of(
            rect = RectF32(0f, 0f, 70f, 80f),
            topLeft = CornerRadiiF32.of(5f, 10f),
            topRight = CornerRadiiF32.of(13f, 10f),
            bottomRight = CornerRadiiF32.of(13f, 7f),
            bottomLeft = CornerRadiiF32.of(5f, 7f),
        )
        drawBlurryRrect(
            canvas,
            cellY = 200,
            mf = MaskFilter.Blur(BlurStyle.NORMAL, 2.5f),
            color = ColorARGB.fromRGBA(200f / 255f, 100f / 255f, 30f / 255f, 1f),
            rr = ninePatch,
        )

        val complex = RRectF32.of(
            rect = RectF32(0f, 0f, 90f, 90f),
            topLeft = CornerRadiiF32.of(0f, 0f),
            topRight = CornerRadiiF32.of(20f, 1f),
            bottomRight = CornerRadiiF32.of(30f, 30f),
            bottomLeft = CornerRadiiF32.of(10f, 30f),
        )
        drawBlurryRrect(
            canvas,
            cellY = 300,
            mf = MaskFilter.Blur(BlurStyle.NORMAL, 1.1f),
            color = ColorARGB.fromRGBA(35f / 255f, 120f / 255f, 220f / 255f, 1f),
            rr = complex,
        )

        val linePaint = Paint(
            color = ColorARGB.White,
            style = org.graphiks.kanvas.paint.PaintStyle.STROKE,
            strokeWidth = 1f,
        )
        canvas.drawLine(100f, 0f, 100f, kHeight.toFloat(), linePaint)
        canvas.drawLine(0f, 100f, kWidth.toFloat(), 100f, linePaint)
        canvas.drawLine(0f, 200f, kWidth.toFloat(), 200f, linePaint)
        canvas.drawLine(0f, 300f, kWidth.toFloat(), 300f, linePaint)
    }

    private fun drawBlurryRrect(
        canvas: GmCanvas,
        cellY: Int,
        mf: MaskFilter.Blur,
        color: ColorARGB,
        rr: RRectF32,
    ) {
        val paint = Paint(
            color = color,
            maskFilter = mf,
        )

        val paddingX = ((kCellSize - rr.rect.width()) / 2f).toInt()
        val paddingY = ((kCellSize - rr.rect.height()) / 2f).toInt()

        val leftRRect = offsetRRect(rr, paddingX.toFloat(), paddingY.toFloat() + cellY)
        val leftPath = Path { }.apply { addRRect(leftRRect) }
        canvas.drawPath(leftPath, paint)

        val rightRRect = offsetRRect(rr, 2f * kCellSize + paddingX, paddingY.toFloat() + cellY)
        val rightPath = Path { }.apply { addRRect(rightRRect) }
        canvas.drawPath(rightPath, paint)
    }

    private fun offsetRRect(rr: RRectF32, dx: Float, dy: Float): RRectF32 {
        val offsetRect = RectF32.ofLTRB(
            rr.rect.left + dx,
            rr.rect.top + dy,
            rr.rect.right + dx,
            rr.rect.bottom + dy,
        )
        return RRectF32.of(
            rect = offsetRect,
            topLeft = rr.topLeft,
            topRight = rr.topRight,
            bottomRight = rr.bottomRight,
            bottomLeft = rr.bottomLeft,
        )
    }

    private companion object {
        const val kWidth = 300
        const val kHeight = 400
        const val kCellSize = 100
    }
}
