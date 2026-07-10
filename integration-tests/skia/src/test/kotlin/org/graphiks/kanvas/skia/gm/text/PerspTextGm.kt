package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33

/**
 * Port of Skia's `gm/persptext.cpp` PerspTextGM.
 * Draws "Hamburgefons" repeatedly under perspective matrices
 * varying persp0/persp1 in X, Y, and XY columns.
 * @see https://github.com/google/skia/blob/main/gm/persptext.cpp
 */
class PerspTextGm : SkiaGm {
    override val name = "persptext"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 90.0
    override val width = 1024
    override val height = 768

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(r = 1f, g = 1f, b = 1f)

        val font = Font(typeface, size = 32f, subpixel = true)
        val text = "Hamburgefons"
        val textWidth = font.measureText(text)
        val textHeight = font.size * 1.2f

        var x = 10f
        var y = textHeight + 5f
        val kSteps = 8
        for (pm in PerspMode.entries) {
            for (step in 0 until kSteps) {
                val persp0 = when (pm) {
                    PerspMode.X -> step * 0.00025f / kSteps
                    PerspMode.XY -> step * -0.00025f / kSteps
                    PerspMode.Y -> 0f
                }
                val persp1 = when (pm) {
                    PerspMode.X -> 0f
                    PerspMode.Y -> step * 0.0025f / kSteps
                    PerspMode.XY -> step * -0.00125f / kSteps
                }
                val persp = Matrix33.makeAll(
                    1f, 0f, 0f,
                    0f, 1f, 0f,
                    persp0, persp1, 1f,
                )
                val centered = Matrix33.translate(x, y) * persp * Matrix33.translate(-x, -y)
                canvas.save()
                canvas.concat(centered)
                canvas.drawString(text, x, y, font, Paint(antiAlias = true, color = Color.BLACK))
                canvas.restore()
                y += textHeight + 5f
            }
            x += textWidth + 10f
            y = textHeight + 5f
        }
    }

    private enum class PerspMode { X, Y, XY }
}
