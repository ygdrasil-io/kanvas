package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/bitmaprect.cpp::DrawBitmapRect4` (float src rects variant).
 * 4096x4096 bitmap with border, drawn with XOR blending at 50% alpha.
 * Uses float source rects (mirrors `!fUseIRect` in C++).
 * @see https://github.com/google/skia/blob/main/gm/bitmaprect.cpp
 */
class BigBitmapRectSGm : SkiaGm {
    override val name = "bigbitmaprect_s"
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

        canvas.drawImageRect(img, srcR1, dstR1, paint)
        canvas.drawImageRect(img, srcR2, dstR2, paint)
    }
}

// Shared bitmap builder extracted for use by both variants
internal fun makeBigBitmap(): Image {
    val gXSize = 4096
    val gYSize = 4096
    val gBorderWidth = 10
    val pixels = ByteArray(gXSize * gYSize * 4)
    for (y in 0 until gYSize) {
        for (x in 0 until gXSize) {
            val i = (y * gXSize + x) * 4
            val isBorder = x <= gBorderWidth || x >= gXSize - gBorderWidth ||
                y <= gBorderWidth || y >= gYSize - gBorderWidth
            val a = 0x88
            val r = if (isBorder) 0xFF else 0xFF
            val g = if (isBorder) 0xFF else 0x00
            val b = if (isBorder) 0xFF else 0x00
            pixels[i] = r.toByte()
            pixels[i + 1] = g.toByte()
            pixels[i + 2] = b.toByte()
            pixels[i + 3] = a.toByte()
        }
    }
    return Image.fromPixels(gXSize, gYSize, pixels, ColorType.RGBA_8888, "bigbitmap")
}
