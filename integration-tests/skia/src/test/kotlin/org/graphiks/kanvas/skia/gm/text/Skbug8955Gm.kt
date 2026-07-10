package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Point

/**
 * Port of Skia's `gm/skbug_8955.cpp` (100 × 100).
 * Regression test: drawing the same TextBlob first under scale(0,0)
 * then at identity should render on the second call.
 * @see https://github.com/google/skia/blob/main/gm/skbug_8955.cpp
 */
class Skbug8955Gm : SkiaGm {
    override val name = "skbug_8955"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 100
    override val height = 100

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val font = Font(typeface, size = 50f)

        val gid = typeface.glyphIdForCodepoint('+'.code).toUShort()
        val blob = TextBlob(
            glyphRuns = listOf(KanvasGlyphRun(listOf(gid), listOf(Point(0f, 0f)))),
            typeface = typeface,
            fontSize = font.size,
        )

        val paint = Paint()
        canvas.save()
        canvas.scale(0f, 0f)
        canvas.drawTextBlob(blob, 30f, 60f, paint)
        canvas.restore()
        canvas.drawTextBlob(blob, 30f, 60f, paint)
    }
}
