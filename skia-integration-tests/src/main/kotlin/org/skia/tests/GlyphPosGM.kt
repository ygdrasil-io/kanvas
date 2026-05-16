package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorMAGENTA
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextEncoding
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/glyph_pos.cpp` (six `DEF_SIMPLE_GM` variants,
 * 800 × 600 each).
 *
 * Each variant renders [kText] = "Proportional Hamburgefons #% fi"
 * six times at progressively-spicier CTMs (identity ; 3× scale ;
 * 3×3 scale ; 3×6 non-uniform scale ; 3×3 scale + skew ; perspective
 * + skew). The first two un-warped reference rows additionally
 * overlay :
 *
 *  - a green stroke around the [SkFont.measureText] bounds rect,
 *  - a red advance line, and
 *  - a vertical magenta tick at every glyph origin (per
 *    [SkFont.getWidth]),
 *
 * giving a six-way visual cross-check between glyph positioning and
 * the geometry the font advertises. Upstream gates the overlays on
 * `canvas->getLocalToDeviceAs3x3().getType() == translate|identity` ;
 * we don't expose the device CTM through the public API, so we drive
 * the same gate from a per-row boolean argument.
 *
 * The two flag dimensions — hairline (`0f`) vs non-hairline (`1.2f`)
 * stroke widths × `{kFill, kStroke, kStrokeAndFill}` paint styles —
 * cover the six PNG variants `glyph_pos_(h/n)_(f/s/b)`.
 */
public abstract class GlyphPosGM(
    private val variantName: String,
    private val strokeWidth: Float,
    private val strokeStyle: SkPaint.Style,
) : GM() {

    override fun getName(): String = variantName
    override fun getISize(): SkISize = SkISize.Make(800, 600)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        // Black 1×1 pixel at (40, 40) — upstream draws via drawPoint;
        // we approximate with a 1×1 rect for the same single-pixel hint.
        c.drawRect(SkRect.MakeXYWH(40f, 40f, 1f, 1f), SkPaint())

        // Two reference images (translate-only — overlays drawn).
        c.translate(50f, 50f)
        drawTestCase(c, 1f, drawRef = true)
        c.translate(0f, 50f)
        drawTestCase(c, 3f, drawRef = true)

        // Uniform scaling test.
        c.translate(0f, 100f)
        c.save()
        c.scale(3f, 3f)
        drawTestCase(c, 1f, drawRef = false)
        c.restore()

        // Non-uniform scaling test.
        c.translate(0f, 100f)
        c.save()
        c.scale(3f, 6f)
        drawTestCase(c, 1f, drawRef = false)
        c.restore()

        // Skew test.
        c.translate(0f, 80f)
        c.save()
        c.scale(3f, 3f)
        val skew = SkMatrix.MakeAll(1f, 8f / 25f, 0f, 2f / 25f, 1f, 0f)
        c.concat(skew)
        drawTestCase(c, 1f, drawRef = false)
        c.restore()

        // Perspective test.
        c.translate(0f, 80f)
        c.save()
        val perspective = SkMatrix.MakeAll(
            1f, 8f / 25f, 0f,
            2f / 25f, 1f, 0f,
            -1f / 340f, 0f, 1f,
        )
        c.concat(perspective)
        drawTestCase(c, 1f, drawRef = false)
        c.restore()
    }

    private fun drawTestCase(canvas: SkCanvas, textScale: Float, drawRef: Boolean) {
        val paint = SkPaint().apply {
            color = SK_ColorBLACK
            isAntiAlias = true
            this.strokeWidth = this@GlyphPosGM.strokeWidth
            style = strokeStyle
        }

        val font = SkFont(ToolUtils.DefaultPortableTypeface(), kTextHeight * textScale)

        if (drawRef) {
            val bounds = SkRect.MakeEmpty()
            val advance = font.measureText(kText, kText.length, SkTextEncoding.kUTF8, bounds)

            paint.strokeWidth = 0f
            paint.style = SkPaint.Style.kStroke_Style

            paint.color = SK_ColorGREEN
            canvas.drawRect(bounds, paint)

            paint.color = SK_ColorRED
            canvas.drawLine(0f, 0f, advance, 0f, paint)
        }

        paint.color = SK_ColorBLACK
        paint.strokeWidth = this.strokeWidth
        paint.style = strokeStyle
        canvas.drawSimpleText(kText, kText.length, SkTextEncoding.kUTF8, 0f, 0f, font, paint)

        if (drawRef) {
            // Per-character advance widths for the magenta tick marks.
            val codepoints = kText.codePoints().toArray()
            val n = codepoints.size
            val glyphsShort = ShortArray(n)
            font.unicharsToGlyphs(codepoints, n, glyphsShort)

            paint.strokeWidth = 0f
            paint.style = SkPaint.Style.kStroke_Style
            paint.color = SK_ColorMAGENTA

            var w = 0f
            for (i in 0 until n) {
                canvas.drawLine(w, 0f, w, 5f, paint)
                w += font.getWidth(glyphsShort[i].toInt() and 0xFFFF)
            }
        }
    }

    private companion object {
        private const val kTextHeight = 14f
        private val kText = "Proportional Hamburgefons #% fi"
    }
}

public class GlyphPosHbGM : GlyphPosGM("glyph_pos_h_b", 0f, SkPaint.Style.kStrokeAndFill_Style)
public class GlyphPosNbGM : GlyphPosGM("glyph_pos_n_b", 1.2f, SkPaint.Style.kStrokeAndFill_Style)
public class GlyphPosHsGM : GlyphPosGM("glyph_pos_h_s", 0f, SkPaint.Style.kStroke_Style)
public class GlyphPosNsGM : GlyphPosGM("glyph_pos_n_s", 1.2f, SkPaint.Style.kStroke_Style)
public class GlyphPosHfGM : GlyphPosGM("glyph_pos_h_f", 0f, SkPaint.Style.kFill_Style)
public class GlyphPosNfGM : GlyphPosGM("glyph_pos_n_f", 1.2f, SkPaint.Style.kFill_Style)
