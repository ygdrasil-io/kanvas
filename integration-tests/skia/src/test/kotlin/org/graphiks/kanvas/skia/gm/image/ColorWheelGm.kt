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
 * Port of Skia's `gm/colorwheel.cpp::DEF_SIMPLE_GM(colorwheel, canvas, 384, 256)`.
 * @see https://github.com/google/skia/blob/main/gm/colorwheel.cpp
 */
class ColorWheelGm : SkiaGm {
    override val name = "colorwheel"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 384
    override val height = 256

    override fun draw(canvas: GmCanvas, width0: Int, height0: Int) {
        drawCheckerboard(canvas)
        drawImage(canvas, "images/color_wheel.png", 0, 0)
        drawImage(canvas, "images/color_wheel.jpg", 128, 128)
    }

    private fun drawCheckerboard(canvas: GmCanvas) {
        val tileSize = 8
        for (y in 0 until height step tileSize) {
            for (x in 0 until width step tileSize) {
                val dark = ((x / tileSize) + (y / tileSize)) % 2 == 0
                val gray = if (dark) 0x60 else 0x99
                val c = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
                canvas.drawRect(
                    Rect.fromXYWH(x.toFloat(), y.toFloat(), tileSize.toFloat(), tileSize.toFloat()),
                    Paint(color = Color(c.toUInt())),
                )
            }
        }
    }

    private fun drawImage(canvas: GmCanvas, path: String, x: Int, y: Int) {
        val bytes = loadResource(path) ?: return
        val image = Image.decode(bytes)
        canvas.drawImage(image, Rect.fromXYWH(x.toFloat(), y.toFloat(), image.width.toFloat(), image.height.toFloat()))
    }

    private fun loadResource(path: String): ByteArray? =
        this::class.java.classLoader?.getResourceAsStream(path)?.readBytes()
}
