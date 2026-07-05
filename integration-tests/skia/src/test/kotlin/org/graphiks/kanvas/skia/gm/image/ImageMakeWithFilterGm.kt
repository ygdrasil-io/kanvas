package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.codec.Codec
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port stub for Skia's `gm/imagemakewithfilter.cpp::ImageMakeWithFilterGM`.
 * 1840x860 GM that exercises SkImages.MakeWithFilter across 13 filter
 * factories. This minimal port renders only the per-cell structural
 * scaffolding (faded mandrill backgrounds) without running MakeWithFilter.
 * A proper port will follow when the missing SkImageFilters factories land.
 * @see https://github.com/google/skia/blob/main/gm/imagemakewithfilter.cpp
 */
class ImageMakeWithFilterGm : SkiaGm {
    override val name = "imagemakewithfilter"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 1840
    override val height = 860

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val bytes = loadResource("images/mandrill_128.png") ?: return
        val src = Image.decode(bytes)

        val surf = Surface(100, 100)
        surf.canvas {
            drawImage(src, Rect(0f, 0f, 100f, 100f))
        }
        val img = surf.makeImageSnapshot()

        val margin = 40f
        val dx = 100f + margin
        val dy = 100f + margin

        canvas.save()
        canvas.translate(margin, margin)
        val alpha = Paint(color = Color.fromRGBA(0f, 0f, 0f, 0.3f))
        for (row in 0 until 6) {
            canvas.save()
            for (col in 0 until 13) {
                canvas.drawImage(img, Rect(0f, 0f, 100f, 100f), alpha)
                canvas.translate(dx, 0f)
            }
            canvas.restore()
            canvas.translate(0f, dy)
        }
        canvas.restore()
    }

    private fun loadResource(path: String): ByteArray? {
        return this::class.java.classLoader?.getResourceAsStream(path)?.readBytes()
    }
}
