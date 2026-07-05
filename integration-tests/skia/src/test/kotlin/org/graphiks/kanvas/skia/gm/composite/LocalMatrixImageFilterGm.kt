package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/localmatriximagefilter.cpp` (640 × 640).
 * Renders a 100×100 red circle through four filter factories (Blur, Dilate, Erode, Offset).
 * **Adaptation**: Skia's [SkImageFilter.makeWithLocalMatrix] is not available in Kanvas;
 * local-matrix variants are omitted. Baseline filter cells are kept.
 * @see https://github.com/google/skia/blob/main/gm/localmatriximagefilter.cpp
 */
class LocalMatrixImageFilterGm : SkiaGm {
    override val name = "localmatriximagefilter"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 640

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val image0 = makeSourceImage()

        val filters: Array<ImageFilter?> = arrayOf(
            ImageFilter.Blur(8f, 8f, input = null),
            ImageFilter.Dilate(8f, 8f, null),
            ImageFilter.Erode(8f, 8f, null),
            ImageFilter.Offset(8f, 8f, null),
        )

        val spacer = image0.width * 3f / 2f

        canvas.translate(40f, 40f)
        for (filter in filters) {
            canvas.save()
            showImage(canvas, image0, filter)
            canvas.restore()
            canvas.translate(0f, spacer)
        }
    }

    private fun makeSourceImage(): Image {
        val surface = Surface(100, 100)
        surface.canvas {
            drawRect(Rect(0f, 0f, 100f, 100f), Paint(color = Color.TRANSPARENT))
            val circlePath = Path { }.apply { addCircle(50f, 50f, 50f) }
            drawPath(circlePath, Paint(antiAlias = true, color = Color.RED))
        }
        return surface.makeImageSnapshot()
    }

    private fun showImage(canvas: GmCanvas, image: Image, filter: ImageFilter?) {
        val strokePaint = Paint(style = PaintStyle.STROKE)
        val r = Rect(-0.5f, -0.5f, image.width + 0.5f, image.height + 0.5f)
        canvas.drawRect(r, strokePaint)

        val fillPaint = Paint(style = PaintStyle.FILL, imageFilter = filter)
        canvas.drawImage(image, Rect(0f, 0f, image.width.toFloat(), image.height.toFloat()), fillPaint)
    }
}
