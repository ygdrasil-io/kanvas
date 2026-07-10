package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/xfermodes.cpp`.
 * Canonical visual regression for 29 SkBlendMode values.
 * @see https://github.com/google/skia/blob/main/gm/xfermodes.cpp
 */
class XfermodesGm : SkiaGm {
    override val name = "xfermodes"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 45.0
    override val width = 1990
    override val height = 570

    private data class ModeRow(val mode: BlendMode, val mask: Int)

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.translate(10f, 20f)

        val w = 64f
        val h = 64f
        val kWrap = 5

        val modes = listOf(
            ModeRow(BlendMode.CLEAR, kAll),
            ModeRow(BlendMode.SRC, kAll),
            ModeRow(BlendMode.DST, kAll),
            ModeRow(BlendMode.SRC_OVER, kAll),
            ModeRow(BlendMode.DST_OVER, kAll),
            ModeRow(BlendMode.SRC_IN, kAll),
            ModeRow(BlendMode.DST_IN, kAll),
            ModeRow(BlendMode.SRC_OUT, kAll),
            ModeRow(BlendMode.DST_OUT, kAll),
            ModeRow(BlendMode.SRC_ATOP, kAll),
            ModeRow(BlendMode.DST_ATOP, kAll),
            ModeRow(BlendMode.XOR, kBasic),
            ModeRow(BlendMode.PLUS, kBasic),
            ModeRow(BlendMode.MODULATE, kAll),
            ModeRow(BlendMode.SCREEN, kBasic),
            ModeRow(BlendMode.OVERLAY, kBasic),
            ModeRow(BlendMode.DARKEN, kBasic),
            ModeRow(BlendMode.LIGHTEN, kBasic),
            ModeRow(BlendMode.COLOR_DODGE, kBasic),
            ModeRow(BlendMode.COLOR_BURN, kBasic),
            ModeRow(BlendMode.HARD_LIGHT, kBasic),
            ModeRow(BlendMode.SOFT_LIGHT, kBasic),
            ModeRow(BlendMode.DIFFERENCE, kBasic),
            ModeRow(BlendMode.EXCLUSION, kBasic),
            ModeRow(BlendMode.MULTIPLY, kAll),
            ModeRow(BlendMode.HUE, kBasic),
            ModeRow(BlendMode.SATURATION, kBasic),
            ModeRow(BlendMode.COLOR, kBasic),
            ModeRow(BlendMode.LUMINOSITY, kBasic),
        )

        var x0 = 0f
        var y0 = 0f
        var sourceType = 1
        while (sourceType and kAll != 0) {
            var x = x0
            var y = y0
            for ((i, row) in modes.withIndex()) {
                if ((row.mask and sourceType) == 0) continue
                val r = Rect.fromLTRB(x, y, x + w, y + h)

                // Background: dark/light alternating rect cells
                val bgColor = if ((i / 5 + i) % 2 == 0)
                    Color.fromRGBA(0.8f, 0.8f, 0.8f, 1f)
                else
                    Color.fromRGBA(0.4f, 0.4f, 0.4f, 1f)
                canvas.drawRect(r, Paint(color = bgColor))

                // Draw source shapes with blend mode
                canvas.save()
                val srcPaint = Paint(
                    color = Color.fromRGBA(0.4f, 0.7f, 1f, 0.8f),
                    blendMode = row.mode,
                )
                val dstPaint = Paint(
                    color = Color.fromRGBA(1f, 0.8f, 0.3f, 0.8f),
                )
                canvas.drawRect(
                    Rect.fromLTRB(x + 4f, y + 4f, x + w - 4f, y + h - 4f),
                    dstPaint,
                )
                canvas.drawCircle(x + w / 2f, y + h / 2f, w / 4f, srcPaint)
                canvas.restore()

                // Stroke frame
                val frame = Rect.fromLTRB(
                    r.left - 0.5f, r.top - 0.5f,
                    r.right + 0.5f, r.bottom + 0.5f,
                )
                canvas.drawRect(frame, Paint(style = PaintStyle.STROKE))

                x += w + 10f
                if ((i % kWrap) == kWrap - 1) {
                    x = x0
                    y += h + 30f
                }
            }
            if (y < 320f) {
                if (x > x0) y += h + 30f
                y0 = y
            } else {
                x0 += 400f
                y0 = 0f
            }
            sourceType = sourceType shl 1
        }
    }

    private companion object {
        const val kAll: Int = 0xFF
        const val kBasic: Int = 0x03
    }
}
