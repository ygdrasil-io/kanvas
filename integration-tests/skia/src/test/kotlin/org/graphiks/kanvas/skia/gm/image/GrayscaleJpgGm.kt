package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

class GrayscaleJpgGm : SkiaGm {
    override val name = "grayscalejpg"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 128
    override val height = 128

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val bytes = loadResource("images/grayscale.jpg") ?: return
        val image = Image.decode(bytes)
        if (image.width == 0) return
        canvas.drawImage(image, Rect(0f, 0f, image.width.toFloat(), image.height.toFloat()))
    }

    private fun loadResource(path: String): ByteArray? =
        this::class.java.classLoader?.getResourceAsStream(path)?.readBytes()
}
