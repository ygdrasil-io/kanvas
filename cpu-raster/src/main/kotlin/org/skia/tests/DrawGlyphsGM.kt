package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRSXform
import org.skia.foundation.SkTextBlob
import org.skia.foundation.SkTextBlobBuilder
import org.skia.foundation.SkTypeface
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.tools.ToolUtils
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of Skia's `gm/drawglyphs.cpp::DrawGlyphsGM` (640 × 480).
 *
 * Exercises [SkCanvas]'s lower-level glyph-rendering entry points :
 *  - `canvas->drawGlyphs(glyphIds, positions, origin, font, paint)` — a
 *    glyph-ID array with explicit per-glyph positions, anchored at an
 *    `origin` offset. Drawn three times in the GM (twice at the top of
 *    the canvas, once at the bottom after positions are shifted).
 *  - `canvas->drawGlyphsRSXform(glyphIds, xforms, origin, font, paint)`
 *    — each glyph receives its own rotation + scale + translation
 *    (an [SkRSXform]). Used here to render the same text along the top
 *    half of a circle of radius `length / π`.
 *
 * C++ original (full):
 * ```cpp
 * static const char gText[] = "Call me Ishmael. Some years ago—never mind how long precisely";
 *
 * class DrawGlyphsGM : public skiagm::GM {
 * public:
 *     void onOnceBeforeDraw() override {
 *         fTypeface = ToolUtils::CreatePortableTypeface("serif", SkFontStyle());
 *         fFont = SkFont(fTypeface);
 *         fFont.setSubpixel(true);
 *         fFont.setSize(18);
 *         const size_t txtLen = strlen(gText);
 *         fGlyphCount = fFont.countText(gText, txtLen, SkTextEncoding::kUTF8);
 *
 *         fGlyphs.append(fGlyphCount);
 *         fFont.textToGlyphs(gText, txtLen, SkTextEncoding::kUTF8, fGlyphs);
 *
 *         fPositions.append(fGlyphCount);
 *         fFont.getPos(fGlyphs, fPositions);
 *         auto positions = SkSpan(fPositions.begin(), fGlyphCount);
 *
 *         fLength = positions.back().x() - positions.front().x();
 *         fRadius = fLength / SK_FloatPI;
 *         fXforms.append(fGlyphCount);
 *
 *         for (auto [xform, pos] : SkMakeZip(fXforms.begin(), positions)) {
 *             const SkScalar lengthToGlyph = pos.x() - positions.front().x();
 *             const SkScalar angle = SK_FloatPI * (fLength - lengthToGlyph) / fLength;
 *             const SkScalar cos = std::cos(angle);
 *             const SkScalar sin = std::sin(angle);
 *             xform = SkRSXform::Make(sin, cos, fRadius*cos, -fRadius*sin);
 *         }
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkSpan<const SkGlyphID> glyphs = {fGlyphs.data(), (size_t)fGlyphCount};
 *         SkSpan<SkPoint> pos = {fPositions.data(), (size_t)fGlyphCount};
 *         canvas->drawGlyphs(glyphs, pos, {50, 100}, fFont, SkPaint{});
 *
 *         canvas->drawGlyphs(glyphs, pos, {50, 120}, fFont, SkPaint{});
 *
 *         // Check bounding box calculation.
 *         for (auto& p : fPositions) {
 *             p += {0, -500};
 *         }
 *         canvas->drawGlyphs(glyphs, pos, {50, 640}, fFont, SkPaint{});
 *
 *         canvas->drawGlyphsRSXform(fGlyphs, fXforms,
 *                            {50 + fLength / 2, 160 + fRadius}, fFont, SkPaint{});
 *     }
 *     // ...
 * };
 * ```
 *
 * **kanvas-skia adaptations** :
 *  - There is no [SkCanvas] `drawGlyphs` overload. We emulate it via
 *    [SkTextBlobBuilder.allocRunPos] — a `FullPositions` run with
 *    per-glyph `(x, y)` carries the same data as `drawGlyphs(glyphs,
 *    pos, origin, …)`, and [SkCanvas.drawTextBlob] adds the origin
 *    at draw time (matching upstream's `+ origin` semantics).
 *  - [SkCanvas] also has no `drawGlyphsRSXform`. We emulate by
 *    walking each glyph's [SkFont.getPath] outline, then issuing
 *    [SkCanvas.save] / [SkCanvas.concat] / [SkCanvas.drawPath] /
 *    [SkCanvas.restore] under the [SkRSXform] expressed as an
 *    [SkMatrix] (`scos, -ssin, tx ; ssin, scos, ty`). The visible
 *    result is the same — same outlines, same scale (= 1 here), same
 *    rotation, same translation.
 *  - Glyph-ID lookup (upstream's `countText` / `textToGlyphs`) is
 *    open-coded on top of [SkFont.unicharsToGlyphs] — one glyph per
 *    Unicode code point.
 *  - Per-glyph advance positions (upstream's `font.getPos(...)`) are
 *    derived from [SkFont.getWidth] cumulative sums on a constant
 *    baseline `y = 0` (matches Skia's default `getPos`).
 */
public class DrawGlyphsGM : GM() {

    private lateinit var fTypeface: SkTypeface
    private lateinit var fFont: SkFont
    private lateinit var fGlyphs: IntArray
    private lateinit var fPositions: FloatArray  // interleaved [x0, y0, x1, y1, ...]
    private lateinit var fXforms: Array<SkRSXform>
    private var fGlyphCount: Int = 0
    private var fRadius: Float = 0f
    private var fLength: Float = 0f

    override fun getName(): String = "drawglyphs"
    override fun getISize(): SkISize = SkISize.Make(640, 480)

    override fun onOnceBeforeDraw() {
        fTypeface = ToolUtils.CreatePortableTypeface("serif", SkFontStyle())
        fFont = SkFont(fTypeface).apply {
            isSubpixel = true
            size = 18f
        }

        // Open-code `countText` / `textToGlyphs`: one glyph per Unicode
        // code point (matches Skia's UTF-8 path on a string with no
        // ligature substitutions).
        val codepoints = gText.codePoints().toArray()
        fGlyphCount = codepoints.size
        val glyphsShort = ShortArray(fGlyphCount)
        fFont.unicharsToGlyphs(codepoints, fGlyphCount, glyphsShort)
        fGlyphs = IntArray(fGlyphCount) { glyphsShort[it].toInt() and 0xFFFF }

        // `font.getPos(glyphs, positions)`: positions[0] = (0, 0), then
        // each subsequent position is the previous plus the previous
        // glyph's advance width (matches Skia's default getPos when no
        // origin is supplied).
        fPositions = FloatArray(fGlyphCount * 2)
        var advance = 0f
        for (i in 0 until fGlyphCount) {
            fPositions[i * 2] = advance
            fPositions[i * 2 + 1] = 0f
            advance += fFont.getWidth(fGlyphs[i])
        }

        fLength = if (fGlyphCount >= 2) {
            fPositions[(fGlyphCount - 1) * 2] - fPositions[0]
        } else {
            0f
        }
        fRadius = fLength / PI.toFloat()

        fXforms = Array(fGlyphCount) { i ->
            val lengthToGlyph = fPositions[i * 2] - fPositions[0]
            val angle = PI.toFloat() * (fLength - lengthToGlyph) / fLength
            val cosA = cos(angle)
            val sinA = sin(angle)
            SkRSXform.Make(sinA, cosA, fRadius * cosA, -fRadius * sinA)
        }
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Three `drawGlyphs` calls via SkTextBlobBuilder.allocRunPos —
        // the FullPositions run + drawTextBlob's origin offset matches
        // upstream's `drawGlyphs(glyphs, pos, origin, font, paint)`.
        drawGlyphs(c, fPositions, 50f, 100f)
        drawGlyphs(c, fPositions, 50f, 120f)

        // Shift positions by (0, -500) — exercises bounding-box calc upstream.
        val shifted = FloatArray(fPositions.size) { idx ->
            if (idx and 1 == 0) fPositions[idx] else fPositions[idx] - 500f
        }
        drawGlyphs(c, shifted, 50f, 640f)

        // `drawGlyphsRSXform` emulation : per-glyph save / concat /
        // drawPath / restore, anchored at `origin = (50 + length/2,
        // 160 + radius)`.
        val originX = 50f + fLength / 2f
        val originY = 160f + fRadius
        val paint = SkPaint()
        for (i in 0 until fGlyphCount) {
            val gid = fGlyphs[i]
            val path = fFont.getPath(gid) ?: continue
            val xf = fXforms[i]
            // SkRSXform: (scos, ssin, tx, ty) — corresponding matrix is
            // [ scos  -ssin   tx ]
            // [ ssin   scos   ty ]
            // [   0      0     1 ]
            // Then the glyph is anchored at `origin + (tx, ty)`.
            val m = SkMatrix.MakeAll(
                xf.fSCos, -xf.fSSin, originX + xf.fTx,
                xf.fSSin, xf.fSCos, originY + xf.fTy,
                0f, 0f, 1f,
            )
            c.save()
            c.concat(m)
            c.drawPath(path, paint)
            c.restore()
        }
    }

    private fun drawGlyphs(c: SkCanvas, positions: FloatArray, originX: Float, originY: Float) {
        val builder = SkTextBlobBuilder()
        val rec = builder.allocRunPos(fFont, fGlyphCount)
        for (i in 0 until fGlyphCount) {
            rec.glyphs[i] = fGlyphs[i]
        }
        for (i in positions.indices) {
            rec.pos[i] = positions[i]
        }
        val blob: SkTextBlob = builder.make() ?: return
        c.drawTextBlob(blob, originX, originY, SkPaint())
    }

    private companion object {
        const val gText: String = "Call me Ishmael. Some years ago—never mind how long precisely"
    }
}
