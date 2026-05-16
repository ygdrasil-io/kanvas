package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorGREEN
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRadialGradient
import org.skia.foundation.SkTextBlob
import org.skia.foundation.SkTextBlobBuilder
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.tools.ToolUtils
import kotlin.math.sin

/**
 * Port of Skia's `gm/textblobshader.cpp::TextBlobShader` (640 × 480).
 *
 * Exercises drawTextBlob offset vs. shader space behavior. A single
 * 3-run blob ("Blobber" in the default portable face, 30 pt) is built
 * with all three [SkTextBlobBuilder] entry points (allocRun,
 * allocRunPosH, allocRunPos) and then drawn 4×3 = 12 times with a
 * shared [SkRadialGradient] paint. The shader is anchored in canvas
 * space so the gradient should remain stationary while the glyphs
 * tile across the surface.
 */
public class TextBlobShaderGM : GM() {

    private var fGlyphs: IntArray = IntArray(0)
    private var fBlob: SkTextBlob? = null
    private var fShader: SkRadialGradient? = null

    override fun getName(): String = "textblobshader"
    override fun getISize(): SkISize = SkISize.Make(640, 480)

    override fun onOnceBeforeDraw() {
        val baseFont = ToolUtils.DefaultPortableFont()
        val txt = "Blobber"
        val codePoints = txt.codePoints().toArray()
        val glyphCount = codePoints.size
        val glyphsShort = ShortArray(glyphCount)
        baseFont.unicharsToGlyphs(codePoints, glyphCount, glyphsShort)
        fGlyphs = IntArray(glyphCount) { glyphsShort[it].toInt() and 0xFFFF }

        val font = ToolUtils.DefaultPortableFont().apply {
            isSubpixel = true
            edging = SkFont.Edging.kAntiAlias
            size = 30f
        }

        val builder = SkTextBlobBuilder()

        // Run 1 : horizontal spread anchored at (10, 10).
        val r1 = builder.allocRun(font, glyphCount, 10f, 10f)
        for (i in 0 until glyphCount) r1.glyphs[i] = fGlyphs[i]

        // Run 2 : per-glyph X with shared baseline at y = 80.
        val r2 = builder.allocRunPosH(font, glyphCount, 80f)
        for (i in 0 until glyphCount) {
            r2.glyphs[i] = fGlyphs[i]
            r2.pos[i] = font.size * i * 0.75f
        }

        // Run 3 : per-glyph (x, y) with sinusoidal baseline.
        val r3 = builder.allocRunPos(font, glyphCount)
        for (i in 0 until glyphCount) {
            r3.glyphs[i] = fGlyphs[i]
            r3.pos[i * 2] = font.size * i * 0.75f
            r3.pos[i * 2 + 1] = 150f + 5f * sin(i.toFloat() * 8f / glyphCount)
        }

        fBlob = builder.make()

        val sz = getISize()
        val colors = intArrayOf(SK_ColorRED, SK_ColorGREEN)
        val pos = floatArrayOf(0f, 1f)
        fShader = SkRadialGradient.Make(
            center = SkPoint(sz.width / 2f, sz.height / 2f),
            radius = sz.width * 0.66f,
            colors = colors,
            positions = pos,
            tileMode = SkTileMode.kRepeat,
        )
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val blob = fBlob ?: return

        val p = SkPaint().apply {
            isAntiAlias = true
            shader = fShader
        }

        val sz = getISize()
        val kXCount = 4
        val kYCount = 3
        for (i in 0 until kXCount) {
            for (j in 0 until kYCount) {
                c.drawTextBlob(
                    blob,
                    (i * sz.width / kXCount).toFloat(),
                    (j * sz.height / kYCount).toFloat(),
                    p,
                )
            }
        }
    }
}
