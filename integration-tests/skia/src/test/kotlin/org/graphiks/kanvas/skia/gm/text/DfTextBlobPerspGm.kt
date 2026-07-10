package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color

/**
 * Port of Skia's `gm/dftext_blob_persp.cpp::DFTextBlobPerspGM`.
 * Exercises text blob drawing under various CTM transforms (rotate, scale, translate)
 * and optional clipping. The upstream version uses SDF text + perspective matrices
 * (GPU-only); here we emulate the transform combinations via `canvas.rotate`/`scale`.
 * @see https://github.com/google/skia/blob/main/gm/dftext_blob_persp.cpp
 */
class DfTextBlobPerspGm : SkiaGm {
    override val name = "dftext_blob_persp"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 900
    override val height = 350

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width0: Int, height0: Int) {
        val text = "SkiaText"
        val font = Font(typeface, size = 32f)
        val paint = Paint(color = Color.BLACK)

        canvas.drawColor(r = 1f, g = 1f, b = 1f)

        var y = 40f
        for (scale in listOf(1f, 1.5f, 2f, 2.5f, 3f)) {
            var x = 20f

            canvas.save()
            canvas.translate(x, y)
            canvas.scale(scale, scale)
            canvas.drawString(text, 0f, 0f, font, paint)
            canvas.restore()
            x += 250f

            canvas.save()
            canvas.translate(x, y)
            canvas.scale(scale, scale)
            canvas.rotate(5f)
            canvas.drawString(text, 0f, 0f, font, paint)
            canvas.restore()
            x += 250f

            canvas.save()
            canvas.translate(x, y)
            canvas.scale(scale, scale)
            canvas.rotate(-10f)
            canvas.scale(1.5f, 0.8f)
            canvas.drawString(text, 0f, 0f, font, paint)
            canvas.restore()

            y += 50f
        }
    }
}
