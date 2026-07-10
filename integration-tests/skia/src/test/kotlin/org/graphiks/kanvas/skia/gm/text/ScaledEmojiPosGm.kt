package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.EmojiTypeface
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point

/**
 * Port of Skia's `gm/scaledemoji_pos.cpp`.
 * Verifies that per-glyph-positioned emoji rendering (drawGlyphs)
 * agrees with the auto-advance path (drawSimpleText).
 * @see https://github.com/google/skia/blob/main/gm/scaledemoji_pos.cpp
 */
class ScaledEmojiPosGm : SkiaGm {
    override val name = "scaledemojipos_colrv0"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 1200
    override val height = 1200

    private val emojiTypeface = EmojiTypeface.createOrFallback(
        EmojiTypeface.Format.COLRv0, ByteArray(0),
    )

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0.5f, 0.5f, 0.5f)

        val text = "\uD83D\uDE00" // 😀
        val font = Font(emojiTypeface, size = 200f, antiAlias = false, subpixel = true)

        // Get glyph positions from auto-advance
        val blob = font.toTextBlob(text, 50f, 250f)
        val run = blob.glyphRuns.firstOrNull() ?: return
        val glyphIds = run.glyphs.map { it.toInt() }
        val positions = run.positions

        // Draw via auto-advance (upper row — reference, in green)
        canvas.drawSimpleText(text, 50f, 250f, font, Paint(color = Color.GREEN))

        // Draw via per-glyph-pos (lower row, in red, shifted down)
        val shiftedPositions = positions.map { Point(it.x, it.y + 350f) }
        canvas.drawGlyphs(glyphIds, shiftedPositions, font, Paint(color = Color.RED))
    }
}
