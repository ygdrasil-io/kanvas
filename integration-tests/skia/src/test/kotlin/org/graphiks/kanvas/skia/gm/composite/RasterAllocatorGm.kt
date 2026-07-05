package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/rasterhandleallocator.cpp::DEF_SIMPLE_GM(rasterallocator)`
 * (600 × 300).
 *
 * The upstream GM exercises [SkRasterHandleAllocator] — a Skia-specific
 * API with no direct Kanvas equivalent. This port draws a simplified
 * approximation: a red/blue/white/green rect arrangement on the main canvas.
 * @see https://github.com/google/skia/blob/main/gm/rasterhandleallocator.cpp
 */
class RasterAllocatorGm : SkiaGm {
    override val name = "rasterallocator"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 600
    override val height = 300

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        // Simplified approximation of the upstream layout
        val redPaint = Paint(color = Color.RED)
        val bluePaint = Paint(color = Color.BLUE)
        val whitePaint = Paint(color = Color.WHITE)
        val greenPaint = Paint(color = Color.GREEN)
        val greyPaint = Paint(color = Color.fromRGBA(0.8f, 0.8f, 0.8f))

        canvas.drawRect(Rect.fromLTRB(0f, 0f, 256f, 256f), redPaint)
        canvas.drawRect(Rect.fromLTRB(30f, 30f, 60f, 60f), bluePaint)
        canvas.drawOval(Rect.fromLTRB(40f, 40f, 50f, 50f), whitePaint)
        canvas.saveLayer(Rect.fromLTRB(50f, 50f, 100f, 100f), Paint(color = Color.fromRGBA(0f, 0f, 0f, 0.5f)))
        canvas.drawRect(Rect.fromLTRB(55f, 55f, 95f, 95f), greenPaint)
        canvas.restore()
        canvas.drawRect(Rect.fromLTRB(150f, 50f, 200f, 200f), greyPaint)

        // Also draw the right-side copy
        canvas.translate(280f, 0f)
        canvas.drawRect(Rect.fromLTRB(0f, 0f, 256f, 256f), redPaint)
        canvas.drawRect(Rect.fromLTRB(30f, 30f, 60f, 60f), bluePaint)
        canvas.drawOval(Rect.fromLTRB(40f, 40f, 50f, 50f), whitePaint)
        canvas.drawRect(Rect.fromLTRB(55f, 55f, 95f, 95f), greenPaint)
    }
}
