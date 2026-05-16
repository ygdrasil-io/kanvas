package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.withLayer
import org.skia.core.withSave
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextEncoding
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/annotated_text.cpp::annotated_text`.
 *
 * **Validates a previously untested combination**: text rendered
 * after a `rotate(90)` CTM transform inside a `saveLayer`, with
 * `clipRect` constraining the visible area. Earlier text GMs
 * exercised text in identity-CTM contexts; this is the first to
 * stress the full chain `saveLayer` → `rotate` → `clipRect` →
 * `drawString` against a real reference.
 *
 * The upstream GM also calls `SkAnnotateRectWithURL` — a PDF/annotation
 * metadata API that is **a no-op on raster backends**. We don't ship
 * `SkAnnotation` and skip the call entirely; the rendered pixels are
 * unaffected (only PDF/SkPicture pickups would care).
 *
 * 512 × 512 canvas, two `Click this link!` strings (one upright at
 * `(200, 80)`, one rotated 90° via the layered subcanvas) over
 * translucent slate-blue rects (`0x80346180`) inside a grey clipped
 * area (`0xFFEEEEEE`).
 *
 * C++ original:
 * ```cpp
 * static void draw_url_annotated_text_with_box(
 *         SkCanvas* canvas, const void* text,
 *         SkScalar x, SkScalar y, const SkFont& font, const char* url) {
 *     size_t byteLength = strlen(static_cast<const char*>(text));
 *     SkRect bounds;
 *     (void)font.measureText(text, byteLength, SkTextEncoding::kUTF8, &bounds);
 *     bounds.offset(x, y);
 *     sk_sp<SkData> urlData(SkData::MakeWithCString(url));
 *     SkAnnotateRectWithURL(canvas, bounds, urlData.get());          // no-op on raster
 *     SkPaint shade;
 *     shade.setColor(0x80346180);
 *     canvas->drawRect(bounds, shade);
 *     canvas->drawSimpleText(text, byteLength, SkTextEncoding::kUTF8, x, y, font, SkPaint());
 * }
 *
 * DEF_SIMPLE_GM(annotated_text, canvas, 512, 512) {
 *     SkAutoCanvasRestore autoCanvasRestore(canvas, true);
 *     canvas->clear(SK_ColorWHITE);
 *     canvas->clipRect(SkRect::MakeXYWH(64, 64, 256, 256));
 *     canvas->clear(0xFFEEEEEE);
 *     SkFont font = ToolUtils::DefaultPortableFont();
 *     font.setEdging(SkFont::Edging::kAlias);
 *     font.setSize(40);
 *     const char text[] = "Click this link!";
 *     const char url[] = "https://www.google.com/";
 *     draw_url_annotated_text_with_box(canvas, text, 200.0f, 80.0f, font, url);
 *     canvas->saveLayer(nullptr, nullptr);
 *     canvas->rotate(90);
 *     draw_url_annotated_text_with_box(canvas, text, 150.0f, -55.0f, font, url);
 *     canvas->restore();
 * }
 * ```
 */
public class AnnotatedTextGM : GM() {
    override fun getName(): String = "annotated_text"
    override fun getISize(): SkISize = SkISize.Make(512, 512)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Iso with upstream `SkAutoCanvasRestore autoCanvasRestore(canvas, true)` — save once,
        // restore at function exit so clip / CTM mods don't leak past this GM.
        c.withSave {
            clear(SK_ColorWHITE)
            clipRect(SkRect.MakeXYWH(64f, 64f, 256f, 256f))
            clear(0xFFEEEEEE.toInt())

            val font = ToolUtils.DefaultPortableFont().apply {
                edging = SkFont.Edging.kAlias
                size = 40f
            }
            val text = "Click this link!"

            drawUrlAnnotatedTextWithBox(this, text, 200f, 80f, font)

            // Iso with upstream `canvas->saveLayer(nullptr, nullptr); … ; canvas->restore();`.
            withLayer {
                rotate(90f)
                drawUrlAnnotatedTextWithBox(this, text, 150f, -55f, font)
            }
        }
    }

    /**
     * Mirrors the C++ `draw_url_annotated_text_with_box` helper, minus
     * the `SkAnnotateRectWithURL` call (no-op on raster). Computes the
     * tight glyph bbox via [SkFont.measureText], paints a translucent
     * slate-blue rect under the text, then renders the text in
     * default-ctor `SkPaint()` (opaque black).
     */
    private fun drawUrlAnnotatedTextWithBox(
        canvas: SkCanvas,
        text: String,
        x: Float,
        y: Float,
        font: SkFont,
    ) {
        val byteLength = text.length
        val bounds = SkRect.MakeEmpty()
        font.measureText(text, byteLength, SkTextEncoding.kUTF8, bounds)
        bounds.offset(x, y)
        // SkAnnotateRectWithURL(canvas, bounds, urlData.get()) — no-op on raster.
        val shade = SkPaint(0x80346180.toInt())
        canvas.drawRect(bounds, shade)
        canvas.drawSimpleText(text, byteLength, SkTextEncoding.kUTF8, x, y, font, SkPaint())
    }
}
