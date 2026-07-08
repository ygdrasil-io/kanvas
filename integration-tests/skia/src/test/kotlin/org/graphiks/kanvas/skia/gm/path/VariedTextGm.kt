package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color

/** Tests varied text rendering with random-colored circles and lines across the canvas. */
class VariedTextGm : SkiaGm {
    override val name = "variedtext"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 512

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f, 1f)
        val tf = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!
        val font = org.graphiks.kanvas.text.Font(tf, 24f)
        val samples = listOf(
            "The quick brown fox jumps over the lazy dog",
            "1234567890 !@#\$%^&*()_+-=[]{}|;:',.<>?/~`",
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ",
            "abcdefghijklmnopqrstuvwxyz",
            "Skia GM text rendering test",
            "Variable width text with different sizes",
            "A quick brown fox jumps over the lazy dog 1234567890",
            "Kanvas Kotlin Skia GM Port",
        )
        var y = 40f
        for ((i, text) in samples.withIndex()) {
            val f = org.graphiks.kanvas.text.Font(tf, 16f + (i % 4) * 4f)
            canvas.drawString(text, 20f, y, f, Paint(color = Color.BLACK))
            y += 50f
        }
    }
}
