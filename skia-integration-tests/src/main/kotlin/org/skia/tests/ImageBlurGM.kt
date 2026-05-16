package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.skia.foundation.SkFont
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.graphiks.math.colorToRGB565
import org.graphiks.math.SkISize
import org.skia.tools.SkRandom
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/imageblur.cpp`. The upstream file registers **two**
 * GM variants via `DEF_SIMPLE_GM_BG`, both calling the same
 * `imageblurgm_draw(sigmaX, sigmaY, canvas)` helper with different blur
 * sigmas on a black background :
 *
 *  - [ImageBlurGM] (`imageblur`)      — `sigmaX = 24, sigmaY = 0`
 *  - [ImageBlurLargeGM] (`imageblur_large`) — `sigmaX = sigmaY = 80`
 *
 * The helper draws 25 strings of `"The quick brown fox jumped over the
 * lazy dog."` at deterministic random `(x, y)` positions inside the
 * 500 × 500 canvas, with deterministic random colours (`color_to_565`
 * of the upper 24 bits of `nextBits(24)` masked to opaque) and
 * deterministic random font sizes in `[0, 300]`. All 25 strings are
 * recorded inside a single `saveLayer(nullptr, &paint)` whose [paint]
 * carries [SkImageFilters.Blur] — the layer's contents are drawn
 * unblurred, then on `restore()` the offscreen is gaussian-blurred and
 * composited onto the black BG.
 *
 * Phase G7 wires `paint.imageFilter` on `saveLayer` to the
 * filter-image-then-composite path inside [SkCanvas.restore]
 * (`SkCanvas.kt:160-191`), so this GM exercises the full text-into-layer
 * + Gaussian-blur + premul-blit pipeline end-to-end. [SkRandom] is a
 * bit-compatible port of upstream, and `colorToRGB565` mirrors
 * `ToolUtils::color_to_565`, so the 25 strings land at the same
 * positions / colours / sizes as the reference image.
 *
 * C++ original:
 * ```cpp
 * void imageblurgm_draw(SkScalar fSigmaX, SkScalar fSigmaY, SkCanvas* canvas) {
 *     SkPaint paint;
 *     paint.setImageFilter(SkImageFilters::Blur(fSigmaX, fSigmaY, nullptr));
 *     canvas->saveLayer(nullptr, &paint);
 *     const char* str = "The quick brown fox jumped over the lazy dog.";
 *     SkRandom rand;
 *     SkPaint textPaint;
 *     SkFont   font = ToolUtils::DefaultPortableFont();
 *     for (int i = 0; i < 25; ++i) {
 *         int x = rand.nextULessThan(WIDTH);
 *         int y = rand.nextULessThan(HEIGHT);
 *         textPaint.setColor(ToolUtils::color_to_565(rand.nextBits(24) | 0xFF000000));
 *         font.setSize(rand.nextRangeScalar(0, 300));
 *         canvas->drawString(str, SkIntToScalar(x), SkIntToScalar(y), font, textPaint);
 *     }
 *     canvas->restore();
 * }
 * DEF_SIMPLE_GM_BG(imageblur,       canvas, 500, 500, SK_ColorBLACK) {
 *     imageblurgm_draw(24.0f, 0.0f, canvas);
 * }
 * DEF_SIMPLE_GM_BG(imageblur_large, canvas, 500, 500, SK_ColorBLACK) {
 *     imageblurgm_draw(80.0f, 80.0f, canvas);
 * }
 * ```
 */
internal fun imageBlurDraw(sigmaX: Float, sigmaY: Float, canvas: SkCanvas) {
    val paint = SkPaint().apply {
        imageFilter = SkImageFilters.Blur(sigmaX, sigmaY, null)
    }
    canvas.saveLayer(null, paint)
    try {
        val str = "The quick brown fox jumped over the lazy dog."
        val rand = SkRandom()
        val textPaint = SkPaint()
        val font: SkFont = ToolUtils.DefaultPortableFont()
        repeat(25) {
            val x = rand.nextULessThan(IMAGE_BLUR_WIDTH)
            val y = rand.nextULessThan(IMAGE_BLUR_HEIGHT)
            textPaint.color = colorToRGB565(rand.nextBits(24) or (0xFF shl 24))
            font.size = rand.nextRangeScalar(0f, 300f)
            canvas.drawString(str, x.toFloat(), y.toFloat(), font, textPaint)
        }
    } finally {
        canvas.restore()
    }
}

internal const val IMAGE_BLUR_WIDTH: Int = 500
internal const val IMAGE_BLUR_HEIGHT: Int = 500

/** `DEF_SIMPLE_GM_BG(imageblur, canvas, 500, 500, SK_ColorBLACK)`. */
public class ImageBlurGM : GM() {
    init { setBGColor(SK_ColorBLACK) }
    override fun getName(): String = "imageblur"
    override fun getISize(): SkISize = SkISize.Make(IMAGE_BLUR_WIDTH, IMAGE_BLUR_HEIGHT)
    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        imageBlurDraw(24.0f, 0.0f, c)
    }
}

/** `DEF_SIMPLE_GM_BG(imageblur_large, canvas, 500, 500, SK_ColorBLACK)`. */
public class ImageBlurLargeGM : GM() {
    init { setBGColor(SK_ColorBLACK) }
    override fun getName(): String = "imageblur_large"
    override fun getISize(): SkISize = SkISize.Make(IMAGE_BLUR_WIDTH, IMAGE_BLUR_HEIGHT)
    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        imageBlurDraw(80.0f, 80.0f, c)
    }
}
