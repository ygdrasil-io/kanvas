package org.graphiks.kanvas.skia.gm.path

/**
 * Port of Skia's `gm/nestedhairlinesquare.cpp`.
 * Tests nested hairline-width square rendering with non-integer scaling.
 * @see https://github.com/google/skia/blob/main/gm/nestedhairlinesquare.cpp
 */

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

class NestedHairlineSquareGm : SkiaGm {
    override val name = "nested_hairline_square"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 92.6
    override val width = 64
    override val height = 64

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val square = Path { moveTo(0f, 0f) }
        square.addRect(Rect.fromLTRB(0f, 9f, 5f, 14f))
        square.addRect(Rect.fromLTRB(1f, 10f, 4f, 13f))

        val paint = Paint(
            color = Color.fromRGBA(70f / 255f, 70f / 255f, 70f / 255f, 1f),
            antiAlias = true,
        )

        drawEllipses(canvas, square, paint)
        canvas.translate(0.5f, 16f)
        drawEllipses(canvas, square, paint)
    }

    private fun drawEllipses(canvas: GmCanvas, square: Path, paint: Paint) {
        canvas.save()
        canvas.scale(16f / 24f, 16f / 24f)
        canvas.drawPath(square, paint)
        canvas.translate(10f, 0f)
        canvas.drawPath(square, paint)
        canvas.translate(10f, 0f)
        canvas.drawPath(square, paint)
        canvas.restore()
    }
}
