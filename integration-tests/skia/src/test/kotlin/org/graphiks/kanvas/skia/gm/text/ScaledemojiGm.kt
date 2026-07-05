package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.EmojiTypeface
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.types.Color

/**
 * Port of Skia's `gm/scaledemoji.cpp`.
 * Draws a colour-emoji glyph at four progressively larger point
 * sizes (70, 180, 270, 340) via drawSimpleText.
 * @see https://github.com/google/skia/blob/main/gm/scaledemoji.cpp
 */
class ScaledemojiGm : SkiaGm {
    override val name = "scaledemoji_colrv0"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 1200
    override val height = 1200

    private val emojiTypeface = EmojiTypeface.createOrFallback(
        EmojiTypeface.Format.COLRv0, ByteArray(0),
    )

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0.5f, 0.5f, 0.5f)

        val text = "\uD83D\uDE00" // 😀
        val textSizes = floatArrayOf(70f, 180f, 270f, 340f)
        var y = 0f

        for (textSize in textSizes) {
            val font = Font(emojiTypeface, size = textSize, antiAlias = false, subpixel = true)
            val metrics = font.getMetrics()
            if (metrics != null) {
                y += -metrics.ascent
                canvas.drawSimpleText(text, 10f, y, font, Paint())
                y += metrics.descent + metrics.leading
            } else {
                y += textSize * 1.2f
                canvas.drawSimpleText(text, 10f, y, font, Paint())
            }
        }
    }
}
