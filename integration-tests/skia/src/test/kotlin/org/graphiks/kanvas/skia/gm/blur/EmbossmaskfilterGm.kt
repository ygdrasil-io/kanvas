package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/emboss.cpp::embossmaskfilter` (640 × 960).
 * Exercises mask filter across blur sigma variations and blend modes.
 * **Adaptation**: Kanvas does not have [SkEmbossMaskFilter]; [MaskFilter.Blur]
 * is used as a substitute.
 * @see https://github.com/google/skia/blob/main/gm/emboss.cpp
 */
class EmbossmaskfilterGm : SkiaGm {
    override val name = "embossmaskfilter"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 960

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val totalWidth = 640f
        val topHeight = 480f
        val bottomHeight = 480f

        val gridTopCols = 2
        val gridTopRows = 2
        val cellTopW = totalWidth / gridTopCols
        val cellTopH = topHeight / gridTopRows

        val labels = listOf(
            "Blur: 2.5", "Blur: 5.0", "Blur: 1.0", "Blur: 8.0",
        )
        val sigmas = listOf(2.5f, 5.0f, 1.0f, 8.0f)

        var exampleIndex = 0
        for (r in 0 until gridTopRows) {
            for (col in 0 until gridTopCols) {
                if (exampleIndex < sigmas.size) {
                    val cellBounds = Rect(
                        col * cellTopW + 10f, r * cellTopH + 10f,
                        col * cellTopW + cellTopW - 10f, r * cellTopH + cellTopH - 10f,
                    )
                    drawBlurExample(canvas, cellBounds, sigmas[exampleIndex], labels[exampleIndex])
                    exampleIndex++
                }
            }
        }

        val gridBotCols = 6
        val gridBotRows = 3
        val cellBotW = totalWidth / gridBotCols
        val cellBotH = bottomHeight / gridBotRows

        val sharedMF = MaskFilter.Blur(BlurStyle.NORMAL, blurRadius(2f))

        val blendModesToTest = listOf(
            BlendMode.SRC, BlendMode.DST, BlendMode.SRC_OVER, BlendMode.DST_OVER,
            BlendMode.SRC_IN, BlendMode.DST_IN, BlendMode.SRC_OUT, BlendMode.DST_OUT,
            BlendMode.SRC_ATOP, BlendMode.DST_ATOP, BlendMode.XOR, BlendMode.PLUS,
            BlendMode.MODULATE, BlendMode.SCREEN, BlendMode.OVERLAY, BlendMode.MULTIPLY,
            BlendMode.DARKEN, BlendMode.LIGHTEN,
        )

        var bIndex = 0
        for (r in 0 until gridBotRows) {
            for (col in 0 until gridBotCols) {
                if (bIndex < blendModesToTest.size) {
                    val mode = blendModesToTest[bIndex]
                    val cellBounds = Rect(
                        col * cellBotW + 10f, topHeight + r * cellBotH + 10f,
                        col * cellBotW + cellBotW - 10f, topHeight + r * cellBotH + cellBotH - 10f,
                    )
                    drawBlurBlendExample(canvas, cellBounds, sharedMF, mode, mode.name)
                    bIndex++
                }
            }
        }
    }

    private fun drawBlurExample(canvas: GmCanvas, bounds: Rect, sigma: Float, label: String) {
        canvas.save()
        canvas.clipRect(bounds)
        canvas.translate(bounds.left, bounds.top)

        val mf = MaskFilter.Blur(BlurStyle.NORMAL, blurRadius(sigma))

        val radius = bounds.width * 0.25f
        val offset = radius * 0.3f
        val cx = bounds.width * 0.5f
        val cy = bounds.height * 0.5f

        val paint1 = Paint(antiAlias = true, color = Color.RED, maskFilter = mf)
        val paint2 = Paint(antiAlias = true, color = Color.BLUE, maskFilter = mf)

        canvas.drawCircle(cx - offset, cy, radius, paint1)
        canvas.drawRect(
            Rect(cx + offset - radius, cy - radius, cx + offset + radius, cy + radius),
            paint2,
        )

        canvas.restore()
    }

    private fun drawBlurBlendExample(
        canvas: GmCanvas,
        bounds: Rect,
        blurFilter: MaskFilter,
        blendMode: BlendMode,
        label: String,
    ) {
        canvas.save()
        canvas.clipRect(bounds)
        canvas.translate(bounds.left, bounds.top)

        val radius = bounds.width * 0.25f
        val offset = radius * 0.3f
        val cx = bounds.width * 0.5f
        val cy = bounds.height * 0.5f

        val paint1 = Paint(antiAlias = true, color = Color.RED)
        val paint2 = Paint(antiAlias = true, color = Color.BLUE, maskFilter = blurFilter, blendMode = blendMode)

        canvas.drawCircle(cx - offset, cy, radius, paint1)
        canvas.drawRect(
            Rect(cx + offset - radius, cy - radius, cx + offset + radius, cy + radius),
            paint2,
        )

        canvas.restore()
    }

    private companion object {
        fun blurRadius(radius: Float): Float =
            if (radius > 0f) 0.57735f * radius + 0.5f else 0f
    }
}
