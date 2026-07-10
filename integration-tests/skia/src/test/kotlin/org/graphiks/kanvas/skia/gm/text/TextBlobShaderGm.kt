package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import kotlin.math.sin

/**
 * Port of Skia's `gm/textblobshader.cpp::TextBlobShader` (640 × 480).
 * Draws a 3-run blob 12 times with a radial gradient paint.
 * @see https://github.com/google/skia/blob/main/gm/textblobshader.cpp
 */
class TextBlobShaderGm : SkiaGm {
    override val name = "textblobshader"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.MEDIUM
    override val minSimilarity = 85.0
    override val width = 640
    override val height = 480

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val txt = "Blobber"
        val font = Font(typeface, size = 30f)

        val glyphIds = mutableListOf<UShort>()
        for (cp in txt.codePoints()) {
            glyphIds.add(typeface.glyphIdForCodepoint(cp).toUShort())
        }
        val glyphCount = glyphIds.size

        // Run 1: allocRun equivalent — origin at (10, 10)
        val run1Glyphs = mutableListOf<UShort>()
        val run1Positions = mutableListOf<Point>()
        var x = 10f
        for (gid in glyphIds) {
            run1Glyphs.add(gid)
            run1Positions.add(Point(x, 10f))
            x += typeface.getAdvance(gid.toInt(), font.size)
        }

        // Run 2: allocRunPosH equivalent — y=80, per-glyph x
        val run2Glyphs = mutableListOf<UShort>()
        val run2Positions = mutableListOf<Point>()
        for (i in 0 until glyphCount) {
            run2Glyphs.add(glyphIds[i])
            run2Positions.add(Point(font.size * i * 0.75f, 80f))
        }

        // Run 3: allocRunPos equivalent — sinusoidal y
        val run3Glyphs = mutableListOf<UShort>()
        val run3Positions = mutableListOf<Point>()
        for (i in 0 until glyphCount) {
            run3Glyphs.add(glyphIds[i])
            run3Positions.add(
                Point(
                    font.size * i * 0.75f,
                    150f + 5f * sin(i.toFloat() * 8f / glyphCount),
                ),
            )
        }

        val blob = TextBlob(
            glyphRuns = listOf(
                KanvasGlyphRun(run1Glyphs, run1Positions),
                KanvasGlyphRun(run2Glyphs, run2Positions),
                KanvasGlyphRun(run3Glyphs, run3Positions),
            ),
            typeface = typeface,
            fontSize = font.size,
        )

        val shader = Shader.RadialGradient(
            center = Point(width / 2f, height / 2f),
            radius = width * 0.66f,
            stops = listOf(
                GradientStop(0f, Color.RED),
                GradientStop(1f, Color.GREEN),
            ),
            tileMode = TileMode.REPEAT,
        )

        val paint = Paint(antiAlias = true, shader = shader)

        val kXCount = 4
        val kYCount = 3
        for (i in 0 until kXCount) {
            for (j in 0 until kYCount) {
                canvas.drawTextBlob(
                    blob,
                    (i * width / kXCount).toFloat(),
                    (j * height / kYCount).toFloat(),
                    paint,
                )
            }
        }
    }
}
