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
 * Port of Skia's `gm/textblobuseaftergpufree.cpp::TextBlobUseAfterGpuFree`
 * (200 × 200). Draws "Hamburgefons" twice — GPU free is a no-op on raster.
 * @see https://github.com/google/skia/blob/main/gm/textblobuseaftergpufree.cpp
 */
class TextBlobUseAfterGpuFreeGm : SkiaGm {
    override val name = "textblobuseaftergpufree"
    override val renderFamily = RenderFamily.TEXT
    override val minSimilarity = 0.0
    override val width = 200
    override val height = 200

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val text = "Hamburgefons"
        val font = Font(typeface, size = 20f)
        val blob = font.toTextBlob(text, 0f, 0f)

        // White rect over top half
        val rect = Rect(0f, 0f, width.toFloat(), height / 2f)
        canvas.drawRect(rect, Paint(color = Color.WHITE))
        canvas.drawTextBlob(blob, 20f, 60f, Paint())

        // GPU free is a no-op on raster
        canvas.drawTextBlob(blob, 20f, 160f, Paint())
    }
}
