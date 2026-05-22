package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorLTGRAY
import org.graphiks.math.SkISize
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextBlob
import org.skia.foundation.SkTextBlobBuilder
import org.skia.tools.ToolUtils

/**
 * Port of upstream Skia's `gm/fontregen.cpp::FontRegenGM`
 * (`DEF_GM(return new FontRegenGM())`, kSize × kSize where kSize = 512).
 *
 * Upstream purpose
 * ----------------
 * GPU-side stress test for the glyph atlas regeneration path. The
 * `modifyGrContextOptions` / `modifyGraphiteContextOptions` overrides
 * shrink the atlas to 0 bytes and disable multi-texture growth, then the
 * draw sequence floods the atlas with large glyphs, calls `flushAndSubmit`,
 * and re-emits the original small-glyph run to confirm the atlas
 * re-uploads the evicted Plot rather than smearing.
 *
 * The post-flush re-draw colour is `0xFF010101` instead of `SK_ColorBLACK`
 * so that an atlas-cache hit (mistakenly reused colour-quantised mask)
 * shows up as a 1-bit pixel diff against the upstream reference.
 *
 * Raster reality
 * --------------
 * `:cpu-raster` has no glyph atlas — `SkCanvas.drawTextBlob` materialises
 * each glyph through [org.skia.foundation.SkTypeface.getPath] every call
 * and fills the resulting path. There is nothing to evict, nothing to
 * regenerate, and the `flushAndSubmit` GPU bracket is a no-op. We mirror
 * the *visible* draw sequence exactly so the rendered output still
 * matches the upstream reference PNG (which itself was captured with the
 * atlas behaving correctly — i.e. identical to no-atlas raster).
 *
 * The 1-bit colour delta `0xFF010101` vs `SK_ColorBLACK` is intentional
 * — kept verbatim from upstream because the reference PNG carries it.
 *
 * C++ original (excerpt)
 * ----------------------
 * ```cpp
 * void onOnceBeforeDraw() override {
 *     this->setBGColor(SK_ColorLTGRAY);
 *     auto tf = ToolUtils::CreatePortableTypeface("sans-serif", SkFontStyle::Normal());
 *     static const SkString kTexts[] = { "abc…xyz", "ABCDEFGHI", "NOPQRSTUV" };
 *     SkFont font; font.setEdging(kAntiAlias); font.setSubpixel(false);
 *     font.setSize(80);  font.setTypeface(tf);
 *     fBlobs[0] = make_blob(kTexts[0], font);
 *     font.setSize(162);
 *     fBlobs[1] = make_blob(kTexts[1], font);
 *     fBlobs[2] = make_blob(kTexts[2], font);
 * }
 *
 * DrawResult onDraw(SkCanvas* canvas, SkString*) override {
 *     SkPaint paint; paint.setColor(SK_ColorBLACK);
 *     canvas->drawTextBlob(fBlobs[0], 10, 80, paint);
 *     canvas->drawTextBlob(fBlobs[1], 10, 225, paint);
 *     // GPU flushAndSubmit() — no-op on raster
 *     paint.setColor(0xFF010101);
 *     canvas->drawTextBlob(fBlobs[0], 10, 305, paint);
 *     canvas->drawTextBlob(fBlobs[2], 10, 465, paint);
 *     return DrawResult::kOk;
 * }
 * ```
 *
 * Upstream's `make_blob` uses `SkTextBlob::MakeFromPosTextH(text, len, pos, 0, font)`
 * — a per-glyph X array with constant baseline 0 (the blob's `y` argument
 * supplies the baseline at draw time). We reproduce that via
 * [SkTextBlobBuilder.allocRunPosH] driven by [SkFont.textToGlyphs] +
 * [SkFont.getXPos].
 */
public class FontRegenGM : GM() {

    init { setBGColor(SK_ColorLTGRAY) }

    override fun getName(): String = "fontregen"
    override fun getISize(): SkISize = SkISize.Make(kSize, kSize)

    private lateinit var fBlobs: Array<SkTextBlob>

    override fun onOnceBeforeDraw() {
        val tf = ToolUtils.CreatePortableTypeface("sans-serif", SkFontStyle.Normal())

        val texts = arrayOf(
            "abcdefghijklmnopqrstuvwxyz",
            "ABCDEFGHI",
            "NOPQRSTUV",
        )

        val font = SkFont(tf).apply {
            edging = SkFont.Edging.kAntiAlias
            isSubpixel = false
            size = 80f
        }

        val b0 = makePosTextHBlob(texts[0], font)
        font.size = 162f
        val b1 = makePosTextHBlob(texts[1], font)
        val b2 = makePosTextHBlob(texts[2], font)
        fBlobs = arrayOf(b0, b1, b2)
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val paint = SkPaint().apply { color = SK_ColorBLACK }
        c.drawTextBlob(fBlobs[0], 10f, 80f, paint)
        c.drawTextBlob(fBlobs[1], 10f, 225f, paint)

        // Upstream `dContext->flushAndSubmit()` lives here, gated on
        // `SK_GANESH`. Raster has no recording context, so the bracket
        // collapses to a no-op — the next two drawTextBlob calls hit the
        // same path-fill pipeline as the previous two.

        paint.color = 0xFF010101.toInt()
        c.drawTextBlob(fBlobs[0], 10f, 305f, paint)
        c.drawTextBlob(fBlobs[2], 10f, 465f, paint)
    }

    private companion object {
        const val kSize: Int = 512
    }
}

/**
 * Shared `make_blob` helper between [FontRegenGM] and [BadAppleGM] —
 * mirrors upstream's
 * ```cpp
 * static sk_sp<SkTextBlob> make_blob(const SkString& text, const SkFont& font) {
 *     size_t len = text.size();
 *     AutoTArray<SkScalar>  pos(len);
 *     AutoTArray<SkGlyphID> glyphs(len);
 *     font.textToGlyphs(text.c_str(), len, SkTextEncoding::kUTF8, glyphs);
 *     font.getXPos(glyphs, pos);
 *     return SkTextBlob::MakeFromPosTextH(text.c_str(), len, pos, 0, font);
 * }
 * ```
 *
 * `SkTextBlob::MakeFromPosTextH(text, len, xpos, constY, font)` is the
 * upstream factory for "one run, per-glyph X, shared baseline Y" —
 * structurally identical to [SkTextBlobBuilder.allocRunPosH] under the
 * hood. `constY = 0` here matches upstream ; the blob's draw-time `y`
 * supplies the actual baseline.
 */
internal fun makePosTextHBlob(text: String, font: SkFont): SkTextBlob {
    val glyphs = font.textToGlyphs(text)
    val xpos = font.getXPos(glyphs, origin = 0f)

    val builder = SkTextBlobBuilder()
    val rec = builder.allocRunPosH(font, glyphs.size, y = 0f)
    for (i in glyphs.indices) {
        rec.glyphs[i] = glyphs[i] and 0xFFFF
        rec.pos[i] = xpos[i]
    }
    return builder.make()!!
}
