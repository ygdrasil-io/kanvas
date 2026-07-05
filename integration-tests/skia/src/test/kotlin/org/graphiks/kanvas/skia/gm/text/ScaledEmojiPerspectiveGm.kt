package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.EmojiTypeface
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33

/**
 * Port of Skia's `gm/scaledemoji_perspective.cpp`.
 * Draws a colour-emoji glyph through a 3x3 perspective matrix.
 * @see https://github.com/google/skia/blob/main/gm/scaledemoji_perspective.cpp
 */
class ScaledEmojiPerspectiveGm : SkiaGm {
    override val name = "scaledemojiperspective_colrv0"
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
        val font = Font(emojiTypeface, size = 300f, antiAlias = false, subpixel = true)

        // Perspective matrix: skew + scale to create depth illusion
        // mirroring upstream's 3x3 perspective transform
        val perspective = Matrix33.makeAll(
            1.5f, 0.3f, 100f,
            0.1f, 1.0f, 150f,
            0.0005f, 0.0002f, 1f,
        )

        canvas.save()
        canvas.concat(perspective)
        canvas.drawSimpleText(text, 200f, 500f, font, Paint(color = Color.fromRGBA(0f, 1f, 1f)))
        canvas.restore()
    }
}
