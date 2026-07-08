package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color

/**
 * Port of Skia's `gm/manypathatlases.cpp`.
 * Tests path atlas behavior with clipped path draws.
 * @see https://github.com/google/skia/blob/main/gm/manypathatlases.cpp
 */
class ManyPathAtlasesGm : SkiaGm {
    override val name = "manypathatlases"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 128
    override val height = 128

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 0f)
        val path = Path {
            moveTo(20f, 0f)
            lineTo(108f, 0f)
            cubicTo(108f, 20f, 108f, 20f, 128f, 20f)
            lineTo(128f, 108f)
            cubicTo(108f, 108f, 108f, 108f, 108f, 128f)
            lineTo(20f, 128f)
            cubicTo(20f, 108f, 20f, 108f, 0f, 108f)
            lineTo(0f, 20f)
            cubicTo(20f, 20f, 20f, 20f, 20f, 0f)
            close()
        }
        canvas.drawPath(path, Paint(antiAlias = true, color = Color.fromRGBA(0.03f, 0.91f, 0.87f, 1f)))
    }
}
