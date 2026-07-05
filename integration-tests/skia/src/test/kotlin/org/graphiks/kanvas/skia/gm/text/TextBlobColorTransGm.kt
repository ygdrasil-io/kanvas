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
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import kotlin.math.floor

/**
 * Port of Skia's `gm/textblobcolortrans.cpp::TextBlobColorTrans` (675 × 1600).
 * Builds a 2-run blob (large 256pt "AB" + small 28pt pangram) and draws it
 * repeatedly down the canvas cycling 4 colors.
 * @see https://github.com/google/skia/blob/main/gm/textblobcolortrans.cpp
 */
class TextBlobColorTransGm : SkiaGm {
    override val name = "textblobcolortrans"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 675
    override val height = 1600

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val font256 = Font(typeface, size = 256f)
        val font28 = Font(typeface, size = 28f)

        val text256 = "AB"
        val text28 = "The quick brown fox jumps over the lazy dog."

        val blob256 = font256.toTextBlob(text256, 0f, 0f)
        val blob28 = font28.toTextBlob(text28, 0f, 0f)

        val yOffset256 = font256.size * 1.2f
        val yOffset28 = font28.size * 1.2f

        val allRuns = mutableListOf<KanvasGlyphRun>()
        allRuns.addAll(blob256.glyphRuns)
        // Offset the 28pt run below the 256pt run
        val offsetRuns = blob28.glyphRuns.map { run ->
            KanvasGlyphRun(
                glyphs = run.glyphs,
                positions = run.positions.map { Point(it.x, it.y + yOffset256 - 30f) },
                fontSize = 28f,
            )
        }
        allRuns.addAll(offsetRuns)

        val blob = TextBlob(
            glyphRuns = allRuns,
            typeface = typeface,
            fontSize = 256f,
        )

        val boundsHeight = yOffset256 + yOffset28 - 30f

        canvas.drawColor(r = 0.5f, g = 0.5f, b = 0.5f)
        canvas.translate(10f, 40f)

        val colors = listOf(
            Color.fromRGBA(0f, 1f, 1f, 1f),  // CYAN
            Color.fromRGBA(0.75f, 0.75f, 0.75f, 1f), // LTGRAY
            Color.fromRGBA(1f, 1f, 0f, 1f),  // YELLOW
            Color.WHITE,
        )
        var colorIndex = 0
        var y = 0
        while (y + floor(boundsHeight).toInt() < height) {
            val paint = Paint(color = colors[colorIndex++ % colors.size])
            canvas.save()
            canvas.translate(0f, y.toFloat())
            canvas.drawTextBlob(blob, 0f, 0f, paint)
            canvas.restore()
            y += floor(boundsHeight).toInt()
        }
    }
}
