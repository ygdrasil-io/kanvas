package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color

/** Port of Skia's `gm/stroketext.cpp` (native variant).
 *  Tests stroke text rendering — draws stroked glyph paths using native
 *  stroke operations.
 *  @see https://github.com/google/skia/blob/main/gm/stroketext.cpp
 */
class StrokeTextNativeGm : SkiaGm {
    override val name = "stroketext_native"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 75.0
    override val requiresZeroRefusals = true
    override val width = 650
    override val height = 420
    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val stroke = Paint(
            color = Color(0xFFBB0000u),
            style = PaintStyle.STROKE,
            strokeWidth = 10f,
            strokeCap = StrokeCap.ROUND,
            strokeJoin = StrokeJoin.ROUND,
        )

        val ttf = requireNotNull(Typefaces.fromResource("fonts/Stroking.ttf"))
        val otf = requireNotNull(Typefaces.fromResource("fonts/Stroking.otf"))
        val variable = requireNotNull(Typefaces.fromResource("fonts/Variable.ttf"))

        // Keep the source order, color, stroke widths, strings, and `wght`
        // coordinate from gm/stroketext.cpp.
        canvas.drawString("○◉  ⁻₋⁺₊", 10f, 100f, Font(ttf, size = 100f), stroke)
        canvas.drawString("⁰₀¹₁²₂³₃", 10f, 200f, Font(ttf, size = 100f), stroke)
        canvas.drawString("○◉  ⁰¹³ᶠ", 10f, 300f, Font(otf, size = 100f), stroke)
        canvas.drawString(
            "tŧ",
            10f,
            400f,
            Font(variable, size = 100f, variationCoordinates = mapOf("wght" to 721f)),
            stroke.copy(strokeWidth = 1f),
        )
    }
}
