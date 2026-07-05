package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

class ImageFiltersXfermodesGm : SkiaGm {
    override val name = "imagefilters_xfermodes"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 480
    override val height = 480

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.translate(10f, 10f)

        val imf: ImageFilter? = ImageFilter.Offset(0f, 0f, null)

        val modes = arrayOf(BlendMode.SRC_ATOP, BlendMode.DST_IN)
        for (mode in modes) {
            canvas.save()
            doDraw(canvas, mode, null)
            canvas.translate(240f, 0f)
            doDraw(canvas, mode, imf)
            canvas.restore()
            canvas.translate(0f, 240f)
        }
    }

    private fun doDraw(canvas: GmCanvas, mode: BlendMode, imf: ImageFilter?) {
        canvas.save()
        canvas.clipRect(Rect(0f, 0f, 220f, 220f))
        canvas.saveLayer(Rect(0f, 0f, 220f, 220f), null)
        canvas.drawRect(Rect(0f, 0f, 220f, 220f), Paint(color = Color.GREEN))

        val r0 = Rect(10f, 60f, 210f, 160f)
        val r1 = Rect(60f, 10f, 160f, 210f)

        canvas.drawOval(r0, Paint(color = Color.RED, antiAlias = true))

        val paint = Paint(
            color = Color.fromRGBA(0f, 0f, 1f, 0.4f),
            imageFilter = imf,
            blendMode = mode,
            antiAlias = true,
        )
        canvas.drawOval(r1, paint)

        canvas.restore()
        canvas.restore()
    }
}
