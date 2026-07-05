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
 * Port of Skia's `gm/compositor_quads.cpp::CompositorGM("image", ...)`.
 * Simplified port that decodes mandrill and tiles it across a grid.
 * @see https://github.com/google/skia/blob/main/gm/compositor_quads.cpp
 */
class CompositorQuadsImageGm : SkiaGm {
    override val name = "compositor_quads_image"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 955
    override val height = 699

    override fun draw(canvas: GmCanvas, width0: Int, height0: Int) {
        val data = loadResource("images/mandrill_h1v1.jpg") ?: return
        val image = Image.decode(data, "image/jpeg")

        canvas.save()
        canvas.translate(15f + 120f, 15f)
        for (col in 0 until 5) {
            canvas.save()
            canvas.translate(0f, 40f)
            for (row in 0 until 4) {
                val paint = Paint(color = Color(0x80000000u))
                canvas.drawImageRect(
                    image = image,
                    src = Rect.fromLTRB(0f, 0f, image.width.toFloat(), image.height.toFloat()),
                    dst = Rect.fromLTRB(0f, 0f, (3 * 40).toFloat(), (4 * 30).toFloat()),
                    paint = paint,
                )
                canvas.translate(0f, 40f + 4 * 30f)
            }
            canvas.restore()
            canvas.translate((40f + 3 * 40f), 0f)
        }
        canvas.restore()
    }

    private fun loadResource(path: String): ByteArray? =
        this::class.java.classLoader?.getResourceAsStream(path)?.readBytes()
}
