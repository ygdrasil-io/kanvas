package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import kotlin.math.ceil

/**
 * Port of Skia's `gm/textblobtransforms.cpp::TextBlobTransforms` (1000 × 1200).
 * Draws a 3-glyph blob (A-162pt + B-72pt + C-32pt) under translate/rotate/scale CTM.
 * @see https://github.com/google/skia/blob/main/gm/textblobtransforms.cpp
 */
class TextBlobTransformsGm : SkiaGm {
    override val name = "textblobtransforms"
    override val renderFamily = RenderFamily.TEXT
    override val minSimilarity = 0.0
    override val width = 1000
    override val height = 1200

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(r = 0.5f, g = 0.5f, b = 0.5f)

        val fontA = Font(typeface, size = 162f)
        val fontB = Font(typeface, size = 72f)
        val fontC = Font(typeface, size = 32f)

        val textA = "A"
        val textB = "B"
        val textC = "C"

        val blobA = fontA.toTextBlob(textA, 0f, 0f)
        val widthA = fontA.measureText(textA)
        val heightA = fontA.size * 1.2f

        val blobB = fontB.toTextBlob(textB, widthA + 5f, 0f)
        val heightB = fontB.size * 1.2f

        val blobC = fontC.toTextBlob(textC, widthA + 5f, -heightB - 10f)

        // Merge into one blob
        val allRuns = mutableListOf<org.graphiks.kanvas.text.KanvasGlyphRun>()
        allRuns.addAll(blobA.glyphRuns)
        allRuns.addAll(blobB.glyphRuns)
        allRuns.addAll(blobC.glyphRuns)

        val blob = org.graphiks.kanvas.text.TextBlob(
            glyphRuns = allRuns,
            typeface = typeface,
            fontSize = 162f,
        )

        val paint = Paint()

        // Compute approx blob bounds
        val blobW = widthA + 5f + fontB.measureText(textB)
        val blobH = maxOf(heightA, heightB) + 10f
        val xOffset = ceil(blobW)
        val yOffset = ceil(blobH)

        canvas.translate(20f, 20f)

        // First: translates
        canvas.translate(xOffset, 2f * yOffset)
        canvas.drawTextBlob(blob, 0f, 0f, paint)
        canvas.translate(-xOffset, 0f)
        canvas.drawTextBlob(blob, 0f, 0f, paint)
        canvas.translate(2f * xOffset, 0f)
        canvas.drawTextBlob(blob, 0f, 0f, paint)
        canvas.translate(-xOffset, -yOffset)
        canvas.drawTextBlob(blob, 0f, 0f, paint)
        canvas.translate(0f, 2f * yOffset)
        canvas.drawTextBlob(blob, 0f, 0f, paint)

        // Now: rotates
        canvas.translate(4f * xOffset, -yOffset)
        canvas.rotate(180f)
        canvas.drawTextBlob(blob, 0f, 0f, paint)
        canvas.rotate(-180f)
        canvas.translate(0f, -yOffset)
        canvas.rotate(-180f)
        canvas.drawTextBlob(blob, 0f, 0f, paint)
        canvas.rotate(270f)
        canvas.drawTextBlob(blob, 0f, 0f, paint)
        canvas.rotate(-90f)
        canvas.translate(-xOffset, yOffset)
        canvas.rotate(-90f)
        canvas.drawTextBlob(blob, 0f, 0f, paint)
        canvas.rotate(90f)

        // And scales
        canvas.translate(-3f * xOffset, 3f * yOffset)
        canvas.scale(1.5f, 1.5f)
        canvas.drawTextBlob(blob, 0f, 0f, paint)
        canvas.translate(xOffset, 0f)
        canvas.scale(0.25f, 0.25f)
        canvas.drawTextBlob(blob, 0f, 0f, paint)
        canvas.translate(xOffset, 0f)
        canvas.scale(3f, 2f)
        canvas.drawTextBlob(blob, 0f, 0f, paint)

        // Finally: mix of rotate/scale/translate
        canvas.translate(xOffset, 0f)
        canvas.rotate(23f)
        canvas.scale(0.33f, 0.5f)
        canvas.drawTextBlob(blob, 0f, 0f, paint)
        canvas.rotate(-46f)
        canvas.translate(xOffset, 0f)
        canvas.scale(1.2f, 1.1f)
        canvas.drawTextBlob(blob, 0f, 0f, paint)
        canvas.rotate(46f)
        canvas.translate(xOffset, 0f)
        canvas.scale(1.1f, 1.2f)
        canvas.drawTextBlob(blob, 0f, 0f, paint)
        canvas.rotate(46f)
        canvas.translate(xOffset, 0f)
        canvas.scale(0.95f, 1.1f)
        canvas.drawTextBlob(blob, 0f, 0f, paint)
        canvas.rotate(46f)
        canvas.translate(xOffset, 0f)
        canvas.scale(1.3f, 0.7f)
        canvas.drawTextBlob(blob, 0f, 0f, paint)
        canvas.rotate(46f)
        canvas.translate(xOffset, 0f)
        canvas.scale(0.8f, 1.1f)
        canvas.drawTextBlob(blob, 0f, 0f, paint)
        canvas.rotate(10f)
        canvas.translate(xOffset, 0f)
        canvas.scale(1f, 5f)
        canvas.drawTextBlob(blob, 0f, 0f, paint)
        canvas.rotate(5f)
        canvas.translate(xOffset, 0f)
        canvas.scale(5f, 1f)
        canvas.drawTextBlob(blob, 0f, 0f, paint)
    }
}
