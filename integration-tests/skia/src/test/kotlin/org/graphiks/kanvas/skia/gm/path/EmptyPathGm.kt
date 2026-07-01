package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import kotlin.random.Random

/**
 * Port of Skia's `gm/emptypath.cpp`.
 * Draws empty paths under different fill rules and paint styles.
 * Inverse-fill empty paths should fill the clip rect.
 * @see https://github.com/google/skia/blob/main/gm/emptypath.cpp
 */
class EmptyPathGm : SkiaGm {
    override val name = "emptypath"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 600
    override val height = 280

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val rand = Random(0)
        val rect = Rect(0f, 0f, 100f, 30f)
        val borderPaint = Paint(
            color = Color.fromRGBA(0f, 0f, 0f, 1f),
            style = PaintStyle.STROKE,
            strokeWidth = 1f,
        )
        var index = 0

        for (style in styles) {
            for (fill in fills) {
                val col = index % 4
                val row = index / 4
                val x = 10f + col * 140f
                val y = row * 70f

                val raw = rand.nextInt()
                val colorInt = raw or (0xFF000000.toInt())
                val a = ((colorInt ushr 24) and 0xFF) / 255f
                val r = ((colorInt ushr 16) and 0xFF) / 255f
                val g = ((colorInt ushr 8) and 0xFF) / 255f
                val b = (colorInt and 0xFF) / 255f

                val path = Path { }.also { it.fillType = fill }
                val paint = Paint(color = Color.fromRGBA(r, g, b, a), style = style)

                canvas.save()
                canvas.translate(x, y)
                canvas.save()
                canvas.clipRect(rect)
                canvas.drawPath(path, paint)
                canvas.restore()
                canvas.drawRect(rect, borderPaint)
                canvas.restore()

                index++
            }
        }
    }

    private companion object {
        val fills = listOf(
            FillType.WINDING,
            FillType.EVEN_ODD,
            FillType.INVERSE_WINDING,
            FillType.INVERSE_EVEN_ODD,
        )
        val styles = listOf(
            PaintStyle.FILL,
            PaintStyle.STROKE,
            PaintStyle.STROKE,
        )
    }
}
