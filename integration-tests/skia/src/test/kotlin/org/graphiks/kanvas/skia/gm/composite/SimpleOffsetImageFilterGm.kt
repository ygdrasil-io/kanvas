package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/offsetimagefilter.cpp::SimpleOffsetImageFilterGM`.
 * Exercises SkImageFilters::Offset with clip rect variants (cropRect omitted where unavailable).
 * @see https://github.com/google/skia/blob/main/gm/offsetimagefilter.cpp
 */
class SimpleOffsetImageFilterGm : SkiaGm {
    override val name = "simple-offsetimagefilter"
    override val renderFamily = RenderFamily.COMPOSITE
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 200

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val r = Rect(0f, 0f, 40f, 40f)

        canvas.translate(40f, 40f)

        // Col 1: no filter
        doDraw(canvas, r, null)

        canvas.translate(100f, 0f)
        // Col 2: offset filter, no clip
        doDraw(canvas, r, ImageFilter.Offset(20f, 20f, null))

        canvas.translate(100f, 0f)
        // Col 3: offset filter with clip = source rect
        doDraw(canvas, r, ImageFilter.Offset(20f, 20f, null), clipR = r)

        canvas.translate(100f, 0f)
        // Col 4: offset filter with different clip
        val clipR2 = Rect(40f, 40f, 80f, 80f)
        doDraw(canvas, r, ImageFilter.Offset(20f, 20f, null), clipR = clipR2)

        canvas.translate(100f, 0f)
        // Col 5: offset filter, no clip (repeat of col 2)
        doDraw(canvas, r, ImageFilter.Offset(20f, 20f, null))

        canvas.translate(100f, 0f)
        // Col 6: offset filter with clip
        doDraw(canvas, r, ImageFilter.Offset(20f, 20f, null), clipR = clipR2)
    }

    private fun doDraw(
        canvas: GmCanvas,
        r: Rect,
        imgf: ImageFilter?,
        clipR: Rect? = null,
    ) {
        // Draw clip outline
        if (clipR != null) {
            val green = Paint(color = Color(0xFF00FF00u), style = PaintStyle.STROKE)
            val inset = Rect(clipR.left + 0.5f, clipR.top + 0.5f, clipR.right - 0.5f, clipR.bottom - 0.5f)
            canvas.drawRect(inset, green)
        }

        // Blue source rect
        val blue = Paint(color = Color(0x660000FFu))
        canvas.drawRect(r, blue)

        // Clip + draw red offset rect
        if (clipR != null) {
            canvas.save()
            canvas.clipRect(clipR)
        }
            val red = if (imgf != null) {
            Paint(color = Color(0x66FF0000u), imageFilter = imgf)
        } else {
            Paint(color = Color(0x66FF0000u))
        }
        canvas.drawRect(r, red)
        if (clipR != null) {
            canvas.restore()
        }
    }
}
