package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/copy_to_4444.cpp :: format4444`.
 *
 * Verifies ARGB_4444 pixel storage via four 16×16 blocks (the canvas is
 * pre-scaled 16×):
 *
 *  - (0,0) — solid RED via [Bitmap.eraseColor].
 *  - (1,1) — solid BLUE via [Bitmap.eraseColor].
 *  - (2,2) — RED via [Bitmap.setPixel].
 *  - (3,3) — BLUE via [Bitmap.setPixel].
 * @see https://github.com/google/skia/blob/main/gm/copy_to_4444.cpp
 */
class Format4444Gm : SkiaGm {
    override val name = "format4444"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val tolerance = 8
    override val width = 64
    override val height = 64

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.scale(16f, 16f)

        val bm = Bitmap(1, 1, ColorType.ARGB_4444)

        bm.eraseColor(Color.RED)
        canvas.drawImage(bm.toImage(), Rect(0f, 0f, 1f, 1f))

        bm.eraseColor(Color.BLUE)
        canvas.drawImage(bm.toImage(), Rect(1f, 1f, 2f, 2f))

        bm.setPixel(0, 0, Color.RED)
        canvas.drawImage(bm.toImage(), Rect(2f, 2f, 3f, 3f))

        bm.setPixel(0, 0, Color.fromRGBA(0f, 0f, 1f, 1f))
        canvas.drawImage(bm.toImage(), Rect(3f, 3f, 4f, 4f))
    }
}
