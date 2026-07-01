package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/rrects.cpp` — `RRectGM(kAA_Draw_Type)`.
 * GM name: `rrect_draw_aa`. Renders 43 rrects with anti-aliased fill,
 * tiled in 80 x 40 px cells on a 640 x 480 image. Each rrect is drawn
 * via [Path.addRRect].
 */
/**
 * Port of Skia's `gm/rrects.cpp` (RRectGM with AA draw).
 * 43 anti-aliased rrects across a tiled grid.
 * @see https://github.com/google/skia/blob/main/gm/rrects.cpp
 */
class RRectDrawAaGm : SkiaGm {
    override val name = "rrect_draw_aa"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = kImageWidth
    override val height = kImageHeight

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0xDD / 255f, 0xDD / 255f, 0xDD / 255f, 1f)
        val rrects = buildRRects()
        val paint = Paint(antiAlias = true)

        var x = 1
        var y = 1
        for (idx in 0 until kNumRRects) {
            canvas.save()
            canvas.translate(x.toFloat(), y.toFloat())
            if (idx == kNumRRects - 1) {
                val path = Path { }.apply { addRRect(rrects[idx]) }
                canvas.drawPath(path, paint)
            } else {
                val path = Path { }.apply { addRRect(rrects[idx]) }
                canvas.drawPath(path, paint)
            }
            canvas.restore()
            x += kTileX
            if (x > kImageWidth) {
                x = 1
                y += kTileY
            }
        }
    }
}
