package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point

/**
 * Port of Skia's `gm/stlouisarch.cpp`.
 * Six paths drawn as hairlines under a Y-flip — quad, cubic, conic arches and degenerate variants.
 * @see https://github.com/google/skia/blob/main/gm/stlouisarch.cpp
 */
class StLouisArchGm : SkiaGm {
    override val name = "stlouisarch"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = kWidth.toInt()
    override val height = kHeight.toInt()

    private val paths: List<Path> = listOf(
        Path { moveTo(0f, 0f); quadTo(kWidth / 2f, kHeight, kWidth, 0f) },
        Path { moveTo(0f, kHeight / 2f + 10f); quadTo(0f, kHeight / 2f + 10f, kWidth, kHeight / 2f + 10f) },
        Path { moveTo(0f, 0f); cubicTo(0f, kHeight, kWidth, kHeight, kWidth, 0f) },
        Path { moveTo(0f, kHeight / 2f); cubicTo(0f, kHeight / 2f, 0f, kHeight / 2f, kWidth, kHeight / 2f) },
        Path { moveTo(0f, 0f); quadTo(kWidth / 2f, kHeight, kWidth, 0f) },
        Path { moveTo(0f, kHeight / 2f - 10f); quadTo(0f, kHeight / 2f - 10f, kWidth, kHeight / 2f - 10f) },
    )

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.save()
        canvas.scale(1f, -1f)
        canvas.translate(0f, -kHeight)
        val paint = Paint(color = Color.BLACK, antiAlias = true, style = PaintStyle.STROKE, strokeWidth = 0f)
        for (p in paths) {
            canvas.drawPath(p, paint)
        }
        canvas.restore()
    }

    private companion object {
        const val kWidth: Float = 256f
        const val kHeight: Float = 256f
    }
}
