package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/bitmaprect.cpp::DrawBitmapRect4` (int src rects variant).
 * 4096x4096 bitmap with border, drawn with XOR blending at 50% alpha.
 * Uses IRect-derived source rects (mirrors `fUseIRect` in C++).
 * @see https://github.com/google/skia/blob/main/gm/bitmaprect.cpp
 */
class BigBitmapRectIGm : SkiaGm {
    override val name = "bigbitmaprect_i"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 480

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val img = makeBigBitmap()
        val paint = Paint(blendMode = BlendMode.XOR, color = Color.fromRGBA(1f, 1f, 1f, 0.5f))

        val srcR1 = Rect.fromLTRB(0.0f, 0.0f, 4096.0f, 2040.0f)
        val dstR1 = Rect.fromLTRB(10.1f, 10.1f, 629.9f, 400.9f)
        val srcR2 = Rect.fromLTRB(4085.0f, 10.0f, 4087.0f, 12.0f)
        val dstR2 = Rect.fromLTRB(10f, 410f, 30f, 430f)

        canvas.drawImageRect(img, Rect.fromXYWH(0f, 0f, srcR1.width, srcR1.height), dstR1, paint)
        canvas.drawImageRect(img, Rect.fromXYWH(srcR2.left, srcR2.top, srcR2.width, srcR2.height), dstR2, paint)
    }
}
