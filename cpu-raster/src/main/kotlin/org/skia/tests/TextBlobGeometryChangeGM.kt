package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SkFont
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextBlob
import org.skia.foundation.SkTextBlobBuilder
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/textblobgeometrychange.cpp::TextBlobGeometryChange`.
 *
 * Regression test for `crbug/486744`. Originally checked the GPU
 * textblob cache wouldn't reuse a blob across `SkPixelGeometry`
 * changes with LCD text. Both halves render the same blob ; the
 * upper half goes straight to the destination canvas, the lower
 * half goes through an offscreen [SkSurface] (with — upstream —
 * `kUnknown_SkPixelGeometry`, which forces LCD text off in the
 * offscreen surface) then back via `surface->draw(canvas, 0, 0)`.
 *
 * C++ original:
 * ```cpp
 * void onDraw(SkCanvas* canvas) override {
 *     const char text[] = "Hamburgefons";
 *
 *     SkFont font(ToolUtils::DefaultPortableTypeface(), 20);
 *     font.setEdging(SkFont::Edging::kSubpixelAntiAlias);
 *
 *     SkTextBlobBuilder builder;
 *
 *     ToolUtils::add_to_text_blob(&builder, text, font, 10, 10);
 *
 *     sk_sp<SkTextBlob> blob(builder.make());
 *
 *     SkImageInfo info = SkImageInfo::MakeN32Premul(200, 200);
 *     SkSurfaceProps props(0, kUnknown_SkPixelGeometry);
 *     auto           surface = ToolUtils::makeSurface(canvas, info, &props);
 *     SkCanvas* c = surface->getCanvas();
 *
 *     // LCD text on white background
 *     SkRect rect = SkRect::MakeLTRB(0.f, 0.f, SkIntToScalar(kWidth), kHeight / 2.f);
 *     SkPaint rectPaint;
 *     rectPaint.setColor(0xffffffff);
 *     canvas->drawRect(rect, rectPaint);
 *     canvas->drawTextBlob(blob, 10, 50, SkPaint());
 *
 *     // This should not look garbled since we should disable LCD text in this case
 *     // (i.e., unknown pixel geometry)
 *     c->clear(0x00ffffff);
 *     c->drawTextBlob(blob, 10, 150, SkPaint());
 *     surface->draw(canvas, 0, 0);
 * }
 * ```
 *
 * `:kanvas-skia` adaptations :
 *  - `kSubpixelAntiAlias` is silently downgraded to `kAntiAlias`
 *    (see [SkFont.Edging]), so LCD pixel-geometry handling is moot.
 *  - There is no `ToolUtils::makeSurface(canvas, info, props)` — we
 *    use a standalone [SkSurface.MakeRaster] (same idiom as
 *    [Crbug905548GM]).
 *  - `SkTextBlob::MakeFromText` is replaced with the canonical
 *    `ToolUtils.addToTextBlob` builder dance.
 */
public class TextBlobGeometryChangeGM : GM() {

    override fun getName(): String = "textblobgeometrychange"
    override fun getISize(): SkISize = SkISize.Make(kWidth, kHeight)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val text = "Hamburgefons"

        val font = SkFont(ToolUtils.DefaultPortableTypeface(), 20f).apply {
            edging = SkFont.Edging.kSubpixelAntiAlias
        }

        val builder = SkTextBlobBuilder()
        ToolUtils.addToTextBlob(builder, text, font, 10f, 10f)
        val blob: SkTextBlob = builder.make() ?: return

        // Offscreen surface — upstream uses `ToolUtils::makeSurface(canvas,
        // info, &props)` with `kUnknown_SkPixelGeometry` ; we substitute a
        // raster surface (kanvas-skia has a single pixel geometry).
        val info = SkImageInfo.MakeN32Premul(200, 200)
        val surface = SkSurface.MakeRaster(info)
        val sc: SkCanvas = surface.canvas

        // LCD text on white background — top half.
        val rect = SkRect.MakeLTRB(0f, 0f, kWidth.toFloat(), kHeight / 2f)
        val rectPaint = SkPaint().apply { color = 0xffffffff.toInt() }
        c.drawRect(rect, rectPaint)
        c.drawTextBlob(blob, 10f, 50f, SkPaint())

        // Offscreen — bottom half. Upstream clears with 0x00ffffff
        // (transparent white) ; the draw composites back through the
        // surface's image snapshot.
        sc.clear(0x00ffffff)
        sc.drawTextBlob(blob, 10f, 150f, SkPaint())
        surface.draw(c, 0f, 0f)
    }

    private companion object {
        const val kWidth: Int = 200
        const val kHeight: Int = 200
    }
}
