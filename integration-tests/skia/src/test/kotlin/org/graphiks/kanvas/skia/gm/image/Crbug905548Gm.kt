package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.canvas.drawCircle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/crbug_905548.cpp`.
 * Two stacked panels testing image-filter pipeline with degenerate erosion.
 * @see https://github.com/google/skia/blob/main/gm/crbug_905548.cpp
 */
class Crbug905548Gm : SkiaGm {
    override val name = "crbug_905548"
    override val renderFamily = RenderFamily.IMAGE
    override val minSimilarity = 0.0
    override val width = 100
    override val height = 200

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val offscreen = Surface(100, 100)
        offscreen.canvas { drawCircle(50f, 50f, 45f, Paint.fill(Color.BLACK)) }
        val circleImage = offscreen.makeImageSnapshot()
        val rectBounds = Rect.fromXYWH(0f, 0f, 100f, 100f)

        val blurFilter = ImageFilter.Blur(sigmaX = 15f, sigmaY = 15f)
        val erodedFilter = ImageFilter.Erode(radiusX = 0f, radiusY = 0f, input = blurFilter)
        val blendFilter = ImageFilter.Blend(
            mode = BlendMode.DST_OUT,
            background = erodedFilter,
            foreground = ImageFilter.Blur(sigmaX = 0f, sigmaY = 0f),
        )

        canvas.saveLayer(rectBounds, Paint(imageFilter = blendFilter))
        canvas.drawImage(circleImage, rectBounds)
        canvas.restore()

        canvas.translate(0f, 100f)
        val multFilter = ImageFilter.Blend(
            mode = BlendMode.MODULATE,
            background = erodedFilter,
            foreground = ImageFilter.Blur(sigmaX = 0f, sigmaY = 0f),
        )
        canvas.saveLayer(rectBounds, Paint(imageFilter = multFilter))
        canvas.drawImage(circleImage, rectBounds)
        canvas.restore()
    }
}
