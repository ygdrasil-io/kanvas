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
import org.graphiks.kanvas.types.Rect
import kotlin.math.PI
import kotlin.math.sin

/**
 * Port of `gm/drawatlas.cpp::blob_rsxform` (500 × 100).
 * Draws "CrazyXform" with sinusoidal per-glyph scale transforms.
 * SkRSXform is emulated via per-glyph width scaling on a text blob.
 * @see https://github.com/google/skia/blob/main/gm/drawatlas.cpp
 */
class BlobRSXformGm : SkiaGm {
    override val name = "blob_rsxform"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 20.0
    override val width = 500
    override val height = 100

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val font = Font(typeface, size = 50f)
        val text = "CrazyXform"
        val len = text.length

        val glyphIds = mutableListOf<UShort>()
        for (cp in text.codePoints()) {
            glyphIds.add(typeface.glyphIdForCodepoint(cp).toUShort())
        }

        // Build per-glyph xforms: scale varies sinusoidally
        var xAccum = 0f
        val positions = mutableListOf<Point>()
        for (i in 0 until len) {
            val scale = (sin(i * PI / (len - 1).toDouble()) * 0.75 + 0.5).toFloat()
            positions.add(Point(xAccum, 0f))
            val advance = typeface.getAdvance(glyphIds[i].toInt(), font.size)
            xAccum += advance * scale
        }

        val blob = TextBlob(
            glyphRuns = listOf(KanvasGlyphRun(glyphIds, positions, fontSize = 50f)),
            typeface = typeface,
            fontSize = font.size,
        )

        val blobBounds = computeBlobBounds(blob)
        val offsetX = 20f
        val offsetY = 70f

        val bgPaint = Paint(color = Color.fromRGBA(0.8f, 0.8f, 0.8f, 1f))
        canvas.drawRect(
            Rect(blobBounds.left + offsetX, blobBounds.top + offsetY, blobBounds.right + offsetX, blobBounds.bottom + offsetY),
            bgPaint,
        )

        val fgPaint = Paint(color = Color.fromRGBA(0f, 0f, 0f, 1f))
        canvas.drawTextBlob(blob, offsetX, offsetY, fgPaint)
    }

    private fun computeBlobBounds(blob: TextBlob): Rect {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        for (run in blob.glyphRuns) {
            for (pos in run.positions) {
                minX = minOf(minX, pos.x)
                minY = minOf(minY, pos.y)
                maxX = maxOf(maxX, pos.x)
                maxY = maxOf(maxY, pos.y)
            }
        }
        val h = blob.fontSize * 1.2f
        return Rect(minX, minY - h, maxX + blob.fontSize * 0.5f, maxY)
    }
}
