package org.graphiks.kanvas.skia.gm.clip

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/crbug_892988.cpp` (256 x 256).
 * Reproduces a clipping bug: AA stroke at half-pixel boundaries followed
 * by clipRect and drawRect with kSrc blend mode.
 * @see https://github.com/google/skia/blob/main/gm/crbug_892988.cpp
 */
class Crbug892988Gm : SkiaGm {
    override val name = "crbug_892988"
    override val renderFamily = RenderFamily.CLIP
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint1 = Paint(
            style = PaintStyle.STROKE,
            strokeWidth = 1f,
            antiAlias = true,
        )
        canvas.drawRect(Rect.fromLTRB(11.5f, 0.5f, 245.5f, 245.5f), paint1)

        canvas.clipRect(Rect.fromLTRB(12f, 1f, 244f, 244f))
        val paint2 = Paint(
            color = Color.fromRGBA(240f / 255f, 1f, 1f),
            blendMode = BlendMode.SRC,
            antiAlias = true,
        )
        canvas.drawRect(Rect.fromLTRB(12f, 1f, 244f, 244f), paint2)
    }
}
