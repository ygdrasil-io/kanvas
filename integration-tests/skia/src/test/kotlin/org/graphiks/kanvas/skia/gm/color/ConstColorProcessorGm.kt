package org.graphiks.kanvas.skia.gm.color

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/constcolorprocessor.cpp`.
 * Tests constant-color processor with various input and paint color combinations.
 * @see https://github.com/google/skia/blob/main/gm/constcolorprocessor.cpp
 */
class ConstColorProcessorGm : SkiaGm {
    override val name = "const_color_processor"
    override val renderFamily = RenderFamily.COLOR
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 820
    override val height = 500

    private val kPad = 10f
    private val kRectSize = 20f

    private val kColors = listOf(
        Color.fromRGBA(1f, 1f, 1f, 1f),
        Color.fromRGBA(1f, 0f, 1f, 1f),
        Color.fromRGBA(0f, 0f, 0f, 0.5f),
        Color.TRANSPARENT,
    )

    private val kPaintColors = listOf(
        Color.fromRGBA(1f, 1f, 1f, 1f),
        Color.fromRGBA(0f, 0f, 1f, 1f),
        Color.fromRGBA(0f, 0f, 0f, 0.5f),
        Color.TRANSPARENT,
    )

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0xDD.toFloat() / 255f, 0xDD.toFloat() / 255f, 0xDD.toFloat() / 255f, 1f)

        var x = kPad
        var y = kPad
        var maxW = 0f

        val allPaintColors = kPaintColors + null

        for (paintColor in allPaintColors) {
            for (procColor in kColors) {
                canvas.save()
                canvas.translate(x, y)

                val rect = Rect.fromXYWH(0f, 0f, kRectSize, kRectSize)
                val labelColor = Color.fromRGBA(0f, 0f, 0f, 1f)

                val p = if (paintColor != null) {
                    Paint(color = paintColor)
                } else {
                    Paint()
                }
                canvas.drawRect(rect, p)

                val overPaint = Paint(color = procColor)
                canvas.drawRect(rect, overPaint)

                val inputLabel = if (paintColor != null) {
                    "Input: color"
                } else {
                    "Input: default"
                }
                val procLabel = "Proc: [color]"

                canvas.drawString(inputLabel, rect.right + kPad, 10f, Font12, Paint(color = labelColor))
                canvas.drawString(procLabel, rect.right + kPad, 22f, Font12, Paint(color = labelColor))

                canvas.drawRect(rect, Paint(color = labelColor, strokeWidth = 1f))

                canvas.restore()

                val widthNeeded = (rect.right + kPad + 80f)
                maxW = maxOf(maxW, widthNeeded)
                y += kRectSize + kPad
                if (y + kRectSize > height) {
                    y = kPad
                    x += maxW + kPad
                    maxW = 0f
                }
            }
        }
    }

    private companion object {
        val Font12 = org.graphiks.kanvas.text.Font(
            org.graphiks.kanvas.text.Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!, 10f
        )
    }
}
