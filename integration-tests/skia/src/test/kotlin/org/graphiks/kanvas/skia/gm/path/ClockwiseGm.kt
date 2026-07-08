package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/clockwise.cpp`.
 * Tests clockwise vs counter-clockwise path winding with colored fills.
 * @see https://github.com/google/skia/blob/main/gm/clockwise.cpp
 */
class ClockwiseGm : SkiaGm {
    override val name = "clockwise"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 300
    override val height = 200

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawRect(
            Rect(0f, 0f, width.toFloat(), height.toFloat()),
            Paint(color = Color.BLACK),
        )

        for (col in 0 until 3) {
            for (row in 0 until 2) {
                val x = col * 100
                val y = row * 100
                val flipWinding = col == 2
                drawCell(canvas, x, y, flipWinding)
            }
        }
    }

    private fun drawCell(canvas: GmCanvas, x: Int, y: Int, flipWinding: Boolean) {
        val cwColor = if (flipWinding) Color.fromRGBA(1f, 0f, 0f, 1f) else Color.fromRGBA(0f, 1f, 0f, 1f)
        val ccwColor = if (flipWinding) Color.fromRGBA(0f, 1f, 0f, 1f) else Color.fromRGBA(1f, 0f, 0f, 1f)

        canvas.save()
        canvas.translate(x.toFloat(), y.toFloat())

        canvas.drawPath(
            Path {
                moveTo(100f, 0f)
                lineTo(0f, 100f)
                lineTo(0f, 0f)
                close()
            },
            Paint(color = cwColor),
        )

        canvas.drawPath(
            Path {
                moveTo(0f, 100f)
                lineTo(0f, 0f)
                lineTo(100f, 100f)
                close()
            },
            Paint(color = ccwColor),
        )

        canvas.restore()
    }
}
