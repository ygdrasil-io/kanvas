package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color

/**
 * Port of Skia's `gm/pdf_never_embed.cpp::pdf_never_embed` (512 × 512).
 * Renders "HELLO, WORLD!" four ways: plain, rotated 45°, y-scaled 4×, y-scaled 0.5×.
 * The PDF subsetting test is a no-op on raster; this exercises drawString + CTM transforms.
 * @see https://github.com/google/skia/blob/main/gm/pdf_never_embed.cpp
 */
class PdfNeverEmbedGm : SkiaGm {
    override val name = "pdf_never_embed"
    override val renderFamily = RenderFamily.TEXT
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 512

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width0: Int, height0: Int) {
        val font = Font(typeface, size = 60f)
        val text = "HELLO, WORLD!"

        canvas.drawColor(r = 1f, g = 1f, b = 1f)

        canvas.drawString(text, 30f, 90f, font, Paint(color = Color.BLACK))

        canvas.save()
        canvas.rotate(45f)
        canvas.drawString(text, 30f, 45f, font, Paint(color = Color.fromRGBA(0.94f, 0f, 0f, 0.94f)))
        canvas.restore()

        canvas.save()
        canvas.scale(1f, 4f)
        canvas.drawString(text, 15f, 70f, font, Paint(color = Color.fromRGBA(0f, 0.5f, 0f, 0.94f)))
        canvas.restore()

        canvas.scale(1f, 0.5f)
        canvas.drawString(text, 30f, 700f, font, Paint(color = Color.fromRGBA(0f, 0f, 0.5f, 0.94f)))
    }
}
