package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.pipeline.BlurStyle
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
import kotlin.math.abs
import kotlin.math.sin

/**
 * Port of Skia's `gm/textblobmixedsizes.cpp::TextBlobMixedSizes` (2100 × 1900).
 * Builds a single blob with runs at sizes 262→162→72→32→14→0 pt and draws it
 * 4 times with random rotations and blurred shadow pass.
 * @see https://github.com/google/skia/blob/main/gm/textblobmixedsizes.cpp
 */
class TextBlobMixedSizesGm : SkiaGm {
    override val name = "textblobmixedsizes"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 85.0
    override val width = 2100
    override val height = 1900

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val text = "Skia"
        val sizes = floatArrayOf(262f, 162f, 72f, 32f, 14f, 0f)

        val allRuns = mutableListOf<KanvasGlyphRun>()
        var yOffset = 0f
        val font = Font(typeface, size = 262f)

        for (size in sizes) {
            val f = font.copy(size = size)
            val blob = f.toTextBlob(text, 0f, yOffset)
            allRuns.addAll(blob.glyphRuns)
            yOffset += if (size > 0f) size * 1.2f else font.size * 0.5f
        }

        val blob = TextBlob(
            glyphRuns = allRuns,
            typeface = typeface,
            fontSize = 262f,
        )

        val blobBounds = computeBlobBounds(blob)

        canvas.drawColor(r = 1f, g = 1f, b = 1f)

        val kPadX = (blobBounds.width / 3f).toInt()
        val kPadY = (blobBounds.height / 3f).toInt()

        var rowCount = 0
        canvas.translate(kPadX.toFloat(), kPadY.toFloat())
        canvas.save()

        val paint = Paint(antiAlias = false, color = Color.BLACK)
        val kSigma = 24f  // ConvertRadiusToSigma(8) for blur
        val blurPaint = Paint(
            color = Color.BLACK,
            maskFilter = MaskFilter.Blur(style = BlurStyle.NORMAL, sigma = kSigma),
        )

        // Deterministic "random" rotations using a simple LCG
        val randomValues = floatArrayOf(12.34f, 28.76f, 5.01f, 39.99f)

        for (i in 0 until 4) {
            canvas.save()
            val angle = if (i % 2 == 0) randomValues[i] else -randomValues[i]
            canvas.rotate(angle)

            canvas.drawTextBlob(blob, 0f, 0f, blurPaint)
            canvas.drawTextBlob(blob, 0f, 0f, paint)

            canvas.restore()
            canvas.translate(blobBounds.width + kPadX.toFloat(), 0f)
            ++rowCount
            if ((blobBounds.width + 2 * kPadX) * rowCount > width) {
                canvas.restore()
                canvas.translate(0f, blobBounds.height + kPadY.toFloat())
                canvas.save()
                rowCount = 0
            }
        }
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
