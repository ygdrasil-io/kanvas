package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color

/**
 * Port of Skia's `gm/blurtextsmallradii.cpp::blurSmallRadii`.
 * @see https://github.com/google/skia/blob/main/gm/blurtextsmallradii.cpp
 */
class BlurSmallRadiiGm : SkiaGm {
    override val name = "blurSmallRadii"
    override val renderFamily = RenderFamily.TEXT
    override val minSimilarity = 0.0
    override val width = 100
    override val height = 150

    private val font = Font(
        typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!,
        size = 12f,
    )

    override fun draw(canvas: GmCanvas, width0: Int, height0: Int) {
        val sigmas = floatArrayOf(0.25f, 0.5f, 0.75f, 1.0f, 1.5f, 2.5f)
        var y = 10f

        for (sigma in sigmas) {
            val redBlurPaint = Paint(
                color = Color.RED,
                antiAlias = true,
                maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, sigma),
            )
            canvas.drawString("guest", 20f, y, font, redBlurPaint)

            val greenPaint = Paint(color = Color(0xFF00FF00u))
            canvas.drawString("guest", 20f, y, font, greenPaint)
            y += 20f
        }
    }
}
