package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/showmiplevels.cpp` `showmiplevels_explicit` (1130 × 970).
 * Loads ship.png, creates explicit mip levels, draws at decreasing scales.
 * @see https://github.com/google/skia/blob/main/gm/showmiplevels.cpp
 */
class ShowMipLevelsGm : SkiaGm {
    override val name = "showmiplevels_explicit"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 1130
    override val height = 970

    private var image: Image? = null

    override fun onOnceBeforeDraw(canvas: GmCanvas) {
        val bytes = this::class.java.classLoader?.getResourceAsStream("images/ship.png")?.readBytes()
        if (bytes != null) {
            image = Image.decode(bytes)
        }
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0xDD / 255f, 0xDD / 255f, 0xDD / 255f)
        canvas.translate(10f, 10f)

        val img = image ?: return
        val samplingLevels = listOf(
            "nearest_none", "linear_none",
            "nearest_nearest", "linear_nearest",
            "nearest_linear", "linear_linear",
        )

        for (sl in samplingLevels) {
            canvas.save()
            var scale = 1f
            var col = 0
            while (scale >= 0.1f) {
                val shader = img.makeShader(TileMode.REPEAT, TileMode.REPEAT)
                val paint = Paint(shader = shader)
                canvas.save()
                canvas.scale(scale, scale)
                canvas.drawRect(Rect.fromXYWH(0f, 0f, 150f, 150f), paint)
                canvas.restore()
                canvas.translate(160f, 0f)
                scale *= 0.7f
                col++
            }
            canvas.restore()
            canvas.translate(0f, 160f)
        }
    }
}
