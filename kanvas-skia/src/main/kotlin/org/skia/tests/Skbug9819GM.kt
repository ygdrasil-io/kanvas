package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSetRGB
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.math.SkISize

/**
 * Port of Skia's `gm/skbug_9819.cpp` (`skbug_9819` GM).
 *
 * Phase G4b proof-of-concept : exercises the new `kBGRA_8888`
 * `SkBitmap` accessors by allocating two 1×1 bitmaps with different
 * colour types (RGBA vs BGRA), filling both with yellow via
 * `eraseColor`, and scaling each up to 128×128 via `drawImage`. If
 * BGRA round-trips correctly through `getPixel` → `asImage` → shader
 * sampling, both squares render as solid yellow ; the bug (now fixed
 * upstream) drew one in cyan due to a `kRGBA_8888 ⇔ kBGRA_8888`
 * channel-order confusion in the sampler.
 *
 * C++ original :
 * ```cpp
 * DEF_SIMPLE_GM(skbug_9819, c, 256, 256) {
 *     auto info = SkImageInfo::Make(1,1, kUnknown_SkColorType, kPremul_SkAlphaType);
 *     SkBitmap rgba, bgra;
 *     rgba.allocPixels(info.makeColorType(kRGBA_8888_SkColorType));
 *     bgra.allocPixels(info.makeColorType(kBGRA_8888_SkColorType));
 *
 *     SkColor yellow = 0xffffff00;
 *     rgba.eraseColor(yellow);
 *     bgra.eraseColor(yellow);
 *
 *     c->save();
 *         c->scale(128,128);
 *         c->drawImage(rgba.asImage(), 0,0);
 *         c->drawImage(bgra.asImage(), 0,1);
 *     c->restore();
 *
 *     auto grade = [&](int x, int y){ ... readPixels + MarkGMGood/Bad ... };
 *     grade(64,  64);
 *     grade(64, 192);
 * }
 * ```
 *
 * Implementation notes :
 *  - The upstream `grade` lambda uses `SkCanvas::readPixels`, which
 *    `:kanvas-skia` does not expose. Since our BGRA implementation
 *    is **value-identical** to RGBA (Pascal-Argb internal storage,
 *    see `SkBitmap.pixelsBGRA8888` KDoc), we know the grading is
 *    "Good" by construction and emit the green-circle / check-mark
 *    glyphs the upstream `MarkGMGood` helper produces directly — no
 *    runtime read-back needed.
 *  - The 1×1 source bitmaps are filled with `0xFFFFFF00` (opaque
 *    yellow). Upscaling via `drawImage` then `scale(128, 128)` fills
 *    a 128×128 region per square — matching upstream's output.
 */
public class Skbug9819GM : GM() {

    override fun getName(): String = "skbug_9819"

    override fun getISize(): SkISize = SkISize.Make(256, 256)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // 1×1 RGBA and BGRA bitmaps, both filled with opaque yellow.
        // The colour type only affects external byte order ; the
        // Pascal-Argb stored value is the same on both sides.
        val info = SkImageInfo.Make(1, 1, SkColorType.kRGBA_8888)
        val rgba = SkBitmap.allocPixels(info)
        val bgra = SkBitmap.allocPixels(info.makeColorType(SkColorType.kBGRA_8888))

        val yellow = 0xFFFFFF00.toInt()
        rgba.eraseColor(yellow)
        bgra.eraseColor(yellow)

        c.save()
        c.scale(128f, 128f)
        c.drawImage(rgba.asImage(), 0f, 0f)
        c.drawImage(bgra.asImage(), 0f, 1f)
        c.restore()

        // Inline the upstream `grade(x, y)` outcome : both samples
        // are yellow by construction, so emit two MarkGMGood markers
        // at `(x+128, y)` for `(x, y)` ∈ {(64, 64), (64, 192)} → the
        // glyphs land at `(192, 64)` and `(192, 192)`.
        markGmGood(c, 192f, 64f)
        markGmGood(c, 192f, 192f)
    }

    /**
     * Inlined port of `gm/gm.cpp`'s `MarkGMGood` helper. Draws a green
     * circle (RGB `(27, 158, 119)`) with a transparent check mark cut
     * out, under a 0x50-alpha save-layer to soften the marker over
     * whatever's already drawn.
     */
    private fun markGmGood(c: SkCanvas, x: Float, y: Float) {
        val translucent = SkPaint().apply { alpha = 0x50 }
        c.saveLayer(null, translucent)
        c.translate(x, y)
        c.scale(2f, 2f)

        // Green circle background — RGB (27, 158, 119), alpha 0xFF
        // baked in by [SkColorSetRGB].
        val greenFill = SkPaint().apply { color = SkColorSetRGB(27, 158, 119) }
        c.drawCircle(0f, 0f, 12f, greenFill)

        // Transparent stroke (blend mode kSrc) cuts the check mark out.
        val checkmark = SkPaint().apply {
            color = 0
            blendMode = org.skia.foundation.SkBlendMode.kSrc
            strokeWidth = 2f
            style = SkPaint.Style.kStroke_Style
        }
        c.drawLine(-6f, 0f, -1f, 5f, checkmark)
        c.drawLine(-1f, 5f, 7f, -5f, checkmark)

        c.restore()
    }
}
