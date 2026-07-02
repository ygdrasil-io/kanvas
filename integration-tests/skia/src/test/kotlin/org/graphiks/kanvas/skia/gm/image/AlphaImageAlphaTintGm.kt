package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

class AlphaImageAlphaTintGm : SkiaGm {
    override val name = "alpha_image_alpha_tint"
    override val renderFamily = RenderFamily.IMAGE
    override val minSimilarity = 0.0
    override val width = 152
    override val height = 80

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0x80 / 255f, 0x80 / 255f, 0x80 / 255f)

        val pixels = ByteArray(64 * 64)
        for (y in 0 until 64) {
            for (x in 0 until 64) {
                pixels[y * 64 + x] = (y * 4).toByte()
            }
        }
        val image = Image.fromPixels(64, 64, pixels, ColorType.ALPHA_8, "alpha_image")

        val tint = Color.fromRGBA(0f, 1f, 0f, 0.5f)
        val paint = Paint(color = tint)

        canvas.translate(8f, 8f)
        canvas.drawImage(image, Rect(0f, 0f, 64f, 64f), paint)

        canvas.translate(72f, 0f)
        val shaderPaint = Paint(color = tint, shader = Shader.Image(image))
        canvas.drawRect(Rect(0f, 0f, 64f, 64f), shaderPaint)
    }
}
