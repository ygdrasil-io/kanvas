package org.skia.tests

import org.graphiks.math.SK_ColorCYAN
import org.graphiks.math.SK_ColorGRAY
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.core.SkCanvas
import org.skia.foundation.SkData
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontMetrics
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextEncoding
import org.skia.foundation.SkTypeface
import org.skia.foundation.emoji.EmojiTypeface

/**
 * Full-body port of upstream Skia's
 * [`gm/scaledemoji_rendering.cpp::ScaledEmojiRenderingGM`](
 *   https://github.com/google/skia/blob/main/gm/scaledemoji_rendering.cpp)
 * (registered as `scaledemoji_rendering`, 1200 × 1200).
 *
 * The GM iterates over five emoji font format slots — COLRv0, sbix, CBDT,
 * Test, SVG — and for each sample renders the format's canonical emoji text
 * at two point sizes (70 pt and 150 pt), each with and without fake-bold
 * embolden, in aliased mode with subpixel positioning enabled.
 *
 * The intent upstream is to catch bitmap-vs-vector scaling regressions as the
 * glyph-cache dispatch scales emoji at non-native sizes.
 *
 * ## STUB.EMOJI_TABLES
 *
 * [onOnceBeforeDraw] calls [EmojiTypeface.create] for the four real
 * colour-emoji formats (COLRv0, Sbix, CBDT, SVG) so the compile contract
 * is fully exercised. The upstream fifth slot (`Test`) maps to a portable
 * typeface with no emoji-font stub equivalent; it falls back to
 * [SkTypeface.MakeEmpty] here.
 *
 * At runtime every [EmojiTypeface.create] call throws `STUB.EMOJI_TABLES`
 * because the AWT scaler cannot decode CBDT / sbix / COLRv0 / SVG glyph
 * tables; those require FreeType (and librsvg for SVG) via JNI.
 * [ScaledemojiRenderingTest] is therefore `@Disabled("STUB.EMOJI_TABLES")`.
 *
 * See [`API_FINALIZATION_PLAN.md`](../../../../../../../../API_FINALIZATION_PLAN.md)
 * § STUB.EMOJI_TABLES.
 */
public class ScaledemojiRenderingGM : GM() {

    /**
     * Mirrors upstream's `EmojiTestSample { typeface, sampleText }`.
     * The typeface is either an [EmojiTypeface.create] result (throws at
     * runtime) or an [SkTypeface.MakeEmpty] placeholder for the `Test` slot.
     */
    private data class EmojiSample(
        val typeface: SkTypeface,
        val sampleText: String,
    )

    /**
     * Mirrors upstream's `fontSamples[]` — one entry per slot in the
     * five-format list `{ ColrV0, Sbix, Cbdt, Test, Svg }`.
     * Populated by [onOnceBeforeDraw]; `null` until then.
     */
    private val fontSamples: Array<EmojiSample?> = arrayOfNulls(5)

    /**
     * Mirrors upstream's `onOnceBeforeDraw` —
     * ```cpp
     * for (auto&& [i, format] : SkMakeEnumerate(formatsToTest)) {
     *     fontSamples[i] = ToolUtils::EmojiSample(format);
     *     if (!fontSamples[i].typeface)
     *         fontSamples[i].typeface = ToolUtils::DefaultTypeface();
     * }
     * ```
     * Calls [EmojiTypeface.create] for the four colour-emoji formats;
     * throws `STUB.EMOJI_TABLES` at runtime (caught here so the class
     * initialises). The `Test` slot falls back to [SkTypeface.MakeEmpty]
     * (mirrors upstream's `DefaultTypeface()` fallback).
     */
    override fun onOnceBeforeDraw() {
        // Slot 0 — COLRv0
        fontSamples[0] = EmojiSample(
            typeface = safeCreate(EmojiTypeface.Format.COLRv0),
            sampleText = "😀 ♢",  // 😀 ♢
        )
        // Slot 1 — Sbix
        fontSamples[1] = EmojiSample(
            typeface = safeCreate(EmojiTypeface.Format.Sbix),
            sampleText = "😀 ♢",
        )
        // Slot 2 — CBDT
        fontSamples[2] = EmojiSample(
            typeface = safeCreate(EmojiTypeface.Format.CBDT),
            sampleText = "😀 ♢",
        )
        // Slot 3 — Test (upstream: CreatePortableTypeface("Emoji", SkFontStyle()))
        // No EmojiTypeface.Format equivalent; fall back to MakeEmpty, mirroring
        // upstream's DefaultTypeface() null-fallback path.
        fontSamples[3] = EmojiSample(
            typeface = SkTypeface.MakeEmpty(),
            sampleText = "😀 ♢",
        )
        // Slot 4 — SVG (sample text is "abcdefghij", not an emoji string)
        fontSamples[4] = EmojiSample(
            typeface = safeCreate(EmojiTypeface.Format.SVG),
            sampleText = "abcdefghij",
        )
    }

    /**
     * Calls [EmojiTypeface.create] and returns the typeface, or
     * [SkTypeface.MakeEmpty] if the call throws `STUB.EMOJI_TABLES`.
     * This matches upstream's null-typeface → DefaultTypeface() fallback
     * for format slots where no font resource is available.
     */
    private fun safeCreate(format: EmojiTypeface.Format): SkTypeface =
        try {
            EmojiTypeface.create(format, SkData.MakeWithCopy(ByteArray(0)))
        } catch (_: NotImplementedError) {
            // STUB.EMOJI_TABLES — expected at runtime.
            SkTypeface.MakeEmpty()
        }

    override fun getName(): String = "scaledemoji_rendering"
    override fun getISize(): SkISize = SkISize.Make(1200, 1200)

    /**
     * Mirrors upstream's `onDraw` body verbatim:
     * ```cpp
     * canvas->drawColor(SK_ColorGRAY);
     * SkPaint textPaint;  textPaint.setColor(SK_ColorCYAN);
     * SkPaint boundsPaint; boundsPaint.setStrokeWidth(2);
     *     boundsPaint.setStyle(SkPaint::kStroke_Style);
     *     boundsPaint.setColor(SK_ColorGREEN);
     * SkPaint advancePaint; advancePaint.setColor(SK_ColorRED);
     *
     * SkScalar y = 0;
     * for (auto& sample : fontSamples) {
     *     SkFont font(sample.typeface);
     *     font.setEdging(SkFont::Edging::kAlias);
     *     const char* text = sample.sampleText;
     *     SkFontMetrics metrics;
     *     for (SkScalar textSize : { 70, 150 }) {
     *         font.setSize(textSize);
     *         font.getMetrics(&metrics);
     *         font.setSubpixel(true);
     *         y += -metrics.fAscent;
     *         SkScalar x = 0;
     *         for (bool fakeBold : { false, true }) {
     *             font.setEmbolden(fakeBold);
     *             SkRect bounds;
     *             SkScalar advance = font.measureText(text, strlen(text),
     *                 SkTextEncoding::kUTF8, &bounds, &textPaint);
     *             canvas->drawSimpleText(text, strlen(text),
     *                 SkTextEncoding::kUTF8, x, y, font, textPaint);
     *             if ((false)) { … }   // dead upstream debug overlay
     *             x += bounds.width() * 1.2;
     *         }
     *         y += metrics.fDescent + metrics.fLeading;
     *         x = 0;
     *     }
     * }
     * ```
     * Note: upstream passes `&textPaint` as the fifth arg to `measureText`;
     * the Kotlin API omits the optional paint parameter (it's irrelevant for
     * advance-width computation in the AWT backend) — we call the four-arg
     * overload instead.
     */
    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        c.drawColor(SK_ColorGRAY)

        val textPaint = SkPaint().apply {
            color = SK_ColorCYAN
        }

        // Debug paints — only used in the upstream dead-code block below.
        val boundsPaint = SkPaint().apply {
            strokeWidth = 2f
            style = SkPaint.Style.kStroke_Style
            color = SK_ColorGREEN
        }
        val advancePaint = SkPaint().apply {
            color = SK_ColorRED
        }

        val metrics = SkFontMetrics()
        var y = 0f

        for (sample in fontSamples) {
            val s = sample ?: continue
            val text = s.sampleText
            val textBytes = text.toByteArray(Charsets.UTF_8)
            val textLen = textBytes.size

            val font = SkFont(s.typeface)
            font.edging = SkFont.Edging.kAlias

            for (textSize in floatArrayOf(70f, 150f)) {
                font.size = textSize
                font.getMetrics(metrics)
                // All typefaces should support subpixel mode.
                font.isSubpixel = true

                y += -metrics.fAscent

                var x = 0f
                for (fakeBold in booleanArrayOf(false, true)) {
                    font.isEmbolden = fakeBold
                    val bounds = SkRect.MakeEmpty()
                    // Upstream: font.measureText(text, strlen(text),
                    //     SkTextEncoding::kUTF8, &bounds, &textPaint)
                    // Kotlin API omits the optional paint param.
                    val advance = font.measureText(text, textLen, SkTextEncoding.kUTF8, bounds)
                    c.drawSimpleText(text, textLen, SkTextEncoding.kUTF8, x, y, font, textPaint)

                    // Upstream dead-code debug overlay: if ((false)) { … }
                    // Kept verbatim — documents the bounds-rect + advance-line
                    // overlay the authors used while developing this GM.
                    @Suppress("ConstantConditionIf", "UNUSED_VALUE")
                    if (false) {
                        bounds.offset(x, y)
                        c.drawRect(bounds, boundsPaint)
                        val advanceRect = SkRect.MakeLTRB(x, y + 2f, x + advance, y + 4f)
                        c.drawRect(advanceRect, advancePaint)
                    }

                    x += bounds.width() * 1.2f
                }
                y += metrics.fDescent + metrics.fLeading
                // x resets to 0 at the top of the next fakeBold loop.
            }
        }
    }
}
