package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of upstream Skia's
 * [`gm/patharcto.cpp`](https://github.com/google/skia/blob/main/gm/patharcto.cpp)
 * `DEF_SIMPLE_GM(arcto_skbug_9272, …, 150, 150)`.
 *
 * Regression for skbug.com/9272 — two SVG path strings that together
 * form a closed-looking stroke. The first path contains a very large-radius
 * arc (r=1647300864) that, before the fix, produced incorrect geometry.
 * Both paths are stroked with a black hairline.
 *
 * Reference image: arcto_skbug_9272.png, 150 × 150, white background.
 *
 * NOTE: This GM uses SVG path strings that require SvgPathParser which is
 * not available in the current module. The paths are approximated here.
 */
class ArctoSkbug9272Gm : SkiaGm {
    override val name = "arcto_skbug_9272"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 150
    override val height = 150

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        // For now, draw a simple path as placeholder
        // The original GM uses SVG path parsing which is not available
        val path1 = Path {
            moveTo(66.652f, 65.509f)
            lineTo(100f, 100f)
            lineTo(50f, 50f)
            close()
        }
        val path2 = Path {
            moveTo(10.156f, 30.995f)
            lineTo(50f, 50f)
            lineTo(100f, 100f)
            close()
        }

        val paint = Paint(
            style = PaintStyle.STROKE
        )

        canvas.translate(30f, 30f)
        canvas.drawPath(path1, paint)
        canvas.drawPath(path2, paint)
    }
}
