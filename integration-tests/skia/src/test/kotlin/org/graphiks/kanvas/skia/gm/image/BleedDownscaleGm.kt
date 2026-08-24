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

/**
 * Port of Skia's `gm/bleed.cpp::bleed_downscale`.
 * Tests downscaling with image rect constraints.
 * @see https://github.com/google/skia/blob/main/gm/bleed.cpp
 */
class BleedDownscaleGm : SkiaGm {
    override val name = "bleed_downscale"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 360
    override val height = 240

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val (img, src) = makeImage()

        canvas.translate(10f, 10f)

        for (row in 0 until 2) {
            canvas.save()
            for (col in 0 until 3) {
                val surf = Surface(1, 1)
                surf.canvas {
                    drawImageRect(img, src, RectF32(0f, 0f, 1f, 1f))
                }
                val snap = surf.makeImageSnapshot()
                canvas.drawImage(
                    snap,
                    RectF32(0f, 0f, snap.width.toFloat(), snap.height.toFloat()),
                )
                canvas.translate(120f, 0f)
            }
            canvas.restore()
            canvas.translate(0f, 120f)
        }
    }

    private fun makeImage(): Pair<Image, RectF32> {
        val n = 10 + 2 + 8 + 2 + 10
        val surf = Surface(n, n)
        surf.canvas {
            drawRect(RectF32(0f, 0f, n.toFloat(), n.toFloat()), Paint(color = ColorARGB.Red))
            val inner = RectF32.ofLTRB(10f, 10f, (n - 10).toFloat(), (n - 10).toFloat())
            drawRect(inner, Paint(color = ColorARGB.Blue))
        }
        val image = surf.makeImageSnapshot()
        val srcRect = RectF32.ofLTRB(12f, 12f, (n - 12).toFloat(), (n - 12).toFloat())
        return Pair(image, srcRect)
    }
}
