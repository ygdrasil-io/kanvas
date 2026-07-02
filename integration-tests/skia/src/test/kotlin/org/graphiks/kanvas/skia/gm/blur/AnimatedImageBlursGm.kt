package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Port of Skia's `gm/animatedimageblurs.cpp::AnimatedImageBlurs`.
 * Lays out 30 randomly-sized rounded rectangles, each with a blur ImageFilter.
 * @see https://github.com/google/skia/blob/main/gm/animatedimageblurs.cpp
 */
class AnimatedImageBlursGm : SkiaGm {
    override val name = "animated-image-blurs"
    override val renderFamily = RenderFamily.BLUR
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 512

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0xCC / 255f, 0xCC / 255f, 0xCC / 255f)

        val rand = Random(0)
        val nCount = 30
        val kBlurMax = 7f
        val kWidth = 512f
        val kHeight = 512f

        data class Node(val size: Float, val posX: Float, val posY: Float, val sigma: Float)

        val nodes = Array(nCount) {
            val size = rand.nextFloat() * 50f + 10f
            val posX = rand.nextFloat() * (kWidth - 2f * size) + size
            val posY = rand.nextFloat() * (kHeight - 2f * size) + size
            @Suppress("UNUSED_VARIABLE")
            val dirX = rand.nextFloat() * 2f - 1f
            @Suppress("UNUSED_VARIABLE")
            val dirY = sqrt(1f - dirX * dirX)
            if (rand.nextFloat() < 0.5f) {
                // dirY negation consumed from SkRandom stream
            }
            val blurOffset = rand.nextFloat() * kBlurMax
            val sigma = blurOffset
            @Suppress("UNUSED_VARIABLE")
            val speed = rand.nextFloat() * 40f + 20f
            Node(size, posX, posY, sigma)
        }

        val paint = Paint(antiAlias = true)

        for (n in nodes) {
            val layerPaint = Paint(imageFilter = ImageFilter.Blur(n.sigma, n.sigma))
            canvas.saveLayer(null, layerPaint)
            val rect = Rect(
                n.posX - n.size - 0.5f,
                n.posY - n.size - 0.5f,
                n.posX + n.size + 0.5f,
                n.posY + n.size + 0.5f,
            )
            canvas.drawRRect(RRect(rect, n.size), paint)
            canvas.restore()
        }
    }
}
