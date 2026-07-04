package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color

private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

/**
 * Port of Skia's `gm/lcdoverlap.cpp::LcdOverlapGM` (750 × 750).
 * Renders text six times in a rotation around four pivot points with different blend modes.
 * @see https://github.com/google/skia/blob/main/gm/lcdoverlap.cpp
 */
class LcdOverlapGm : SkiaGm {
    override val name = "lcdoverlap"
    override val renderFamily = RenderFamily.TEXT
    override val minSimilarity = 0.0
    override val width = 750
    override val height = 750

    private val font = Font(typeface, size = 32f)
    private val text = "able was I ere I saw elba"
    private val textWidth = font.measureText(text)

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val offsetX = width / 4f
        val offsetY = height / 4f
        drawTestCase(canvas, offsetX, offsetY, BlendMode.SRC, BlendMode.SRC)
        drawTestCase(canvas, 3 * offsetX, offsetY, BlendMode.SRC_OVER, BlendMode.SRC_OVER)
        drawTestCase(canvas, offsetX, 3 * offsetY, BlendMode.HARD_LIGHT, BlendMode.LUMINOSITY)
        drawTestCase(canvas, 3 * offsetX, 3 * offsetY, BlendMode.SRC_OVER, BlendMode.SRC)
    }

    private fun drawTestCase(
        canvas: GmCanvas,
        x: Float,
        y: Float,
        mode: BlendMode,
        mode2: BlendMode,
    ) {
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color(0xFFFFFF00u), Color(0xFF00FFFFu), Color(0xFFFF00FFu))
        for (i in colors.indices) {
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(360.0f / colors.size * i)
            canvas.translate(-textWidth / 2f + 0.5f, 0f)
            val paint = Paint(color = colors[i], blendMode = if (i % 2 == 0) mode else mode2)
            canvas.drawString(text, 0f, 0f, font, paint)
            canvas.restore()
        }
    }
}
