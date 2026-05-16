package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkFont
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.skia.math.colorToRGB565
import org.skia.math.SkISize
import org.skia.tools.SkRandom
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/imageblur2.cpp`. Draws a 6×6 grid of saveLayer'd
 * text blocks, each with a different `(sigmaX, sigmaY)` pair pulled
 * from `kBlurSigmas = { 0.0, 0.3, 0.5, 2.0, 32.0, 80.0 }`. Each cell
 * contains 6 short strings rendered with `SkFont::Edging::kAlias`.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(imageblur2, canvas, kWidth, kHeight) {
 *     constexpr float kBlurSigmas[] = { 0.0, 0.3f, 0.5f, 2.0f, 32.0f, 80.0f };
 *     const char* kTestStrings[] = {
 *         "The quick`~",
 *         "brown fox[]",
 *         "jumped over",
 *         "the lazy@#$",
 *         "dog.{}!%^&",
 *         "*()+=-\\'\"/",
 *     };
 *     ...
 *     for (int x = 0; x < sigmaCount; x++) {
 *         for (int y = 0; y < sigmaCount; y++) {
 *             SkPaint paint;
 *             paint.setImageFilter(SkImageFilters::Blur(sigmaX, sigmaY, nullptr));
 *             canvas->saveLayer(nullptr, &paint);
 *             SkRandom rand;
 *             SkPaint textPaint;
 *             textPaint.setColor(ToolUtils::color_to_565(rand.nextBits(24) | 0xFF000000));
 *             for (int i = 0; i < testStringCount; i++) {
 *                 canvas->drawString(kTestStrings[i],
 *                                    SkIntToScalar(x * dx),
 *                                    SkIntToScalar(y * dy + textSize * i + textSize),
 *                                    font, textPaint);
 *             }
 *             canvas->restore();
 *         }
 *     }
 * }
 * ```
 *
 * Each cell uses a fresh `SkRandom` (default seed), so the text colour
 * is the same in every cell — `colorToRGB565` of the first 24-bit draw
 * masked to opaque.
 */
public class ImageBlur2GM : GM() {

    override fun getName(): String = "imageblur2"
    override fun getISize(): SkISize = SkISize.Make(IMAGE_BLUR2_WIDTH, IMAGE_BLUR2_HEIGHT)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val sigmaCount = kBlurSigmas.size
        val testStringCount = kTestStrings.size
        val dx = IMAGE_BLUR2_WIDTH.toFloat() / sigmaCount.toFloat()
        val dy = IMAGE_BLUR2_HEIGHT.toFloat() / sigmaCount.toFloat()
        val textSize = 12f

        val font = SkFont(ToolUtils.DefaultPortableTypeface(), textSize).apply {
            edging = SkFont.Edging.kAlias
        }

        for (x in 0 until sigmaCount) {
            val sigmaX = kBlurSigmas[x]
            for (y in 0 until sigmaCount) {
                val sigmaY = kBlurSigmas[y]

                val paint = SkPaint().apply {
                    imageFilter = SkImageFilters.Blur(sigmaX, sigmaY, null)
                }
                c.saveLayer(null, paint)
                try {
                    val rand = SkRandom()
                    val textPaint = SkPaint()
                    textPaint.color = colorToRGB565(rand.nextBits(24) or (0xFF shl 24))
                    for (i in 0 until testStringCount) {
                        c.drawString(
                            kTestStrings[i],
                            (x * dx),
                            (y * dy + textSize * i + textSize),
                            font,
                            textPaint,
                        )
                    }
                } finally {
                    c.restore()
                }
            }
        }
    }

    private companion object {
        const val IMAGE_BLUR2_WIDTH: Int = 500
        const val IMAGE_BLUR2_HEIGHT: Int = 500

        val kBlurSigmas: FloatArray = floatArrayOf(0.0f, 0.3f, 0.5f, 2.0f, 32.0f, 80.0f)
        val kTestStrings: Array<String> = arrayOf(
            "The quick`~",
            "brown fox[]",
            "jumped over",
            "the lazy@#\$",
            "dog.{}!%^&",
            "*()+=-\\'\"/",
        )
    }
}
