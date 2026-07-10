package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/** Simple GM emulating color emoji rendering with a yellow smiley face. */
class ColorEmojiSimpleGm : SkiaGm {
    override val name = "coloremoji"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 400
    override val height = 200

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f)
        val size = 80f
        canvas.drawCircle(width / 2f, height / 2f, size / 2f, Paint(color = Color.fromRGBA(1f, 0.8f, 0f, 1f)))
        canvas.drawCircle(width / 2f - 15f, height / 2f - 10f, 8f, Paint(color = Color.BLACK))
        canvas.drawCircle(width / 2f + 15f, height / 2f - 10f, 8f, Paint(color = Color.BLACK))
        canvas.drawArc(Rect(width / 2f - 20f, height / 2f + 5f, width / 2f + 20f, height / 2f + 30f), 0f, 180f, false, Paint(color = Color.BLACK, strokeWidth = 3f))
    }
}
