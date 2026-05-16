package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkBlurMaskFilter
import org.skia.foundation.SkBlurStyle
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextBlob
import org.skia.foundation.SkTextBlobBuilder
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.graphiks.math.SkScalar
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/clip_error.cpp::ClipErrorGM` (800 × 800).
 *
 * Ensures glyphs whose pre-blur bbox is too large for the atlas
 * still translate + clip correctly. Two stacked draws of the text
 * "hambur" at 256 px under a normal blur mask filter (sigma derived
 * from radius 50 via `SkBlurMask::ConvertRadiusToSigma`) then on top
 * un-blurred — each pair lives under a different translate + clipRect
 * pair so the upper and lower halves get distinct sub-regions.
 *
 * The helper [drawText] first clip-restricts to the inner rect
 * `(0, 0, 1081, 665)`, paints it white (via [SK_ColorWHITE] `clearPaint`),
 * then draws the blob blurred at baseline `y = 256` and un-blurred at
 * `y = 477`.
 *
 * C++ original:
 * ```cpp
 * #define WIDTH 800
 * #define HEIGHT 800
 *
 * static void draw_text(SkCanvas* canvas, sk_sp<SkTextBlob> blob,
 *                       const SkPaint& paint, const SkPaint& blurPaint,
 *                       const SkPaint& clearPaint) {
 *     canvas->save();
 *     canvas->clipRect(SkRect::MakeLTRB(0, 0, 1081, 665));
 *     canvas->drawRect(SkRect::MakeLTRB(0, 0, 1081, 665), clearPaint);
 *     canvas->drawTextBlob(blob, 0, 256, blurPaint);
 *     canvas->drawTextBlob(blob, 0, 477, paint);
 *     canvas->restore();
 * }
 *
 * class ClipErrorGM : public skiagm::GM {
 *     SkString getName() const override { return SkString("cliperror"); }
 *     SkISize getISize() override { return SkISize::Make(WIDTH, HEIGHT); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint paint;
 *         paint.setAntiAlias(true);
 *
 *         SkFont font(ToolUtils::DefaultPortableTypeface(), 256);
 *
 *         const SkScalar kSigma = SkBlurMask::ConvertRadiusToSigma(SkIntToScalar(50));
 *
 *         SkPaint blurPaint(paint);
 *         blurPaint.setMaskFilter(SkMaskFilter::MakeBlur(kNormal_SkBlurStyle, kSigma));
 *
 *         const char text[] = "hambur";
 *         auto blob = SkTextBlob::MakeFromText(text, strlen(text), font);
 *
 *         SkPaint clearPaint(paint);
 *         clearPaint.setColor(SK_ColorWHITE);
 *
 *         canvas->save();
 *         canvas->translate(0, 0);
 *         canvas->clipRect(SkRect::MakeLTRB(0, 0, WIDTH, 256));
 *         draw_text(canvas, blob, paint, blurPaint, clearPaint);
 *         canvas->restore();
 *
 *         canvas->save();
 *         canvas->translate(0, 256);
 *         canvas->clipRect(SkRect::MakeLTRB(0, 256, WIDTH, 510));
 *         draw_text(canvas, blob, paint, blurPaint, clearPaint);
 *         canvas->restore();
 *     }
 * };
 * DEF_GM(return new ClipErrorGM;)
 * ```
 */
public class ClipErrorGM : GM() {
    private companion object {
        const val WIDTH: Int = 800
        const val HEIGHT: Int = 800
    }

    override fun getName(): String = "cliperror"
    override fun getISize(): SkISize = SkISize.Make(WIDTH, HEIGHT)

    private fun drawText(
        canvas: SkCanvas,
        blob: SkTextBlob,
        paint: SkPaint,
        blurPaint: SkPaint,
        clearPaint: SkPaint,
    ) {
        canvas.save()
        canvas.clipRect(SkRect.MakeLTRB(0f, 0f, 1081f, 665f))
        canvas.drawRect(SkRect.MakeLTRB(0f, 0f, 1081f, 665f), clearPaint)
        // draw as blurred to push glyph to be too large for atlas
        canvas.drawTextBlob(blob, 0f, 256f, blurPaint)
        canvas.drawTextBlob(blob, 0f, 477f, paint)
        canvas.restore()
    }

    /** Mirrors `SkBlurMask::ConvertRadiusToSigma(radius)`. */
    private fun convertRadiusToSigma(radius: SkScalar): SkScalar =
        if (radius > 0f) 0.57735f * radius + 0.5f else 0f

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val paint = SkPaint()
        paint.isAntiAlias = true

        val font = SkFont(ToolUtils.DefaultPortableTypeface(), 256f)

        // setup up maskfilter
        val kSigma = convertRadiusToSigma(50f)

        val blurPaint = SkPaint().apply {
            isAntiAlias = true
            maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, kSigma)
        }

        val text = "hambur"
        val blob = makeBlobFromText(text, font) ?: return

        val clearPaint = SkPaint().apply {
            isAntiAlias = true
            color = SK_ColorWHITE
        }

        c.save()
        c.translate(0f, 0f)
        c.clipRect(SkRect.MakeLTRB(0f, 0f, WIDTH.toFloat(), 256f))
        drawText(c, blob, paint, blurPaint, clearPaint)
        c.restore()

        c.save()
        c.translate(0f, 256f)
        c.clipRect(SkRect.MakeLTRB(0f, 256f, WIDTH.toFloat(), 510f))
        drawText(c, blob, paint, blurPaint, clearPaint)
        c.restore()
    }

    /**
     * Mirrors `SkTextBlob::MakeFromText(text, strlen(text), font)`. Build
     * a single horizontal-spread run at origin `(0, 0)` — the canvas's
     * `drawTextBlob(blob, x, y, paint)` adds the per-call offset on top.
     */
    private fun makeBlobFromText(text: String, font: SkFont): SkTextBlob? {
        if (text.isEmpty()) return null
        val codePoints = text.codePoints().toArray()
        val n = codePoints.size
        val glyphsShort = ShortArray(n)
        font.unicharsToGlyphs(codePoints, n, glyphsShort)

        val builder = SkTextBlobBuilder()
        val rec = builder.allocRun(font, n, 0f, 0f)
        for (i in 0 until n) {
            rec.glyphs[i] = glyphsShort[i].toInt() and 0xFFFF
        }
        return builder.make()
    }
}
