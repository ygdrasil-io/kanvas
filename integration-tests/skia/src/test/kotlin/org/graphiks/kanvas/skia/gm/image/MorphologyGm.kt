package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/** Port of Skia's `gm/morphology.cpp`.
 *  Tests ImageFilter morphology (dilate/erode) — draws rectangles with
 *  morphology filters applied.
 *  @see https://github.com/google/skia/blob/main/gm/morphology.cpp
 */
class MorphologyGm : SkiaGm {
    override val name = "morphology"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 700
    override val height = 560

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawRect(Rect(0f, 0f, 700f, 560f), Paint(Color.BLACK))

        val surf = Surface(135, 135)
        surf.canvas { drawColor(Color.BLACK) }
        val white = Paint(Color.WHITE)
        surf.canvas {
            drawRect(Rect.fromXYWH(10f, 10f, 110f, 50f), white)
            drawRect(Rect.fromXYWH(10f, 65f, 110f, 50f), white)
        }
        val image = surf.makeImageSnapshot()

        data class Sample(val rx: Int, val ry: Int)
        val samples = listOf(Sample(0, 0), Sample(0, 2), Sample(2, 0), Sample(2, 2), Sample(25, 25))

        for (j in 0 until 4) {
            for (i in samples.indices) {
                val s = samples[i]
                val imageFilter = if (j and 0x01 != 0) {
                    ImageFilter.Erode(s.rx.toFloat(), s.ry.toFloat())
                } else {
                    ImageFilter.Dilate(s.rx.toFloat(), s.ry.toFloat())
                }
                val paint = Paint(imageFilter = imageFilter)
                canvas.save()
                canvas.translate((i * 140).toFloat(), (j * 140).toFloat())
                canvas.clipRect(Rect(0f, 0f, image.width.toFloat(), image.height.toFloat()))
                canvas.drawImage(image, Rect(0f, 0f, image.width.toFloat(), image.height.toFloat()), paint)
                canvas.restore()
            }
        }
    }
}
