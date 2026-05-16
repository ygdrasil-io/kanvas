package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorBLACK
import org.skia.math.SK_ColorLTGRAY
import org.skia.math.SK_ColorRED
import org.skia.math.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkFont
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextEncoding
import org.skia.math.SkISize
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/all_bitmap_configs.cpp::DEF_SIMPLE_GM(all_bitmap_configs, …, SCALE, 6*SCALE)`
 * (`SCALE = 128`).
 *
 * Renders `images/color_wheel.png` six times stacked vertically, each
 * row labelled with its `SkColorType` :
 *
 *  1. Native 32 (`kN32_SkColorType`) — direct decode.
 *  2. RGB 565 — **deferred** (no `kRGB_565` in `:kanvas-skia`).
 *  3. ARGB 4444 — copied to a 4444 bitmap via per-pixel sampling.
 *  4. RGBA F16 — copied to an F16 bitmap via per-pixel sampling.
 *  5. Alpha 8 — synthetic 128 × 128 spectrum (shifted by one byte per
 *     scanline), drawn as a tinted image.
 *  6. Gray 8 — **deferred** (no `kGray_8` in `:kanvas-skia`).
 *
 * Rows we can't render are left as the underlying checkerboard.
 *
 * The labels on top of each row use `ToolUtils::DefaultPortableFont()` at
 * 12 pt — same as upstream.
 *
 * C++ original (abbreviated) :
 * ```cpp
 * DEF_SIMPLE_GM(all_bitmap_configs, canvas, SCALE, 6 * SCALE) {
 *   SkPaint p(SkColors::kBlack); p.setAntiAlias(true);
 *   SkFont font = ToolUtils::DefaultPortableFont();
 *   ToolUtils::draw_checkerboard(canvas, SK_ColorLTGRAY, SK_ColorWHITE, 8);
 *   SkBitmap bitmap;
 *   if (ToolUtils::GetResourceAsBitmap("images/color_wheel.png", &bitmap)) {
 *     draw(canvas, p, font, bitmap, kN32_SkColorType, "Native 32");
 *     canvas->translate(0, SCALE);
 *     // copy_to 565 → draw → translate
 *     // copy_to 4444 → draw → translate
 *     // copy_to F16  → draw → translate
 *   }
 *   canvas->translate(0, SCALE);
 *   SkBitmap a8 = make_bitmap(kAlpha_8_SkColorType);
 *   draw(canvas, p, font, a8, kAlpha_8_SkColorType, "Alpha 8");
 *   p.setColor(SK_ColorRED);
 *   canvas->translate(0, SCALE);
 *   SkBitmap g8 = make_bitmap(kGray_8_SkColorType);
 *   draw(canvas, p, font, g8, kGray_8_SkColorType, "Gray 8");
 * }
 * ```
 */
public class AllBitmapConfigsGM : GM() {

    override fun getName(): String = "all_bitmap_configs"

    override fun getISize(): SkISize = SkISize.Make(SCALE, 6 * SCALE)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val p = SkPaint().apply { color = SK_ColorBLACK; isAntiAlias = true }
        val font: SkFont = ToolUtils.DefaultPortableFont()

        ToolUtils.draw_checkerboard(c, SK_ColorLTGRAY, SK_ColorWHITE, 8)

        val src = ToolUtils.GetResourceAsImage("images/color_wheel.png")
        if (src != null) {
            // Row 1 — Native 32.
            c.drawImage(src, 0f, 0f)
            c.drawSimpleText("Native 32", "Native 32".length, SkTextEncoding.kUTF8, 0f, 12f, font, p)

            // Row 2 — RGB 565 ; not supported. Translate-only to advance.
            c.translate(0f, SCALE.toFloat())
            c.drawSimpleText("RGB 565 (n/a)", "RGB 565 (n/a)".length, SkTextEncoding.kUTF8, 0f, 12f, font, p)

            // Row 3 — ARGB 4444. Per-pixel sample from a freshly allocated
            // 4444 bitmap of the source size. Premul-quantised in [SkBitmap.setPixel].
            c.translate(0f, SCALE.toFloat())
            val bm4444 = copyToBitmap(src, SkImageInfo.Make4444(src.width, src.height))
            c.drawImage(bm4444.asImage(), 0f, 0f)
            c.drawSimpleText("ARGB 4444", "ARGB 4444".length, SkTextEncoding.kUTF8, 0f, 12f, font, p)

            // Row 4 — RGBA F16. Same per-pixel copy, into an F16Norm bitmap.
            c.translate(0f, SCALE.toFloat())
            val bmF16 = copyToBitmap(
                src,
                SkImageInfo.Make(
                    src.width, src.height,
                    org.skia.foundation.SkColorType.kRGBA_F16Norm,
                ),
            )
            c.drawImage(bmF16.asImage(), 0f, 0f)
            c.drawSimpleText("RGBA F16", "RGBA F16".length, SkTextEncoding.kUTF8, 0f, 12f, font, p)
        } else {
            // Match upstream's "no source image" branch — skip three rows.
            c.translate(0f, (3 * SCALE).toFloat())
        }

        // Row 5 — Alpha 8 synthetic spectrum.
        c.translate(0f, SCALE.toFloat())
        val a8 = makeSpectrumA8(SCALE)
        c.drawImage(a8.asImage(), 0f, 0f)
        c.drawSimpleText("Alpha 8", "Alpha 8".length, SkTextEncoding.kUTF8, 0f, 12f, font, p)

        // Row 6 — Gray 8 ; not supported. Stop here ; underlying checker
        // remains visible.
        p.color = SK_ColorRED
        c.translate(0f, SCALE.toFloat())
        c.drawSimpleText("Gray 8 (n/a)", "Gray 8 (n/a)".length, SkTextEncoding.kUTF8, 0f, 12f, font, p)
    }

    /**
     * Substitute for upstream's `ToolUtils::copy_to(&dst, ct, src)` —
     * walks each pixel of [src] through [SkImage.peekPixel] and writes
     * the matching colour into a fresh bitmap of the desired [info].
     * The pixel write is performed via [SkBitmap.setPixel], so the
     * colour-type-specific premul / quantise rules are honoured.
     */
    private fun copyToBitmap(src: org.skia.foundation.SkImage, info: SkImageInfo): SkBitmap {
        val dst = SkBitmap.allocPixels(info)
        for (y in 0 until src.height) {
            for (x in 0 until src.width) {
                dst.setPixel(x, y, src.peekPixel(x, y))
            }
        }
        return dst
    }

    /**
     * Substitute for upstream's `make_bitmap(kAlpha_8_SkColorType)` :
     * builds a `size × size` A8 bitmap whose row `y` carries `spectrum[y]`
     * for the first 128 columns, then shifts by one byte per scanline.
     * The remaining columns are left at zero (upstream only fills `[0,
     * 128)` per row — beyond that the memory is uninitialised, which DM
     * runners zero-init).
     */
    private fun makeSpectrumA8(size: Int): SkBitmap {
        val bm = SkBitmap.allocPixels(SkImageInfo.MakeA8(size, size))
        val spectrum = ByteArray(256) { it.toByte() }
        for (y in 0 until bm.height) {
            // Copy `spectrum[y..y+127]` (clamped) into row y, columns 0..127.
            val rowOffset = y * bm.width
            for (x in 0 until 128) {
                val sIdx = (y + x).coerceAtMost(255)
                bm.pixelsA8[rowOffset + x] = spectrum[sIdx]
            }
        }
        return bm
    }

    private companion object {
        private const val SCALE: Int = 128
    }
}
