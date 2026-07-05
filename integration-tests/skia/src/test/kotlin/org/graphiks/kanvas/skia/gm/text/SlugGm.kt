package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/slug.cpp::SlugGM` (1000 × 480).
 * Slug is a Skia GPU pre-compiled text blob (chromium-only).
 * On the raster backend, replaces Slug with simple drawString at increasing
 * scale/rotate combinations to exercise the same transform coverage.
 * @see https://github.com/google/skia/blob/main/gm/slug.cpp
 */
class SlugGm : SkiaGm {
    override val name = "slug"
    override val renderFamily = RenderFamily.TEXT
    override val minSimilarity = 0.0
    override val width = 1000
    override val height = 480

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width0: Int, height0: Int) {
        val text = "hamburgefons"
        val font = Font(typeface, size = 16f)
        val paint = Paint(color = Color.BLACK)

        canvas.clipRect(Rect.fromLTRB(40f, 50f, (width0 - 40).toFloat(), (height0 - 50).toFloat()))
        canvas.scale(1.3f, 1.3f)
        canvas.translate(0.5f, 0.5f)

        // Row 1: scale=1.0, no rotation
        canvas.save()
        canvas.translate(30f, 30f)
        canvas.drawString(text, 10f, 10f, font, paint)
        canvas.translate(370f, 0f)
        canvas.drawString(text, 10f, 10f, font, paint)
        canvas.restore()

        // Rows 2-6: scale goes 1.5, 2.0, 2.5, 3.0, 3.5 with 5deg rotation
        var y = 30f
        var scale = 1.5f
        while (scale < 4f) {
            y += 20f * scale
            canvas.save()
            canvas.translate(30f, y)
            canvas.save()
            canvas.scale(scale, scale)
            canvas.rotate(5f)
            canvas.drawString(text, 10f, 10f, font, paint)
            canvas.restore()
            canvas.translate(370f, 0f)
            canvas.save()
            canvas.scale(scale, scale)
            canvas.rotate(5f)
            canvas.drawString(text, 10f, 10f, font, paint)
            canvas.restore()
            canvas.restore()
            scale += 0.5f
        }
    }
}
