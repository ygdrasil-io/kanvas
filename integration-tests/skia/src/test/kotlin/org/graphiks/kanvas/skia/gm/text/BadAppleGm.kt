package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color

/**
 * Port of Skia's `gm/fontregen.cpp::BadAppleGM`.
 * @see https://github.com/google/skia/blob/main/gm/fontregen.cpp
 */
class BadAppleGm : SkiaGm {
    override val name = "badapple"
    override val renderFamily = RenderFamily.TEXT
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 512

    override fun draw(canvas: GmCanvas, width0: Int, height0: Int) {
        val font = Font(
            typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!,
            size = 256f,
        )
        val paint = Paint(color = Color(0xFF111111u))
        canvas.drawString("Meet", 10f, 260f, font, paint)
        canvas.drawString("iPad Pro", 10f, 500f, font, paint)
    }
}
