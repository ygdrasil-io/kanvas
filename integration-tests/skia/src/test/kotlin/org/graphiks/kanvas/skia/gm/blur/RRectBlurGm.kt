package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.CornerRadii
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect

class RRectBlurGm : SkiaGm {
    override val name = "rrect_blurs"
    override val renderFamily = RenderFamily.BLUR
    override val minSimilarity = 0.0
    override val width = kWidth
    override val height = kHeight

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0x44 / 255f, 0x44 / 255f, 0x44 / 255f, 1f)

        drawBlurryRrect(
            canvas,
            cellY = 0,
            mf = MaskFilter.Blur(BlurStyle.NORMAL, 1.0f),
            color = Color.WHITE,
            rr = RRect(Rect(0f, 0f, 50f, 50f), CornerRadii(10f, 15f)),
        )

        drawBlurryRrect(
            canvas,
            cellY = 100,
            mf = MaskFilter.Blur(BlurStyle.NORMAL, 0.5f),
            color = Color.fromRGBA(1f, 1f, 0f, 1f),
            rr = RRect(Rect(0f, 0f, 60f, 80f), CornerRadii(3.1f, 1.5f)),
        )

        val ninePatch = RRect(
            rect = Rect(0f, 0f, 70f, 80f),
            topLeft = CornerRadii(5f, 10f),
            topRight = CornerRadii(13f, 10f),
            bottomRight = CornerRadii(13f, 7f),
            bottomLeft = CornerRadii(5f, 7f),
        )
        drawBlurryRrect(
            canvas,
            cellY = 200,
            mf = MaskFilter.Blur(BlurStyle.NORMAL, 2.5f),
            color = Color.fromRGBA(200f / 255f, 100f / 255f, 30f / 255f, 1f),
            rr = ninePatch,
        )

        val complex = RRect(
            rect = Rect(0f, 0f, 90f, 90f),
            topLeft = CornerRadii(0f, 0f),
            topRight = CornerRadii(20f, 1f),
            bottomRight = CornerRadii(30f, 30f),
            bottomLeft = CornerRadii(10f, 30f),
        )
        drawBlurryRrect(
            canvas,
            cellY = 300,
            mf = MaskFilter.Blur(BlurStyle.NORMAL, 1.1f),
            color = Color.fromRGBA(35f / 255f, 120f / 255f, 220f / 255f, 1f),
            rr = complex,
        )

        val linePaint = Paint(
            color = Color.WHITE,
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
        color: Color,
        rr: RRect,
    ) {
        val paint = Paint(
            color = color,
            maskFilter = mf,
        )

        val paddingX = ((kCellSize - rr.rect.width) / 2f).toInt()
        val paddingY = ((kCellSize - rr.rect.height) / 2f).toInt()

        val leftRRect = offsetRRect(rr, paddingX.toFloat(), paddingY.toFloat() + cellY)
        val leftPath = Path { }.apply { addRRect(leftRRect) }
        canvas.drawPath(leftPath, paint)

        val rightRRect = offsetRRect(rr, 2f * kCellSize + paddingX, paddingY.toFloat() + cellY)
        val rightPath = Path { }.apply { addRRect(rightRRect) }
        canvas.drawPath(rightPath, paint)
    }

    private fun offsetRRect(rr: RRect, dx: Float, dy: Float): RRect {
        val offsetRect = Rect.fromLTRB(
            rr.rect.left + dx,
            rr.rect.top + dy,
            rr.rect.right + dx,
            rr.rect.bottom + dy,
        )
        return RRect(
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
