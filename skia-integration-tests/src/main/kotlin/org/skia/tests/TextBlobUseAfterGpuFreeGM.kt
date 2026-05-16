package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextBlob
import org.skia.foundation.SkTextBlobBuilder
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/textblobuseaftergpufree.cpp::TextBlobUseAfterGpuFree`.
 *
 * Regression test for `crbug/491350`. Originally checked that an
 * `SkTextBlob` continued to render correctly after the GPU resource
 * cache was freed between two `drawTextBlob` calls. On the raster
 * backend there is no GPU cache to free, so the two `drawTextBlob`
 * calls simply render the same blob twice ; the SK_GANESH branch
 * (`dContext->freeGpuResources()`) is a no-op.
 *
 * C++ original:
 * ```cpp
 * void onDraw(SkCanvas* canvas) override {
 *     const char text[] = "Hamburgefons";
 *
 *     SkFont font(ToolUtils::DefaultPortableTypeface(), 20);
 *     auto blob = SkTextBlob::MakeFromText(text, strlen(text), font);
 *
 *     // draw textblob
 *     SkRect rect = SkRect::MakeLTRB(0.f, 0.f, SkIntToScalar(kWidth), kHeight / 2.f);
 *     SkPaint rectPaint;
 *     rectPaint.setColor(0xffffffff);
 *     canvas->drawRect(rect, rectPaint);
 *     canvas->drawTextBlob(blob, 20, 60, SkPaint());
 *
 * #if defined(SK_GANESH)
 *     // This text should look fine
 *     if (auto dContext = GrAsDirectContext(canvas->recordingContext())) {
 *         dContext->freeGpuResources();
 *     }
 * #endif
 *
 *     canvas->drawTextBlob(blob, 20, 160, SkPaint());
 * }
 * ```
 *
 * `:kanvas-skia` doesn't carry a `SkTextBlob::MakeFromText` factory ;
 * we reproduce its single-`allocRun` build via [ToolUtils.addToTextBlob],
 * mirroring upstream's `add_to_text_blob` helper (the same pattern
 * used by [Skbug8955GM] and the other text-blob GM ports).
 */
public class TextBlobUseAfterGpuFreeGM : GM() {

    override fun getName(): String = "textblobuseaftergpufree"
    override fun getISize(): SkISize = SkISize.Make(kWidth, kHeight)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val text = "Hamburgefons"

        val font = SkFont(ToolUtils.DefaultPortableTypeface(), 20f)

        // Mirror `SkTextBlob::MakeFromText(text, strlen(text), font)`
        // via the established `ToolUtils.addToTextBlob` helper.
        val builder = SkTextBlobBuilder()
        ToolUtils.addToTextBlob(builder, text, font, 0f, 0f)
        val blob: SkTextBlob = builder.make() ?: return

        // White rect over the top half of the canvas.
        val rect = SkRect.MakeLTRB(0f, 0f, kWidth.toFloat(), kHeight / 2f)
        val rectPaint = SkPaint().apply { color = 0xffffffff.toInt() }
        c.drawRect(rect, rectPaint)
        c.drawTextBlob(blob, 20f, 60f, SkPaint())

        // SK_GANESH branch is a no-op on raster — the second draw is
        // an identical render of the same blob at a different y.
        c.drawTextBlob(blob, 20f, 160f, SkPaint())
    }

    private companion object {
        const val kWidth: Int = 200
        const val kHeight: Int = 200
    }
}
