package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/crbug_478659067.cpp`.
 * Tests skewed text rendering with varying skew factors on a large canvas.
 * @see https://github.com/google/skia/blob/main/gm/crbug_478659067.cpp
 */
class Crbug478659067Gm : SkiaGm {
    override val name = "crbug_478659067"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 1024
    override val height = 1280

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val dfText = "TheQuickBrownFoxJumpsOverTheLazyDog_0123456789"
        val dfSize = 162f

        val baseFont = Font(
            typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!,
            size = dfSize - 64f,
        )

        val paint = Paint(color = Color.BLACK)

        val surface = Surface(width, height)
        surface.canvas {
            clear(Color.fromRGBA(1f, 1f, 1f, 1f))

            val lineSpacing = dfSize
            for (i in 1..6) {
                save()
                translate(10f, (i * lineSpacing) - 10f)
                skew(0.2f * i, 0f)
                drawString(dfText, 0f, 0f, baseFont, paint)
                restore()
            }
        }

        canvas.drawImage(surface.makeImageSnapshot(), Rect.fromLTRB(0f, 0f, width.toFloat(), height.toFloat()))
    }
}
