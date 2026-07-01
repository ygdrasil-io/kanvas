package org.graphiks.kanvas.skia.gm.path

/**
 * Port of Skia's `gm/strokerectanisotropic5408.cpp`.
 * Tests stroked rect with 10x anisotropic scale (regression for Skia bug 5408).
 * @see https://github.com/google/skia/blob/main/gm/strokerectanisotropic5408.cpp
 */

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

class StrokerectAnisotropic5408Gm : SkiaGm {
    override val name = "strokerect_anisotropic_5408"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 200
    override val height = 50

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(
            style = PaintStyle.STROKE,
            strokeWidth = 6f,
        )
        canvas.scale(10f, 1f)
        canvas.drawRect(Rect.fromXYWH(5f, 20f, 10f, 10f), paint)
    }
}
