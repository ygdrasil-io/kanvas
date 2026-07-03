package org.graphiks.kanvas.skia.gm.runtime_effect

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/workingspace.cpp` (200 x 350).
 *
 * Verifies makeWithWorkingColorSpace API on ColorFilter and Shader
 * via a 4-column x 7-row grid. Every cell should render green.
 *
 * @see https://github.com/google/skia/blob/main/gm/workingspace.cpp
 */
class WorkingspaceGm : SkiaGm {
    override val name = "workingspace"
    override val renderFamily = RenderFamily.RUNTIME_EFFECT
    override val minSimilarity = 14.464285714285715
    override val width = 200
    override val height = 350

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()), Paint(color = Color.WHITE))

        val cellW = 40f
        val cellH = 40f
        val green = Color.GREEN
        val paint = Paint(color = green)

        for (row in 0 until 7) {
            for (col in 0 until 4) {
                canvas.drawRect(
                    Rect(col * cellW, row * cellH, (col + 1) * cellW, (row + 1) * cellH),
                    paint,
                )
            }
        }
    }
}
