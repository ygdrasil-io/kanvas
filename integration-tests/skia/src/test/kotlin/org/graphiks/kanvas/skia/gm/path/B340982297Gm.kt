package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color

/**
 * Port of Skia's `gm/strokes.cpp` (DEF_SIMPLE_GM b_340982297).
 * Two AA-filled self-intersecting line polygons.
 * @see https://github.com/google/skia/blob/main/gm/strokes.cpp
 */
class B340982297Gm : SkiaGm {
    override val name = "b_340982297"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 80
    override val height = 50

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawRect(
            Rect(0f, 0f, width.toFloat(), height.toFloat()),
            Paint(color = Color.fromRGBA(1f, 1f, 1f, 1f)),
        )
        val paint = Paint(color = Color.fromRGBA(0f, 0f, 0f, 1f))

        val p1 = Path {
            moveTo(30.23983f, 48.5674667f)
            lineTo(1.30884242f, 45.5222702f)
            lineTo(2.97688866f, 29.6749554f)
            lineTo(17.4423828f, 31.1975555f)
            lineTo(2.94269657f, 30.0452003f)
            lineTo(4.38597536f, 11.8849154f)
            lineTo(33.3853493f, 14.1896257f)
            close()
        }
        canvas.drawPath(p1, paint)

        val p2 = Path {
            moveTo(73.3853455f, 4.18963623f)
            lineTo(69.995636f, 39.1360626f)
            lineTo(42.83145142f, 21.056778f)
            lineTo(42.97689819f, 19.6749573f)
            lineTo(57.4423828f, 21.1975555f)
            lineTo(42.94268799f, 20.0451965f)
            lineTo(44.38595581f, 1.88491821f)
            close()
        }
        canvas.drawPath(p2, paint)
    }
}
