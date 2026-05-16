package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorBLACK
import org.skia.math.SK_ColorCYAN
import org.skia.math.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.math.SkColor
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRRect
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTextBlobBuilder
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkScalar
import org.skia.math.SkVector
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/skbug_257.cpp` (`DEF_SIMPLE_GM(skbug_257, …)`,
 * 512 × 512).
 *
 * Reproduces [skbug.com/257](https://bugs.skia.org/257) — the
 * regression where a very large CTM translate (`-225364`) combined
 * with a slightly-non-integer pre-scale (`1.00168`) caused the
 * shader-fed primitive renderer to lose alignment with the reference
 * cyan outlines drawn after the translate stack pops.
 *
 * Layout :
 *  - Top-left : a 240×240 rect with a rotated checkerboard fill,
 *    overdrawn by a reference cyan circle.
 *  - Top-right : the same checker pattern in a 40-radius rounded
 *    rect.
 *  - Bottom-left : eight stroked round-cap points (paired into 4
 *    cardinal positions), each pair offset by `1/64` to demonstrate
 *    sub-pixel rounding alignment, plus the cyan circle.
 *  - Bottom-right : three lines of "HELLO WORLD" drawn through
 *    `drawSimpleText` / `drawTextBlob` (allocRunPos + allocRunPosH),
 *    drawn twice — once black under the translated stack, once cyan
 *    after it pops.
 *
 * **kanvas-skia adaptations** :
 *  - `SkBitmap::eraseArea` is not yet on the kanvas-skia surface ; we
 *    inline a per-pixel fill of the two off-diagonal quadrants
 *    (matches upstream `eraseArea(LTRB(0,0,size,size), c2)` +
 *    `eraseArea(LTRB(size,size,2*size,2*size), c2)`).
 *  - `SkAutoCanvasRestore(canvas, true)` is mirrored manually with
 *    `save()` / `restore()` pairs.
 *  - `font.textToGlyphs` + `font.getPos` / `font.getXPos` are not
 *    yet on the surface, so the `exercise_draw_pos_text` helpers
 *    seed their positions via the font's `getWidth` (sufficient for
 *    horizontal text — the GM doesn't rely on Skia's stricter
 *    sub-pixel metrics here).
 *  - LCD-subpixel edging downgrades to plain AA on glyphs (cf.
 *    `SkFont.kt`).
 *
 * C++ source : `gm/skbug_257.cpp`. Reference: `skbug_257.png`.
 */
public class Skbug257GM : GM() {

    override fun getName(): String = "skbug_257"
    override fun getISize(): SkISize = SkISize.Make(512, 512)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val size: SkScalar = 256f
        val scale = 1.00168f

        c.save() // autoCanvasRestore0
        c.scale(scale, scale)

        c.save() // autoCanvasRestore
        val checker = SkPaint().apply {
            isAntiAlias = true
        }
        rotatedCheckerboardShader(checker, SK_ColorWHITE, SK_ColorBLACK, 16)

        c.clear(0xFFCECFCE.toInt())
        val translate: SkScalar = 225364.0f
        c.translate(0f, -translate)

        // Test rects
        val rect = SkRect.MakeLTRB(8f, 8f + translate, size - 8f, size - 8f + translate)
        c.drawRect(rect, checker)

        // Test Paths (an RRect, in this GM)
        c.translate(size, 0f)
        val rrect = SkRRect()
        val radii = arrayOf<SkVector>(
            SkPoint(40f, 40f), SkPoint(40f, 40f),
            SkPoint(40f, 40f), SkPoint(40f, 40f),
        )
        rrect.setRectRadii(rect, radii)
        c.drawRRect(rrect, checker)

        // Test Points
        c.translate(-size, size)
        val delta = 1.0f / 64.0f
        val points = arrayOf(
            SkPoint(size / 2,       8f + translate),
            SkPoint(size / 2,       8f + translate + delta),
            SkPoint(8f,             size / 2 + translate),
            SkPoint(8f,             size / 2 + translate + delta),
            SkPoint(size / 2,       size - 8f + translate),
            SkPoint(size / 2,       size - 8f + translate + delta),
            SkPoint(size - 8f,      size / 2 + translate),
            SkPoint(size - 8f,      size / 2 + translate + delta),
        )
        checker.style = SkPaint.Style.kStroke_Style
        checker.strokeWidth = 8f
        checker.strokeCap = SkPaint.Cap.kRound_Cap
        c.drawPoints(SkCanvas.PointMode.kLines, points, checker)

        // Test Text
        c.translate(size, 0f)
        testText(c, size, SK_ColorBLACK, translate)

        c.restore() // autoCanvasRestore

        // Reference points (without the huge translations).
        val stroke = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 5f
            color = SK_ColorCYAN
        }
        c.drawCircle(size / 2, size / 2, size / 2 - 10, stroke)
        c.drawCircle(3 * size / 2, size / 2, size / 2 - 10, stroke)
        c.drawCircle(size / 2, 384f, size / 2 - 10, stroke)
        c.translate(size, size)
        testText(c, size, SK_ColorCYAN, 0.0f)

        c.restore() // autoCanvasRestore0
    }

    private fun rotatedCheckerboardShader(paint: SkPaint, c1: SkColor, c2: SkColor, size: Int) {
        val bm = SkBitmap(2 * size, 2 * size)
        bm.eraseColor(c1)
        // Inlined `eraseArea` for the two off-diagonal quadrants (0,0,s,s)
        // and (s,s,2*s,2*s) — see KDoc.
        for (yy in 0 until size) {
            for (xx in 0 until size) {
                bm.setPixel(xx, yy, c2)
                bm.setPixel(xx + size, yy + size, c2)
            }
        }
        val matrix = SkMatrix.MakeScale(0.75f, 0.75f).preRotate(30.0f)
        paint.shader = bm.makeShader(
            SkTileMode.kRepeat, SkTileMode.kRepeat,
            SkSamplingOptions.Default, matrix,
        )
    }

    private fun testText(canvas: SkCanvas, size: SkScalar, color: SkColor, y: SkScalar) {
        val font = SkFont(ToolUtils.DefaultPortableTypeface(), 24f)
        font.edging = SkFont.Edging.kAlias
        val type = SkPaint().apply { this.color = color }
        val text = "HELLO WORLD"
        canvas.drawSimpleText(
            text, text.length,
            org.skia.foundation.SkTextEncoding.kUTF8,
            32f, size / 2 + y, font, type,
        )
        val lineSpacing = font.getSpacing()
        exerciseDrawPosText(canvas, text, 32f, size / 2 + y + lineSpacing, font, type)
        exerciseDrawPosTextH(canvas, text, 32f, size / 2 + y + 2 * lineSpacing, font, type)
    }

    private fun exerciseDrawPosText(
        canvas: SkCanvas, text: String, x: SkScalar, y: SkScalar,
        font: SkFont, paint: SkPaint,
    ) {
        val codepoints = text.codePoints().toArray()
        val n = codepoints.size
        if (n == 0) return
        val builder = SkTextBlobBuilder()
        val rec = builder.allocRunPos(font, n)
        val glyphsShort = ShortArray(n)
        font.unicharsToGlyphs(codepoints, n, glyphsShort)
        // Lay out horizontally using per-glyph advance widths.
        var advance = 0f
        for (i in 0 until n) {
            val gid = glyphsShort[i].toInt() and 0xFFFF
            rec.glyphs[i] = gid
            rec.pos[2 * i] = x + advance
            rec.pos[2 * i + 1] = y
            advance += font.getWidth(gid)
        }
        val blob = builder.make() ?: return
        canvas.drawTextBlob(blob, 0f, 0f, paint)
    }

    private fun exerciseDrawPosTextH(
        canvas: SkCanvas, text: String, x: SkScalar, y: SkScalar,
        font: SkFont, paint: SkPaint,
    ) {
        val codepoints = text.codePoints().toArray()
        val n = codepoints.size
        if (n == 0) return
        val builder = SkTextBlobBuilder()
        val rec = builder.allocRunPosH(font, n, 0f)
        val glyphsShort = ShortArray(n)
        font.unicharsToGlyphs(codepoints, n, glyphsShort)
        var advance = 0f
        for (i in 0 until n) {
            val gid = glyphsShort[i].toInt() and 0xFFFF
            rec.glyphs[i] = gid
            rec.pos[i] = advance
            advance += font.getWidth(gid)
        }
        val blob = builder.make() ?: return
        canvas.drawTextBlob(blob, x, y, paint)
    }
}
