package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextBlob
import org.skia.tools.ToolUtils

/**
 * Port of upstream Skia's `gm/fontregen.cpp::BadAppleGM`
 * (`DEF_GM(return new BadAppleGM())`, kSize × kSize where kSize = 512).
 *
 * Despite the cute name, this is **not** the
 * shadertoy-style Bad Apple animation — upstream's BadAppleGM is a
 * sibling of `FontRegenGM` that lives in the same `.cpp` file. It
 * draws two big antialiased subpixel-positioned strings (`"Meet"` at
 * 256pt and `"iPad Pro"` at 256pt) on a white background : two
 * large-glyph blobs, no atlas-stress prologue, no GPU flush bracket.
 * The name is a nod to the historical regression context (a Bad Apple
 * for the GPU glyph pipeline) but the visible GM is just text.
 *
 * Note : the placeholder body that previously lived here assumed the
 * upstream cpp was a path-tessellator torture-test sourced from
 * `gm/bad_apple/` (a separate binary asset). That cpp does **not**
 * exist in the upstream tree — `gm/fontregen.cpp` is the only home for
 * `BadAppleGM`, and the rendered output is purely text. The disabled
 * `STUB.PATH_TORTURE_ASSET` reason in `BadAppleTest` was therefore
 * incorrect ; replaced here.
 *
 * Raster reality
 * --------------
 * `SkFont::Edging::kSubpixelAntiAlias` is silently downgraded to
 * `kAntiAlias` by `:cpu-raster`'s glyph pipeline
 * (`MIGRATION_PLAN_TEXT.md §R3` — AWT's outline-based rasteriser has no
 * subpixel-positioning logic). At 256pt the visual delta vs. upstream's
 * LCD-stripe-aware reference is essentially nil — large glyph edges
 * dominate over the per-channel positioning offset. We keep the
 * `kSubpixelAntiAlias` request on the [SkFont] verbatim to keep the
 * port faithful and so that a future LCD pipeline picks it up
 * automatically.
 *
 * C++ original
 * ------------
 * ```cpp
 * void onOnceBeforeDraw() override {
 *     this->setBGColor(SK_ColorWHITE);
 *     auto fm = ToolUtils::TestFontMgr();
 *     static const SkString kTexts[] = { "Meet", "iPad Pro" };
 *     SkFont font = ToolUtils::DefaultPortableFont();
 *     font.setEdging(SkFont::Edging::kSubpixelAntiAlias);
 *     font.setSubpixel(true);
 *     font.setSize(256);
 *     fBlobs[0] = make_blob(kTexts[0], font);
 *     fBlobs[1] = make_blob(kTexts[1], font);
 * }
 *
 * void onDraw(SkCanvas* canvas) override {
 *     SkPaint paint; paint.setColor(0xFF111111);
 *     canvas->drawTextBlob(fBlobs[0], 10, 260, paint);
 *     canvas->drawTextBlob(fBlobs[1], 10, 500, paint);
 * }
 * ```
 *
 * `make_blob` is the same `MakeFromPosTextH(text, len, pos, 0, font)`
 * factory used by [FontRegenGM] ; we share [makePosTextHBlob] (declared
 * in `FontRegenGM.kt`) to avoid duplication.
 *
 * `ToolUtils::TestFontMgr()` is fetched upstream but never used in the
 * draw — only `DefaultPortableFont` (which itself goes through
 * `LiberationFontMgr` in our port) is consumed. We drop the dead
 * fetch.
 */
public class BadAppleGM : GM() {

    // Background already defaults to SK_ColorWHITE in GM; mirror the
    // upstream explicit `setBGColor` for documentation parity.
    init { /* setBGColor(SK_ColorWHITE) — default */ }

    override fun getName(): String = "badapple"
    override fun getISize(): SkISize = SkISize.Make(kSize, kSize)

    private lateinit var fBlobs: Array<SkTextBlob>

    override fun onOnceBeforeDraw() {
        val font = ToolUtils.DefaultPortableFont().apply {
            edging = SkFont.Edging.kSubpixelAntiAlias
            isSubpixel = true
            size = 256f
        }

        val texts = arrayOf("Meet", "iPad Pro")
        fBlobs = arrayOf(
            makePosTextHBlob(texts[0], font),
            makePosTextHBlob(texts[1], font),
        )
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply { color = 0xFF111111.toInt() }
        c.drawTextBlob(fBlobs[0], 10f, 260f, paint)
        c.drawTextBlob(fBlobs[1], 10f, 500f, paint)
    }

    private companion object {
        const val kSize: Int = 512
    }
}
