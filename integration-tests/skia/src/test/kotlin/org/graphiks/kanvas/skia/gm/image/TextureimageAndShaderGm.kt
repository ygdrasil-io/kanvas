package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of `DEF_SIMPLE_GM(textureimage_and_shader, canvas, 100, 50)` from
 * `gm/image_shader.cpp`.
 * Tests drawImage and image-shader paths.
 * @see https://github.com/google/skia/blob/main/gm/image_shader.cpp
 */
class TextureimageAndShaderGm : SkiaGm {
    override val name = "textureimage_and_shader"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 100
    override val height = 50

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val greenImage = makeGreenImage(50, 50)

        // Left half: drawImage path
        canvas.drawRect(Rect.fromXYWH(0f, 0f, 50f, 50f), Paint(color = Color.RED))
        canvas.drawImage(greenImage, Rect.fromXYWH(0f, 0f, 50f, 50f))

        // Right half: image-shader path
        canvas.drawRect(Rect.fromXYWH(50f, 0f, 50f, 50f), Paint(color = Color.RED))
        val shader = greenImage.makeShader()
        canvas.drawRect(Rect.fromXYWH(50f, 0f, 50f, 50f), Paint(shader = shader))
    }

    private fun makeGreenImage(w: Int, h: Int): Image {
        val pixels = ByteArray(w * h * 4)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = (y * w + x) * 4
                pixels[i] = 0x00.toByte(); pixels[i + 1] = 0xFF.toByte()
                pixels[i + 2] = 0x00.toByte(); pixels[i + 3] = 0xFF.toByte()
            }
        }
        return Image.fromPixels(w, h, pixels, sourceId = "green")
    }
}
