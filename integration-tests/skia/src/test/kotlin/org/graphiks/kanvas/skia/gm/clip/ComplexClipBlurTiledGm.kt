package org.graphiks.kanvas.skia.gm.clip

import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.CornerRadii
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/complexclip_blur_tiled.cpp::ComplexClipBlurTiledGM`.
 * @see https://github.com/google/skia/blob/main/gm/complexclip_blur_tiled.cpp
 */
class ComplexClipBlurTiledGm : SkiaGm {
    override val name = "complexclip_blur_tiled"
    override val renderFamily = RenderFamily.CLIP
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 512

    private val tileSize = 128f

    override fun draw(canvas: GmCanvas, width0: Int, height0: Int) {
        canvas.drawRect(Rect.fromLTRB(0f, 0f, 512f, 512f), Paint())

        val blurPaint = Paint(imageFilter = ImageFilter.Blur(5f, 5f))
        var y = 0f
        while (y < height) {
            var x = 0f
            while (x < width) {
                canvas.save()
                canvas.translate(-x, -y)
                val rect = Rect.fromLTRB(0f, 0f, 512f, 512f)
                canvas.saveLayer(rect, blurPaint)
                val inset = Rect(rect.left + 20f, rect.top + 20f, rect.right - 20f, rect.bottom - 20f)
                val rrect = RRect(inset, CornerRadii(25f, 25f))
                canvas.clipRRect(rrect, ClipOp.DIFFERENCE, true)
                canvas.drawRect(rect, Paint())
                canvas.restore()
                canvas.restore()
                x += tileSize
            }
            y += tileSize
        }
    }
}
