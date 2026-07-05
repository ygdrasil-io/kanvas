package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import kotlin.math.exp

/**
 * Port of Skia's `gm/crbug_1041204.cpp::crbug_10141204`.
 * Stress test for non-axis-aligned transforms with extreme coordinate
 * magnitudes — should fill the canvas with solid blue.
 * @see https://github.com/google/skia/blob/main/gm/crbug_1041204.cpp
 */
class Crbug10141204Gm : SkiaGm {
    override val name = "crbug_10141204"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 512

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val extraZoom = exp(-2.3).toFloat()
        canvas.scale(extraZoom, extraZoom)
        canvas.scale(2f, 2f)
        canvas.concat(Matrix33.makeAll(
            -0.0005550860255665798f, -0.0030798374421905717f, -0.014111959825129805f,
            -0.07569627776417084f, 232.00000000000017f, 39.999999999999936f,
            0f, 0f, 1f,
        ))
        canvas.translate(-3040103.0493857153f, 337502.1103282161f)
        canvas.scale(9783.93962050256f, -9783.93962050256f)

        val paint = Paint(
            color = Color.BLUE,
            antiAlias = true,
        )
        canvas.drawRect(Rect.fromXYWH(0f, 0f, 512f, 512f), paint)
    }
}
