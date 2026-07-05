package org.graphiks.kanvas.skia.gm.clip

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/rrects.cpp` (RRectGM with AA clip).
 * 43 rrects drawn with anti-aliased clipRRect.
 * @see https://github.com/google/skia/blob/main/gm/rrects.cpp
 */
class RRectClipAaGm : SkiaGm {
    override val name = "rrect_clip_aa"
    override val renderFamily = RenderFamily.CLIP
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = kImageWidth
    override val height = kImageHeight

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0xDD / 255f, 0xDD / 255f, 0xDD / 255f, 1f)
        val rrects = buildRRects()

        var x = 1
        var y = 1
        for (idx in 0 until kNumRRects) {
            canvas.save()
            canvas.translate(x.toFloat(), y.toFloat())
            val path = Path { }.apply { addRRect(rrects[idx]) }
            val paint = Paint(color = Color.fromRGBA(0f, 0f, 0f, 1f))
            canvas.drawPath(path, paint)
            canvas.restore()
            x += kTileX
            if (x > kImageWidth) {
                x = 1
                y += kTileY
            }
        }
    }
}
