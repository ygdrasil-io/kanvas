package org.graphiks.kanvas.skia.gm.clip

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/clipdrawdraw.cpp::clipdrawdraw` (DEF_SIMPLE_GM_BG,
 * 512 × 512, BG = `0xFFCCCCCC`).
 *
 * Reproduces crbug.com/423834 : the pattern
 * ```
 * save(); clipRect(rect, noAA); drawRect(bigRect, noAA); restore();
 * drawRect(rect, noAA);
 * ```
 * could leave 1-px wide remnants of the first rect when integer-edge
 * rounding diverged between `clipRect` and `drawRect`.
 * @see https://github.com/google/skia/blob/main/gm/clipdrawdraw.cpp
 */
class ClipDrawDrawGm : SkiaGm {
    override val name = "clipdrawdraw"
    override val renderFamily = RenderFamily.CLIP
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 24.0
    override val width = 512
    override val height = 512

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0xCC / 255f, 0xCC / 255f, 0xCC / 255f)

        val paint = Paint(antiAlias = false)

        drawCase(canvas, paint, Rect(136.5f, 137.5f, 338.5f, 293.5f))
        drawCase(canvas, paint, Rect(207.5f, 179.499f, 530.5f, 429.5f))
    }

    private fun drawCase(canvas: GmCanvas, paint: Paint, rect: Rect) {
        canvas.save()
        canvas.save()
        canvas.clipRect(rect)
        canvas.drawRect(Rect(0f, 0f, 600f, 600f), paint)
        canvas.restore()

        val whitePaint = paint.copy(color = Color.WHITE)
        canvas.drawRect(rect, whitePaint)
        canvas.restore()
    }
}
