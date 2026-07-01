package org.graphiks.kanvas.skia.gm.path

/**
 * Port of Skia's `gm/longwavyline.cpp`.
 * Tests stroked wavy quad path spanning a wide coordinate range.
 * @see https://github.com/google/skia/blob/main/gm/longwavyline.cpp
 */

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

class LongWavyLineGm : SkiaGm {
    override val name = "longwavyline"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 512

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(
            style = PaintStyle.STROKE,
            strokeWidth = 2f,
            antiAlias = true,
        )
        val wavy = Path {
            moveTo(-10000f, 100f)
            var i = -10000f
            while (i < 10000f) {
                quadTo(i + 5f, 95f, i + 10f, 100f)
                quadTo(i + 15f, 105f, i + 20f, 100f)
                i += 20f
            }
        }
        canvas.drawPath(wavy, paint)
    }
}
