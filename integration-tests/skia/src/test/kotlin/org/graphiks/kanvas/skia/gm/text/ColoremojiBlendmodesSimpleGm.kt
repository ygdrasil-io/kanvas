package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color

/** Tests color emoji blend modes with three overlapping semi-transparent circles (RGB). */
class ColoremojiBlendmodesSimpleGm : SkiaGm {
    override val name = "coloremoji_blendmodes"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 300
    override val height = 300

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0.9f, 0.9f, 0.9f)
        canvas.drawCircle(100f, 100f, 80f, Paint(color = Color.fromRGBA(1f, 0f, 0f, 0.5f)))
        canvas.drawCircle(200f, 100f, 80f, Paint(color = Color.fromRGBA(0f, 0f, 1f, 0.5f)))
        canvas.drawCircle(150f, 200f, 80f, Paint(color = Color.fromRGBA(0f, 1f, 0f, 0.5f)))
    }
}
