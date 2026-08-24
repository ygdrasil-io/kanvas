package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32

/** Tests image source creation — creates a red surface, snapshots it,
 *  then draws the snapshot as an image at full canvas size.
 */
class ImageSource2Gm : SkiaGm {
    override val name = "imagesource2"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f, 1f)

        val colors = listOf(
            ColorARGB.Red, ColorARGB.fromRGBA(0.5f, 0.5f, 0.5f, 1f),
            ColorARGB.Green, ColorARGB.fromRGBA(0.5f, 0.5f, 0.5f, 1f),
            ColorARGB.Blue, ColorARGB.fromRGBA(0.5f, 0.5f, 0.5f, 1f),
            ColorARGB.fromRGBA(0f, 1f, 1f, 1f), ColorARGB.fromRGBA(0.5f, 0.5f, 0.5f, 1f),
            ColorARGB.fromRGBA(1f, 0f, 1f, 1f), ColorARGB.fromRGBA(0.5f, 0.5f, 0.5f, 1f),
            ColorARGB.fromRGBA(1f, 1f, 0f, 1f), ColorARGB.fromRGBA(0.5f, 0.5f, 0.5f, 1f),
            ColorARGB.White, ColorARGB.fromRGBA(0.5f, 0.5f, 0.5f, 1f),
        )

        val surface = Surface(kImageSize, kImageSize)
        surface.canvas {
            var curColor = 0
            var x = 0
            while (x < kImageSize) {
                drawRect(RectF32.ofOriginSize(x.toFloat(), 0f, 3f, kImageSize.toFloat()),
                    Paint(color = colors[curColor]))
                curColor = (curColor + 1) % colors.size
                x += 3
            }
        }
        val image = surface.makeImageSnapshot()

        val srcRect = RectF32.ofLTRB(0f, 0f, kImageSize.toFloat(), kImageSize.toFloat())
        val dstRect = RectF32.ofLTRB(0.75f, 0.75f, 225.75f, 225.75f)
        canvas.drawImageRect(image, srcRect, dstRect)
    }

    private companion object {
        const val kImageSize: Int = 503
    }
}
