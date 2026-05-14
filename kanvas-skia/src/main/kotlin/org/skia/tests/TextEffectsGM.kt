package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkTextBlob
import org.skia.foundation.SkTextBlobBuilder
import org.skia.foundation.SkTextEncoding
import org.skia.math.SkISize
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/texteffects.cpp::fancyblobunderline`
 * (DEF_SIMPLE_GM, 1480 × 1380).
 *
 * Stress-tests `SkTextBlob` underline rendering across three font
 * families (`sans-serif`, `serif`, `monospace`) and five sizes
 * (100, 80, 60, 40, 20). Each blob is laid out as three runs
 * (allocRun + allocRunPosH + allocRunPos) over the same source string
 * `"aAjJgGyY_|{-(~[,]qQ}pP}zZ"`, then a stroked underline is drawn
 * underneath.
 *
 * **Iso-fidelity caveats** :
 *  - Upstream computes per-glyph **underline-intercept gaps** via
 *    [SkTextBlob.getIntercepts] (`getIntercepts` queries each
 *    glyph's outline for crossings with the underline strip and
 *    splits the underline into segments that go around descenders /
 *    glyph dips). `SkTextBlob.getIntercepts` is **not yet ported**
 *    (deferred to R3 / R-suivi text-intercept slice) — the Kotlin
 *    port falls back to a **single full-width underline** with no
 *    gap-splitting. Similarity vs. the upstream reference will be
 *    materially lower as a result.
 *  - All other glyph-rendering caveats from [TextBlobGM] apply
 *    (AWT scaler, no LCD AA, glyph-mask cache absent).
 *
 * The companion `textblob_intercepts` GM in the same C++ file has
 * the same intercept dependency and is intentionally not ported
 * here.
 */
public class TextEffectsGM : GM() {

    override fun getName(): String = "fancyblobunderline"
    override fun getISize(): SkISize = SkISize.Make(1480, 1380)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val paint = SkPaint().apply { isAntiAlias = true }
        val fam = arrayOf("sans-serif", "serif", "monospace")
        val test = "aAjJgGyY_|{-(~[,]qQ}pP}zZ"
        val blobOffsetX = 10f
        val blobOffsetY = 80f

        var canvasY = 0f
        for (familyIdx in fam.indices) {
            val familyName = fam[familyIdx]
            for (textSize in floatArrayOf(100f, 80f, 60f, 40f, 20f)) {
                val skFont = SkFont(ToolUtils.CreatePortableTypeface(familyName, SkFontStyle()), textSize)
                val uWidth = textSize / 15f
                paint.strokeWidth = uWidth
                paint.style = SkPaint.Style.kFill_Style

                val blob = makeFancyBlob(skFont, test) ?: continue
                c.drawTextBlob(blob, blobOffsetX, blobOffsetY + canvasY, paint)

                // Upstream : compute intercepts at `bounds = [uPos - uW/2, uPos + uW/2]`
                // and split the underline at each segment. Without
                // `getIntercepts` we fall back to a single full-width
                // underline at `uPos = uWidth` (the same Y the upstream
                // helper uses for its underline strip).
                val uPos = uWidth
                val bounds = blob.bounds()
                val start = bounds.left
                val end = bounds.right
                val underline = SkPathBuilder()
                    .moveTo(start, uPos)
                    .lineTo(end, uPos)
                    .detach()
                    .makeOffset(blobOffsetX, blobOffsetY + canvasY)
                paint.style = SkPaint.Style.kStroke_Style
                c.drawPath(underline, paint)

                canvasY += textSize * 1.3f
            }
            canvasY += 60f
        }
    }

    /**
     * Mirrors `MakeFancyBlob` from upstream's `texteffects.cpp`:
     * splits the source glyph stream into three roughly-equal runs
     * placed via `allocRun` (uniform advance) + `allocRunPosH`
     * (per-glyph X, constant Y) + `allocRunPos` (full per-glyph
     * `(x, y)`). The third run gets `y = 0` for every glyph (matching
     * upstream).
     */
    private fun makeFancyBlob(font: SkFont, text: String): SkTextBlob? {
        val codepoints = text.codePoints().toArray()
        val glyphCount = codepoints.size
        if (glyphCount == 0) return null

        val glyphsShort = ShortArray(glyphCount)
        font.unicharsToGlyphs(codepoints, glyphCount, glyphsShort)
        val widths = FloatArray(glyphCount)
        for (i in 0 until glyphCount) {
            widths[i] = font.getWidth(glyphsShort[i].toInt() and 0xFFFF)
        }

        val builder = SkTextBlobBuilder()
        var glyphIndex = 0
        var advance = 0f

        // 1) Default-positioned run.
        val defaultRunLen = glyphCount / 3
        run {
            val rec = builder.allocRun(font, defaultRunLen, advance, 0f)
            for (i in 0 until defaultRunLen) {
                rec.glyphs[i] = glyphsShort[glyphIndex + i].toInt() and 0xFFFF
            }
            for (i in 0 until defaultRunLen) {
                advance += widths[glyphIndex++]
            }
        }

        // 2) Horizontal-positioned run.
        val horizontalRunLen = glyphCount / 3
        run {
            val rec = builder.allocRunPosH(font, horizontalRunLen, 0f)
            // memcpy first — mirror upstream's `memcpy(buf.glyphs, glyphs + glyphIndex, ...)`.
            for (i in 0 until horizontalRunLen) {
                rec.glyphs[i] = glyphsShort[glyphIndex + i].toInt() and 0xFFFF
            }
            // …then walk widths in lock-step with glyphIndex.
            for (i in 0 until horizontalRunLen) {
                rec.pos[i] = advance
                advance += widths[glyphIndex++]
            }
        }

        // 3) Full-positioned run — remaining glyphs.
        val fullRunLen = glyphCount - glyphIndex
        run {
            val rec = builder.allocRunPos(font, fullRunLen)
            for (i in 0 until fullRunLen) {
                rec.glyphs[i] = glyphsShort[glyphIndex + i].toInt() and 0xFFFF
            }
            for (i in 0 until fullRunLen) {
                rec.pos[i * 2] = advance        // x offset
                rec.pos[i * 2 + 1] = 0f         // y offset
                advance += widths[glyphIndex++]
            }
        }

        return builder.make()
    }
}
