package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/pathfill.cpp` — `DEF_SIMPLE_GM(path_stroke_clip_crbug1070835, ...)`.
 * Draws a stroked cubic path on an offscreen surface with a scale transform,
 * then blits the surface result to the main canvas. Regression test for
 * stroke/clip interaction bugs.
 * @see https://github.com/google/skia/blob/main/gm/pathfill.cpp
 */
class PathStrokeClipCrbug1070835Gm : SkiaGm {
    override val name = "path_stroke_clip_crbug1070835"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 80.0
    override val width = 25
    override val height = 50

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val surf = Surface(25, 25)

        val paint = Paint(
            color = Color.RED,
            antiAlias = true,
            style = PaintStyle.STROKE,
            strokeWidth = 2f,
        )

        surf.canvas {
            scale(4.16666651f / 2f, 4.16666651f / 2f)
            drawPath(Path {
                moveTo(11f, 12f)
                cubicTo(11f, 18.0751324f, 6.07513189f, 23f, -4.80825292E-7f, 23f)
                cubicTo(-6.07513332f, 23f, -11f, 18.0751324f, -11f, 11.999999f)
                cubicTo(-10.999999f, 5.92486763f, -6.07513189f, 1f, 1.31173692E-7f, 1f)
                cubicTo(6.07513141f, 1f, 10.9999981f, 5.92486572f, 11f, 11.9999971f)
            }, paint)
        }

        canvas.drawImage(surf.makeImageSnapshot(), Rect(0f, 0f, 25f, 25f))
    }
}
