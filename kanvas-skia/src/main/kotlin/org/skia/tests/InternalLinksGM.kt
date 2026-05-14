package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorBLACK
import org.skia.foundation.SK_ColorBLUE
import org.skia.foundation.SkAnnotateLinkToDestination
import org.skia.foundation.SkAnnotateNamedDestination
import org.skia.foundation.SkData
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/internal_links.cpp::InternalLinksGM` GM
 * (`internal_links`, 700 × 500, background grey `0xFFDDDDDD`).
 *
 * Validates the no-op behaviour of [SkAnnotateLinkToDestination] /
 * [SkAnnotateNamedDestination] on raster output : both calls are
 * recorded by [SkCanvas.drawAnnotation] but the raster sink simply
 * drops them. The visible output is the two blue rectangles + their
 * labels.
 *
 * Layout (700 × 500, light-grey background) :
 *  - "Link to A" panel at `(100, 100)` : 50×20 blue rect with the
 *    word "Link to A" drawn (in black, size-25 portable typeface) at
 *    the rect's top-left, with [SkAnnotateLinkToDestination] tagging
 *    the rect with name `"target-a"`. The annotation is a no-op
 *    here.
 *  - "Target A" panel at `(300, 250)` : same 50×20 blue rect (the
 *    `translate(200, 200)` is composed with the `drawLabeledRect`
 *    args of `(100, 50)` → `(300, 250)` in canvas space) with the
 *    word "Target A" black-rendered above ; [SkAnnotateNamedDestination]
 *    pins the same name at the rect's anchor point. Also a no-op.
 *
 * **Implementation note** : upstream uses `SkData::MakeWithCString`
 * which appends a NUL terminator. `:kanvas-skia` ships
 * [SkData.MakeWithCopy] only ; we manually append a trailing 0 byte
 * for byte-faithfulness with the upstream PDF backend (irrelevant
 * for raster output since [SkAnnotateLinkToDestination] is a no-op).
 *
 * C++ original :
 * ```cpp
 * void onOnceBeforeDraw() override { this->setBGColor(0xFFDDDDDD); }
 * SkString getName() const override { return SkString("internal_links"); }
 * SkISize getISize() override { return {700, 500}; }
 *
 * void onDraw(SkCanvas* canvas) override {
 *     sk_sp<SkData> name(SkData::MakeWithCString("target-a"));
 *
 *     canvas->save();
 *     canvas->translate(100, 100);
 *     drawLabeledRect(canvas, "Link to A", 0, 0);
 *     SkRect rect = SkRect::MakeXYWH(0, 0, 50, 20);
 *     SkAnnotateLinkToDestination(canvas, rect, name.get());
 *     canvas->restore();
 *
 *     canvas->save();
 *     canvas->translate(200, 200);
 *     SkPoint point = SkPoint::Make(100, 50);
 *     drawLabeledRect(canvas, "Target A", point.x(), point.y());
 *     SkAnnotateNamedDestination(canvas, point, name.get());
 *     canvas->restore();
 * }
 *
 * void drawLabeledRect(SkCanvas* canvas, const char* text,
 *                      SkScalar x, SkScalar y) {
 *     SkPaint paint;
 *     paint.setColor(SK_ColorBLUE);
 *     SkRect rect = SkRect::MakeXYWH(x, y, 50, 20);
 *     canvas->drawRect(rect, paint);
 *
 *     SkFont font(ToolUtils::DefaultPortableTypeface(), 25);
 *     paint.setColor(SK_ColorBLACK);
 *     canvas->drawString(text, x, y, font, paint);
 * }
 * ```
 */
public class InternalLinksGM : GM() {

    init {
        setBGColor(0xFFDDDDDD.toInt())
    }

    override fun getName(): String = "internal_links"

    override fun getISize(): SkISize = SkISize.Make(700, 500)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // `SkData::MakeWithCString("target-a")` → bytes + NUL.
        val nameBytes = "target-a".toByteArray(Charsets.US_ASCII) + 0.toByte()
        val name: SkData = SkData.MakeWithCopy(nameBytes)

        // Panel 1 — "Link to A" annotated rect.
        c.save()
        c.translate(100f, 100f)
        drawLabeledRect(c, "Link to A", 0f, 0f)
        val linkRect = SkRect.MakeXYWH(0f, 0f, 50f, 20f)
        SkAnnotateLinkToDestination(c, linkRect, name) // no-op on raster
        c.restore()

        // Panel 2 — "Target A" annotated point.
        c.save()
        c.translate(200f, 200f)
        val point = SkPoint.Make(100f, 50f)
        drawLabeledRect(c, "Target A", point.fX, point.fY)
        SkAnnotateNamedDestination(c, point, name) // no-op on raster
        c.restore()
    }

    /**
     * Mirrors the C++ `drawLabeledRect` helper. Blue 50×20 rect with
     * a black size-25 string drawn at `(x, y)` (the string baseline
     * sits at `y`, matching upstream — the text rises above the
     * rect's top edge for these GM coordinates).
     */
    private fun drawLabeledRect(canvas: SkCanvas, text: String, x: Float, y: Float) {
        val paint = SkPaint().apply { color = SK_ColorBLUE }
        val rect = SkRect.MakeXYWH(x, y, 50f, 20f)
        canvas.drawRect(rect, paint)

        val font = SkFont(ToolUtils.DefaultPortableTypeface(), 25f)
        paint.color = SK_ColorBLACK
        canvas.drawString(text, x, y, font, paint)
    }
}
