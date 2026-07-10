package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/dftext.cpp` DFTextGM (1024 × 768).
 * Exercises text rendering at various scales, rotations, skews,
 * perspective transforms, and gamma-corrected blending.
 * Offscreen DF-surface allocation is dropped since the Kanvas
 * raster path does not differentiate distance-field rendering.
 * @see https://github.com/google/skia/blob/main/gm/dftext.cpp
 */
class DfTextGm : SkiaGm {
    override val name = "dftext"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 73.0
    override val width = 1024
    override val height = 768

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    private fun fontHeight(font: Font): Float {
        val m = font.getMetrics()
        return if (m != null) m.ascent - m.descent else font.size * 1.2f
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(r = 1f, g = 1f, b = 1f)

        val textSizes = floatArrayOf(9.0f, 9.0f * 2.0f, 9.0f * 5.0f, 9.0f * 2.0f * 5.0f)
        val scales = floatArrayOf(2.0f * 5.0f, 5.0f, 2.0f, 1.0f)

        var font = Font(typeface, subpixel = true)
        val text = "Hamburgefons"

        // ─── 1. Scale up ──────────────────────────────────────────────
        var x = 0f
        var y = 78f
        for (i in textSizes.indices) {
            canvas.save()
            canvas.translate(x, y)
            canvas.scale(scales[i], scales[i])
            font = font.copy(size = textSizes[i])
            canvas.drawSimpleText(text, 0f, 0f, font, Paint(antiAlias = true))
            canvas.restore()
            y += fontHeight(font) * scales[i]
        }

        // ─── 2. Rotation ──────────────────────────────────────────────
        for (i in 0 until 5) {
            val rotX = 10f
            var rotY = y
            canvas.save()
            canvas.translate((10 + i * 200).toFloat(), -80f)
            var ps = 6
            while (ps <= 32) {
                font = font.copy(size = ps.toFloat())
                canvas.save()
                canvas.translate(rotX, rotY)
                canvas.rotate((i * 5).toFloat())
                canvas.translate(-rotX, -rotY)
                canvas.drawSimpleText(text, rotX, rotY, font, Paint(antiAlias = true))
                canvas.restore()
                rotY += fontHeight(font)
                ps += 3
            }
            canvas.restore()
        }

        // ─── 3. Scale down ────────────────────────────────────────────
        font = font.copy(subpixel = true)
        x = 680f
        y = 20f
        val arraySize = textSizes.size
        for (i in 0 until arraySize) {
            canvas.save()
            canvas.translate(x, y)
            val scaleFactor = 1f / scales[arraySize - i - 1]
            canvas.scale(scaleFactor, scaleFactor)
            font = font.copy(size = textSizes[i])
            canvas.drawSimpleText(text, 0f, 0f, font, Paint(antiAlias = true))
            canvas.restore()
            y += fontHeight(font) * scaleFactor
        }

        // ─── 4. Positioned glyph blob ─────────────────────────────────
        canvas.save()
        canvas.scale(2.0f, 2.0f)
        font = font.copy(size = textSizes[0])
        val glyphIds = mutableListOf<UShort>()
        val positions = mutableListOf<Point>()
        var cursorX = 340f
        for (cp in text.codePoints()) {
            val gid = typeface.glyphIdForCodepoint(cp)
            glyphIds.add(gid.toUShort())
            positions.add(Point(cursorX, 75f))
            cursorX += typeface.getAdvance(gid, font.size)
        }
        val blob = TextBlob(
            glyphRuns = listOf(KanvasGlyphRun(glyphIds, positions)),
            typeface = typeface,
            fontSize = font.size,
        )
        canvas.drawTextBlob(blob, 0f, 0f, Paint(antiAlias = true))
        canvas.restore()

        // ─── 5. Gamma-corrected blending ──────────────────────────────
        val fgColors = listOf(
            Color.WHITE,
            Color.fromRGBA(1f, 1f, 0f, 1f),
            Color.fromRGBA(1f, 0f, 1f, 1f),
            Color.fromRGBA(0f, 1f, 1f, 1f),
            Color.RED,
            Color.fromRGBA(0f, 1f, 0f, 1f),
            Color.BLUE,
            Color.BLACK,
        )

        var rect = Rect.fromLTRB(670f, 215f, 820f, 397f)
        canvas.drawRect(rect, Paint(color = Color.fromRGBA(247f / 255f, 243f / 255f, 247f / 255f)))

        x = 680f
        y = 235f
        font = font.copy(size = 19f)
        for (color in fgColors) {
            canvas.drawSimpleText(text, x, y, font, Paint(color = color))
            y += fontHeight(font)
        }

        rect = Rect.fromLTRB(820f, 215f, 970f, 397f)
        canvas.drawRect(rect, Paint(color = Color.fromRGBA(24f / 255f, 28f / 255f, 24f / 255f)))

        x = 830f
        y = 235f
        font = font.copy(size = 19f)
        for (color in fgColors) {
            canvas.drawSimpleText(text, x, y, font, Paint(color = color))
            y += fontHeight(font)
        }

        // ─── 6. Skew ──────────────────────────────────────────────────
        canvas.save()
        font = font.copy(size = 32f, antiAlias = true, subpixel = false)
        canvas.skew(0.0f, 0.151515f)
        canvas.drawSimpleText(text, 745f, 70f, font, Paint(antiAlias = true))
        canvas.restore()

        canvas.save()
        font = font.copy(size = 32f, subpixel = true)
        canvas.skew(0.5f, 0.0f)
        canvas.drawSimpleText(text, 580f, 125f, font, Paint(antiAlias = true))
        canvas.restore()

        // ─── 7. Perspective ───────────────────────────────────────────
        canvas.save()
        font = font.copy(size = 37.5f, antiAlias = true, subpixel = false)
        val persp1 = Matrix33.makeAll(
            0.9839f, 0f, 0f,
            0.2246f, 0.6829f, 0f,
            0.0002352f, -0.0003844f, 1f,
        )
        canvas.concat(persp1)
        canvas.translate(1100f, -295f)
        canvas.drawSimpleText(text, 0f, 0f, font, Paint(antiAlias = true))
        canvas.restore()

        canvas.save()
        font = font.copy(size = 0.1f, antiAlias = false, subpixel = false)
        val persp2 = Matrix33.makeAll(
            0.9839f, 0f, 0f,
            0.2246f, 0.6829f, 0f,
            0.0002352f, -0.0003844f, 1f,
        )
        canvas.concat(persp2)
        canvas.translate(1075f, -245f)
        canvas.scale(375f, 375f)
        canvas.drawSimpleText(text, 0f, 0f, font, Paint(antiAlias = false))
        canvas.restore()
    }
}
