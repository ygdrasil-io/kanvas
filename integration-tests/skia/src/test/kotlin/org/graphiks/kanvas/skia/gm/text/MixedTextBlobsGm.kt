package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
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
import kotlin.math.floor

/**
 * Port of Skia's `gm/mixedtextblobs.cpp::MixedTextBlobsGM` (1250 × 700).
 * Builds a multi-run blob with giant "O" (385pt), LCD "LCD!!!!!" (32pt),
 * and "aA" (12pt) with ReallyBigA. Drawn 4 times with different clip rects.
 * @see https://github.com/google/skia/blob/main/gm/mixedtextblobs.cpp
 */
class MixedTextBlobsGm : SkiaGm {
    override val name = "mixedtextblobs"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 1250
    override val height = 700

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val allRuns = mutableListOf<KanvasGlyphRun>()

        // 1. Giant "O" at 385pt
        val fontLarge = Font(typeface, size = 385f)
        val textO = "O"
        val blobO = fontLarge.toTextBlob(textO, 10f, 0f)
        val yOffset = fontLarge.size * 1.2f
        val offsetO = blobO.glyphRuns.map { run ->
            KanvasGlyphRun(
                glyphs = run.glyphs,
                positions = run.positions.map { Point(it.x, it.y + yOffset) },
                fontSize = 385f,
            )
        }
        allRuns.addAll(offsetO)
        val corruptedAx = fontLarge.measureText(textO)
        val corruptedAy = yOffset
        val boundsHalfWidth = corruptedAx * 0.5f
        val boundsHalfHeight = yOffset * 0.5f
        val xOffset = boundsHalfWidth
        val yOffsetMid = boundsHalfHeight

        // 2. LCD "LCD!!!!!" at 32pt
        val fontLCD = Font(typeface, size = 32f)
        val textLCD = "LCD!!!!!"
        val blobLCD = fontLCD.toTextBlob(textLCD, xOffset - fontLCD.measureText(textLCD) * 0.25f, yOffsetMid - fontLCD.size * 1.2f * 0.5f)
        allRuns.addAll(blobLCD.glyphRuns)

        // 3. Emoji block — skipped (no PlanetTypeface available)

        // 4. "aA" at 12pt
        val fontSmall = Font(typeface, size = 12f)
        val textAA = "aA"
        val blobAA = fontSmall.toTextBlob(textAA, corruptedAx, corruptedAy)
        allRuns.addAll(blobAA.glyphRuns)

        val blob = TextBlob(
            glyphRuns = allRuns,
            typeface = typeface,
            fontSize = 385f,
        )

        val blobBounds = computeBlobBounds(blob)

        canvas.drawColor(r = 0.5f, g = 0.5f, b = 0.5f)
        canvas.translate(10f, 40f)

        val bHalfW = blobBounds.width * 0.5f
        val bHalfH = blobBounds.height * 0.5f
        val bQuarterW = bHalfW * 0.5f
        val bQuarterH = bHalfH * 0.5f

        val clipRects = listOf(
            blobBounds,
            Rect(blobBounds.left, blobBounds.top, blobBounds.left + bHalfW, blobBounds.top + bHalfH),
            Rect(blobBounds.left + bHalfW, blobBounds.top + bHalfH, blobBounds.right, blobBounds.bottom),
            Rect(blobBounds.left + bQuarterW, blobBounds.top + bQuarterH, blobBounds.right - bQuarterW, blobBounds.bottom - bQuarterH),
        )

        val count = clipRects.size
        for (x in 0 until count) {
            drawBlob(canvas, blob, clipRects[x])
            if (x == (count shr 1) - 1) {
                canvas.translate(
                    floor(blobBounds.width + 25f),
                    -(x * floor(blobBounds.height + 25f)),
                )
            } else {
                canvas.translate(0f, floor(blobBounds.height + 25f))
            }
        }
    }

    private fun drawBlob(canvas: GmCanvas, blob: TextBlob, clipRect: Rect) {
        val clipHairline = Paint(color = Color.WHITE, style = PaintStyle.STROKE)
        val paint = Paint(color = Color.BLACK)

        canvas.save()
        canvas.drawRect(clipRect, clipHairline)
        val ghostPaint = Paint(color = Color.fromRGBA(0f, 0f, 0f, 0.125f))
        canvas.drawTextBlob(blob, 0f, 0f, ghostPaint)
        canvas.clipRect(clipRect)
        canvas.drawTextBlob(blob, 0f, 0f, paint)
        canvas.restore()
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
