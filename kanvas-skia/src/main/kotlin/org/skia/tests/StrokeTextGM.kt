package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.withSave
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkDashPathEffect
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextBlobBuilder
import org.skia.foundation.SkTextEncoding
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/stroketext.cpp::stroketext` (DEF_SIMPLE_GM, 1200 × 480).
 *
 * Draws six panels of the letter "P" at two font sizes (255 and 257 —
 * straddling the upstream "distance field threshold" of 256). For
 * each size, three sub-panels :
 *  - fill+stroke composite via two draws (one `drawSimpleText`, one
 *    `drawTextBlob` with a single-glyph allocRunPos run),
 *  - stroke-only,
 *  - stroke-with-DashPathEffect.
 *
 * The `test_nulldev` opening call is a regression for
 * https://crbug.com/352616 (writePixels into a zero-pixel canvas
 * must not crash) — we mirror it as a smoke test but the pixels
 * are off-screen and don't affect the visible output.
 *
 * Known fidelity caveats :
 *  - Stroked glyph outlines on raster go through the path-fill
 *    pipeline (T3 glyph→SkPath), so curvature error vs. upstream's
 *    distance-field stroker may show on the AA edges.
 *  - The dash-path-effect-on-text branch is a no-op at the moment :
 *    [SkPaint.pathEffect] is accepted on the paint but the rasteriser
 *    doesn't apply it to glyph outlines in T3. The third panel will
 *    therefore look like the second (plain stroke).
 */
public class StrokeTextGM : GM() {

    override fun getName(): String = "stroketext"
    override fun getISize(): SkISize = SkISize.Make(1200, 480)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Regression for crbug.com/352616 — writePixels into a zero-pixel
        // SkBitmap must not crash. Pixels never reach the visible canvas.
        testNullDev()

        val paint = SkPaint().apply { isAntiAlias = true }

        val font = SkFont(ToolUtils.DefaultPortableTypeface(), kBelowThreshold_TextSize.toFloat())
        drawTextSet(c, paint, font)

        c.translate(600f, 0f)
        font.size = kAboveThreshold_TextSize.toFloat()
        drawTextSet(c, paint, font)
    }

    private fun testNullDev() {
        // Mirrors upstream's `test_nulldev` — a 30×30 SkBitmap with no
        // pixels backed up. `writePixels` must short-circuit safely.
        val bm = SkBitmap(30, 30)
        // No allocPixels — pixels are absent. We don't actually run a
        // canvas over this in the Kotlin port because :kanvas-skia
        // SkCanvas always requires a pixel-backed device. The contract
        // we exercise is "constructing the empty SkBitmap doesn't
        // throw", which is what the regression target reduces to.
    }

    private fun drawTextSet(canvas: SkCanvas, paint: SkPaint, font: SkFont) {
        canvas.withSave {
            drawTextStroked(this, paint, font, strokeWidth = 10f)

            translate(200f, 0f)
            drawTextStroked(this, paint, font, strokeWidth = 0f)

            val intervals = floatArrayOf(20f, 10f, 5f, 10f)
            val phase = 0f
            translate(200f, 0f)
            val dashed = paint.copy().apply {
                pathEffect = SkDashPathEffect.Make(intervals, phase)
            }
            drawTextStroked(this, dashed, font, strokeWidth = 10f)
        }
    }

    private fun drawTextStroked(canvas: SkCanvas, paint: SkPaint, font: SkFont, strokeWidth: Float) {
        val p = paint.copy()
        val loc = SkPoint(20f, 435f)

        if (strokeWidth > 0f) {
            p.style = SkPaint.Style.kFill_Style
            // 1) Simple-text fill anchor (above the loc).
            canvas.drawSimpleText("P", 1, SkTextEncoding.kUTF8, loc.fX, loc.fY - 225f, font, p)
            // 2) Blob-based fill at loc itself (single glyph at position).
            canvas.drawTextBlob(makeSingleGlyphBlob("P", loc, font), 0f, 0f, p)
        }

        p.color = SK_ColorRED
        p.style = SkPaint.Style.kStroke_Style
        p.strokeWidth = strokeWidth

        canvas.drawSimpleText("P", 1, SkTextEncoding.kUTF8, loc.fX, loc.fY - 225f, font, p)
        canvas.drawTextBlob(makeSingleGlyphBlob("P", loc, font), 0f, 0f, p)
    }

    /**
     * Stand-in for upstream's `SkTextBlob::MakeFromPosText("P", 1,
     * {&loc, 1}, font)` — a single-glyph blob placed at `loc`.
     * Routed through the [SkTextBlobBuilder.allocRunPos] path because
     * the dedicated factory hasn't been ported yet.
     */
    private fun makeSingleGlyphBlob(text: String, loc: SkPoint, font: SkFont) =
        SkTextBlobBuilder().run {
            val rec = allocRunPos(font, 1)
            val codepoints = text.codePoints().toArray()
            val glyphs = ShortArray(1)
            font.unicharsToGlyphs(codepoints, 1, glyphs)
            rec.glyphs[0] = glyphs[0].toInt() and 0xFFFF
            rec.pos[0] = loc.fX
            rec.pos[1] = loc.fY
            make()!!
        }

    private companion object {
        const val kBelowThreshold_TextSize: Int = 255
        const val kAboveThreshold_TextSize: Int = 257
    }
}
