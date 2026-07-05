package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/crbug_918512.cpp::DEF_SIMPLE_GM(crbug_918512, canvas, 256, 256)`.
 *
 * Reduced from a Chromium PDF-backend regression: stacks two nested
 * [saveLayer]s with the inner layer carrying a [BlendMode.DST_IN] blend +
 * [ColorFilter.Luma], then fills the inner layer's left half with grey.
 * @see https://github.com/google/skia/blob/main/gm/crbug_918512.cpp
 */
class Crbug918512Gm : SkiaGm {
    override val name = "crbug_918512"
    override val renderFamily = RenderFamily.COMPOSITE
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width0: Int, height0: Int) {
        canvas.drawColor(1f, 1f, 0f)

        canvas.saveLayer(null, null)
        canvas.drawColor(0f, 1f, 1f)

        val layerPaint = Paint(
            blendMode = BlendMode.DST_IN,
            colorFilter = ColorFilter.Luma,
        )
        canvas.saveLayer(null, layerPaint)

        canvas.drawColor(0f, 0f, 0f, 0f)
        val paint = Paint(color = Color(0xFF808080u))
        canvas.drawRect(Rect.fromLTRB(0f, 0f, 128f, 256f), paint)

        canvas.restore()
        canvas.restore()
    }
}
