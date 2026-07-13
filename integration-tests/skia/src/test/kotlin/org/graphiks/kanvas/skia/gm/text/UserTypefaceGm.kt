package org.graphiks.kanvas.skia.gm.text

import kotlin.math.roundToInt
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.CustomTypeface
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.FontHinting
import org.graphiks.kanvas.text.FontMetrics
import org.graphiks.kanvas.text.Typeface
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/** Port of Skia's `gm/userfont.cpp`.
 *  Draws text samples with a user-specified typeface to test custom
 *  typeface registration and rendering.
 *  @see https://github.com/google/skia/blob/main/gm/userfont.cpp
 */
class UserTypefaceGm : SkiaGm {
    override val name = "user_typeface"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 70.0
    override val requiresZeroRefusals = true
    override val width = 810
    override val height = 452

    private lateinit var customTypeface: Typeface

    override fun onOnceBeforeDraw(canvas: GmCanvas) {
        // `gm/userfont.cpp` steals its first 128 glyphs from the default face,
        // normalizes them to one em, then round-trips the custom typeface.
        customTypeface = CustomTypeface.deserialize(makeCustomTypeface().serialize())
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val defaultTypeface = requireNotNull(Typefaces.fromResource("fonts/LiberationSans-Regular.ttf"))
        waterfall(canvas, defaultTypeface, x = 0f, drawBaseline = true)
        // Bake the right-column translation into paths and rectangles. The current
        // WebGPU path route deliberately refuses non-identity canvas transforms.
        waterfall(canvas, customTypeface, x = 400f, drawBaseline = false)
    }

    private fun makeCustomTypeface(): CustomTypeface {
        val defaultTypeface = requireNotNull(Typefaces.fromResource("fonts/LiberationSans-Regular.ttf"))
        val unitsPerEm = defaultTypeface.unitsPerEm
        val sourceFont = Font(defaultTypeface, size = unitsPerEm, hinting = FontHinting.NONE)
        val metrics = requireNotNull(sourceFont.getMetrics())
        val builder = CustomTypeface.Builder("userfont-roundtrip")
            .setMetrics(
                FontMetrics(
                    ascent = metrics.ascent / unitsPerEm,
                    descent = metrics.descent / unitsPerEm,
                    leading = metrics.leading / unitsPerEm,
                    xHeight = metrics.xHeight / unitsPerEm,
                    capHeight = metrics.capHeight / unitsPerEm,
                ),
            )

        for (codepoint in 0..127) {
            val glyphId = defaultTypeface.glyphIdForCodepoint(codepoint)
            val advance = defaultTypeface.getAdvance(glyphId, unitsPerEm) / unitsPerEm
            val path = (defaultTypeface.getGlyphPath(glyphId, unitsPerEm) ?: Path { })
                .transform(0f, 0f, 1f / unitsPerEm, 1f / unitsPerEm)
            if (codepoint % 2 == 0) {
                builder.setGlyph(codepoint, advance, path)
            } else {
                builder.setDrawableGlyph(
                    codepoint,
                    advance,
                    path,
                    Paint.fill(Color.fromArgb(255, 0, 128, 0)),
                )
            }
        }
        return builder.build()
    }

    private fun waterfall(canvas: GmCanvas, typeface: Typeface, x: Float, drawBaseline: Boolean) {
        var y = 16f
        var size = 9f
        while (size <= 100f) {
            val font = Font(typeface, size = size, antiAlias = true)
            val blob = font.toTextBlob("Typeface", 0f, 0f)
            if (drawBaseline) {
                canvas.drawRect(Rect.fromXYWH(x, y, width.toFloat(), 1f), Paint.fill(Color.fromArgb(255, 221, 221, 221)))
            }
            val bounds = blob.computeBounds(typeface)
            canvas.drawRect(
                Rect.fromLTRB(bounds.left + x + 20f, bounds.top + y, bounds.right + x + 20f, bounds.bottom + y),
                Paint(color = Color.fromArgb(255, 204, 204, 204), style = PaintStyle.STROKE),
            )
            // Draw outlines explicitly so both the default and the custom
            // face take the same GPU route. `GmCanvas.drawGlyphs` preserves
            // CustomTypeface's per-glyph green paint provider.
            blob.glyphRuns.forEach { run ->
                canvas.drawGlyphs(
                    run.glyphs.map { it.toInt() },
                    run.positions.map { position -> Point(position.x + x + 20f, position.y + y) },
                    font,
                    Paint.fill(Color.BLACK),
                )
            }
            val metrics = requireNotNull(font.getMetrics())
            y += (metrics.ascent - metrics.descent + metrics.leading) * 1.25f + 2f
            y = y.roundToInt().toFloat()
            size *= 1.25f
        }
    }
}
