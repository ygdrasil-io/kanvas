package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

class ImageSource2Gm : SkiaGm {
    override val name = "imagesource2"
    override val renderFamily = RenderFamily.IMAGE
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f, 1f)

        val colors = listOf(
            Color.RED, Color.fromRGBA(0.5f, 0.5f, 0.5f, 1f),
            Color.GREEN, Color.fromRGBA(0.5f, 0.5f, 0.5f, 1f),
            Color.BLUE, Color.fromRGBA(0.5f, 0.5f, 0.5f, 1f),
            Color.fromRGBA(0f, 1f, 1f, 1f), Color.fromRGBA(0.5f, 0.5f, 0.5f, 1f),
            Color.fromRGBA(1f, 0f, 1f, 1f), Color.fromRGBA(0.5f, 0.5f, 0.5f, 1f),
            Color.fromRGBA(1f, 1f, 0f, 1f), Color.fromRGBA(0.5f, 0.5f, 0.5f, 1f),
            Color.WHITE, Color.fromRGBA(0.5f, 0.5f, 0.5f, 1f),
        )

        val surface = Surface(kImageSize, kImageSize)
        surface.canvas {
            var curColor = 0
            var x = 0
            while (x < kImageSize) {
                drawRect(Rect.fromXYWH(x.toFloat(), 0f, 3f, kImageSize.toFloat()),
                    Paint(color = colors[curColor]))
                curColor = (curColor + 1) % colors.size
                x += 3
            }
        }
        val image = surface.makeImageSnapshot()

        val srcRect = Rect.fromLTRB(0f, 0f, kImageSize.toFloat(), kImageSize.toFloat())
        val dstRect = Rect.fromLTRB(0.75f, 0.75f, 225.75f, 225.75f)
        canvas.drawImageRect(image, srcRect, dstRect)
    }

    private companion object {
        const val kImageSize: Int = 503
    }
}
