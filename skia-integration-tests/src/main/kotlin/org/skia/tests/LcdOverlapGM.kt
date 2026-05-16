package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorCYAN
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorMAGENTA
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorYELLOW
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextBlob
import org.skia.foundation.SkTextBlobBuilder
import org.graphiks.math.SkISize
import org.graphiks.math.SkScalar
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/lcdoverlap.cpp::LcdOverlapGM` (750 × 750).
 *
 * Renders the palindrome "able was I ere I saw elba" six times in a
 * rotation around four pivot points, each pair using a different
 * [SkBlendMode] pairing alternating between odd-indexed and
 * even-indexed glyphs.
 *
 * Upstream sets `kSubpixelAntiAlias` edging and `setSubpixel(true)` on
 * the font ; the kanvas-skia [SkFont] silently downgrades the
 * subpixel-AA edging to plain antialiased (cf. `SkFont` doc) — the
 * structural content (rotations + colours + blend modes) is preserved.
 *
 * C++ original:
 * ```cpp
 * class LcdOverlapGM : public skiagm::GM {
 *     LcdOverlapGM() { fTextHeight = SkIntToScalar(25); }
 *     SkString getName() const override { return SkString("lcdoverlap"); }
 *     void onOnceBeforeDraw() override {
 *         SkTextBlobBuilder builder;
 *         SkFont font(ToolUtils::DefaultPortableTypeface(), 32);
 *         const char* text = "able was I ere I saw elba";
 *         font.setSubpixel(true);
 *         font.setEdging(SkFont::Edging::kSubpixelAntiAlias);
 *         ToolUtils::add_to_text_blob(&builder, text, font, 0, 0);
 *         fBlob = builder.make();
 *     }
 *     SkISize getISize() override { return SkISize::Make(kWidth, kHeight); }
 *
 *     void drawTestCase(SkCanvas* canvas, SkScalar x, SkScalar y,
 *                       SkBlendMode mode, SkBlendMode mode2) {
 *         const SkColor colors[] {RED, GREEN, BLUE, YELLOW, CYAN, MAGENTA};
 *         for (size_t i = 0; i < std::size(colors); i++) {
 *             canvas->save();
 *             canvas->translate(x, y);
 *             canvas->rotate(360.0f / std::size(colors) * i);
 *             canvas->translate(-fBlob->bounds().width() / 2.0f
 *                               - fBlob->bounds().left() + 0.5f, 0);
 *             SkPaint textPaint;
 *             textPaint.setColor(colors[i]);
 *             textPaint.setBlendMode(i % 2 == 0 ? mode : mode2);
 *             canvas->drawTextBlob(fBlob, 0, 0, textPaint);
 *             canvas->restore();
 *         }
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkScalar offsetX = kWidth / 4.0f;
 *         SkScalar offsetY = kHeight / 4.0f;
 *         drawTestCase(canvas, offsetX, offsetY,
 *                      SkBlendMode::kSrc, SkBlendMode::kSrc);
 *         drawTestCase(canvas, 3 * offsetX, offsetY,
 *                      SkBlendMode::kSrcOver, SkBlendMode::kSrcOver);
 *         drawTestCase(canvas, offsetX, 3 * offsetY,
 *                      SkBlendMode::kHardLight, SkBlendMode::kLuminosity);
 *         drawTestCase(canvas, 3 * offsetX, 3 * offsetY,
 *                      SkBlendMode::kSrcOver, SkBlendMode::kSrc);
 *     }
 *
 *     SkScalar fTextHeight;
 *     sk_sp<SkTextBlob> fBlob;
 * };
 * DEF_GM(return new LcdOverlapGM;)
 * ```
 */
public class LcdOverlapGM : GM() {
    private companion object {
        const val WIDTH: Int = 750
        const val HEIGHT: Int = 750
        const val POINT_SIZE: Int = 25
    }

    @Suppress("unused")
    private val fTextHeight: SkScalar = POINT_SIZE.toFloat()
    private var fBlob: SkTextBlob? = null

    override fun getName(): String = "lcdoverlap"
    override fun getISize(): SkISize = SkISize.Make(WIDTH, HEIGHT)

    override fun onOnceBeforeDraw() {
        val builder = SkTextBlobBuilder()
        val font = SkFont(ToolUtils.DefaultPortableTypeface(), 32f)
        val text = "able was I ere I saw elba"
        font.isSubpixel = true
        // Mirrors `font.setEdging(SkFont::Edging::kSubpixelAntiAlias)`.
        // kanvas-skia downgrades this to kAntiAlias internally (per
        // SkFont docstring) — kept here for source-spec fidelity.
        font.edging = SkFont.Edging.kSubpixelAntiAlias
        ToolUtils.addToTextBlob(builder, text, font, 0f, 0f)
        fBlob = builder.make()
    }

    private fun drawTestCase(
        canvas: SkCanvas,
        x: SkScalar,
        y: SkScalar,
        mode: SkBlendMode,
        mode2: SkBlendMode,
    ) {
        val blob = fBlob ?: return
        val colors = intArrayOf(
            SK_ColorRED,
            SK_ColorGREEN,
            SK_ColorBLUE,
            SK_ColorYELLOW,
            SK_ColorCYAN,
            SK_ColorMAGENTA,
        )
        for (i in colors.indices) {
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(360.0f / colors.size * i)
            canvas.translate(
                -blob.bounds().width() / 2.0f - blob.bounds().left() + 0.5f,
                0f,
            )
            val textPaint = SkPaint()
            textPaint.color = colors[i]
            textPaint.blendMode = if (i % 2 == 0) mode else mode2
            canvas.drawTextBlob(blob, 0f, 0f, textPaint)
            canvas.restore()
        }
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val offsetX: SkScalar = WIDTH / 4.0f
        val offsetY: SkScalar = HEIGHT / 4.0f
        drawTestCase(c, offsetX, offsetY, SkBlendMode.kSrc, SkBlendMode.kSrc)
        drawTestCase(c, 3 * offsetX, offsetY, SkBlendMode.kSrcOver, SkBlendMode.kSrcOver)
        drawTestCase(c, offsetX, 3 * offsetY, SkBlendMode.kHardLight, SkBlendMode.kLuminosity)
        drawTestCase(c, 3 * offsetX, 3 * offsetY, SkBlendMode.kSrcOver, SkBlendMode.kSrc)
    }
}
