package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's gm/composeshader.cpp (composeshader_bitmap2).
 * Tests SrcIn blend of a color image shader and an alpha-8 mask shader.
 * @see https://github.com/google/skia/blob/main/gm/composeshader.cpp
 */
class ComposeShaderBitmap2Gm : SkiaGm {
    override val name = "composeshader_bitmap2"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.TRIVIAL
    override val minSimilarity = 0.0
    override val width = 200
    override val height = 200

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val w = 255
        val h = 255

        val colorPixels = ByteArray(w * h * 4)
        val maskPixels = ByteArray(w * h)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx4 = (y * w + x) * 4
                colorPixels[idx4] = x.toByte()        // R
                colorPixels[idx4 + 1] = y.toByte()     // G
                colorPixels[idx4 + 2] = 0x00.toByte()  // B
                colorPixels[idx4 + 3] = (-1).toByte()  // A = 0xFF
                val alpha = (y + x) / 2
                maskPixels[y * w + x] = alpha.toByte()
            }
        }

        val colorImage = Image.fromPixels(w, h, colorPixels, ColorType.RGBA_8888)
        val maskImage = Image.fromPixels(w, h, maskPixels, ColorType.ALPHA_8)

        val bgPaint = Paint(color = Color.BLUE)
        val r = Rect(0f, 0f, w.toFloat(), h.toFloat())
        canvas.drawRect(r, bgPaint)

        val srcShader = Shader.Image(colorImage, TileMode.CLAMP, TileMode.CLAMP)
        val maskShader = Shader.Image(maskImage, TileMode.CLAMP, TileMode.CLAMP)
        val blendShader = Shader.Blend(BlendMode.SRC_IN, dst = maskShader, src = srcShader)

        val paint = Paint(shader = blendShader)
        canvas.drawRect(r, paint)
    }
}
