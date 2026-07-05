package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.EmojiTypeface
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/coloremoji_blendmodes.cpp`.
 * Draws a colour-emoji glyph through all 29 SkBlendModes
 * in a 5-column grid with a solid background stripe per cell.
 * @see https://github.com/google/skia/blob/main/gm/coloremoji_blendmodes.cpp
 */
class ColoremojiBlendmodesGm : SkiaGm {
    override val name = "coloremoji_blendmodes_colrv0"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 400
    override val height = 640

    private val emojiTypeface = EmojiTypeface.createOrFallback(
        EmojiTypeface.Format.COLRv0, ByteArray(0),
    )

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0.5f, 0.5f, 0.5f)

        val allBlendModes = BlendMode.entries.toList()
        val cols = 5
        val cellSize = 60f
        val startX = 10f
        val startY = 20f

        val text = "\uD83D\uDE00" // 😀
        val font = Font(emojiTypeface, size = 45f, antiAlias = false, subpixel = true)

        for ((idx, mode) in allBlendModes.withIndex()) {
            val row = idx / cols
            val col = idx % cols
            val x = startX + col * cellSize
            val y = startY + row * cellSize

            canvas.drawRect(Rect(x, y, x + cellSize - 4f, y + cellSize - 4f), Paint(color = Color.RED))

            val paint = Paint(color = Color.fromRGBA(0f, 1f, 0f, 1f), blendMode = mode)
            canvas.drawSimpleText(text, x + 5f, y + 40f, font, paint)
        }
    }
}
