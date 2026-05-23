package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorCYAN
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorLTGRAY
import org.graphiks.math.SK_ColorMAGENTA
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorTRANSPARENT
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SK_ColorYELLOW
import org.graphiks.math.SkColor
import org.graphiks.math.SkISize
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextEncoding
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/all_bitmap_configs.cpp::DEF_SIMPLE_GM(not_native32_bitmap_config, …, SCALE, SCALE)`
 * (`SCALE = 128`).
 *
 * Renders the synthetic "color wheel" (white disc + seven coloured
 * letters K, R, G, B, C, M, Y arranged around the centre) into a
 * `kN32_SkColorType` bitmap (which is `kRGBA_8888` under
 * `:kanvas-skia`), copies the pixels into a `kBGRA_8888` bitmap, then
 * draws that **non-native** image over a checker background.
 *
 * Upstream selects the non-native colour type at compile time via
 * `SK_PMCOLOR_BYTE_ORDER` — on a `kN32 = RGBA` host (typical Linux x86)
 * `ct = kBGRA_8888`, on a `kN32 = BGRA` host `ct = kRGBA_8888`. Either
 * way the goal is to verify that drawing a bitmap of "the other" 32-bit
 * byte order produces visually identical output to the native one
 * (the byte-swap happens transparently in the codec / surface code).
 *
 * `:kanvas-skia`'s `kN32` is normalised to `kRGBA_8888` (Pascal-Argb Int
 * backing), so we always copy into a `kBGRA_8888` bitmap here. Both
 * 8888 colour types share the same internal `0xAARRGGBB` representation
 * (see [SkBitmap.pixelsBGRA8888] KDoc) — the colour type only affects
 * the external byte order on encode / decode, which is invisible to the
 * GM's render pipeline.
 *
 * C++ original :
 * ```cpp
 * sk_sp<SkImage> make_not_native32_color_wheel() {
 *     SkBitmap n32bitmap, notN32bitmap;
 *     n32bitmap.allocN32Pixels(SCALE, SCALE);
 *     n32bitmap.eraseColor(SK_ColorTRANSPARENT);
 *     SkCanvas n32canvas(n32bitmap);
 *     color_wheel_native(&n32canvas);
 *     #if SK_PMCOLOR_BYTE_ORDER(B,G,R,A)
 *         const SkColorType ct = kRGBA_8888_SkColorType;
 *     #elif SK_PMCOLOR_BYTE_ORDER(R,G,B,A)
 *         const SkColorType ct = kBGRA_8888_SkColorType;
 *     #endif
 *     SkAssertResult(ToolUtils::copy_to(&notN32bitmap, ct, n32bitmap));
 *     return notN32bitmap.asImage();
 * }
 *
 * DEF_SIMPLE_GM(not_native32_bitmap_config, canvas, SCALE, SCALE) {
 *     sk_sp<SkImage> notN32image(make_not_native32_color_wheel());
 *     ToolUtils::draw_checkerboard(canvas, SK_ColorLTGRAY, SK_ColorWHITE, 8);
 *     canvas->drawImage(notN32image.get(), 0.0f, 0.0f);
 * }
 * ```
 */
public class NotNative32BitmapConfigGM : GM() {

    override fun getName(): String = "not_native32_bitmap_config"

    override fun getISize(): SkISize = SkISize.Make(SCALE, SCALE)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val notN32image = makeNotNative32ColorWheel()
        ToolUtils.draw_checkerboard(c, SK_ColorLTGRAY, SK_ColorWHITE, 8)
        c.drawImage(notN32image.asImage(), 0f, 0f)
    }

    /**
     * Mirrors upstream's `make_not_native32_color_wheel()`. Renders the
     * synthetic wheel into a native (kRGBA_8888) raster surface, then
     * copies the resulting pixels into a kBGRA_8888 bitmap. The pixel
     * values are colour-equivalent (both 8888 share the same internal
     * representation in `:kanvas-skia`), so the visible output matches
     * the native bitmap exactly.
     */
    private fun makeNotNative32ColorWheel(): SkBitmap {
        // 1. Allocate a native N32 = kRGBA_8888 raster surface and draw the
        //    colour wheel into it. Upstream calls `n32bitmap.eraseColor(0)`
        //    before drawing ; [SkSurface.MakeRaster] zero-fills (TRANSPARENT)
        //    on construction, so we skip the explicit erase.
        val n32Info = SkImageInfo.MakeN32(SCALE, SCALE, SkAlphaType.kPremul)
        val surface = SkSurface.MakeRaster(n32Info)
        val n32canvas = surface.canvas
        n32canvas.clear(SK_ColorTRANSPARENT)
        drawColorWheelNative(n32canvas)
        val n32snapshot = surface.makeImageSnapshot()

        // 2. Allocate a non-native kBGRA_8888 bitmap of the same size and
        //    copy each pixel through. Both 8888 colour types share the
        //    `0xAARRGGBB` Pascal-Argb backing (see [SkBitmap.pixelsBGRA8888]
        //    KDoc), so this is colour-equivalent — only the metadata
        //    differs.
        val notN32Info = SkImageInfo.Make(
            width = SCALE,
            height = SCALE,
            colorType = SkColorType.kBGRA_8888,
            alphaType = SkAlphaType.kPremul,
        )
        val notN32bitmap = SkBitmap.allocPixels(notN32Info)
        for (y in 0 until SCALE) {
            for (x in 0 until SCALE) {
                val c = n32snapshot.peekPixel(x, y)
                notN32bitmap.setPixel(x, y, c)
            }
        }
        return notN32bitmap
    }

    /**
     * Mirrors `color_wheel_native(SkCanvas*)` from
     * `gm/all_bitmap_configs.cpp`. Draws a 0.5·SCALE-radius white disc
     * centred on the canvas, then seven coloured letters at the centre
     * (K), bottom (R), top-left (G), top-right (B), top (C),
     * bottom-right (M), bottom-left (Y).
     *
     * Geometry constants follow upstream verbatim :
     *  - D = 0.3 × SCALE (radius of the letter ring)
     *  - X = D × sqrt(3)/2, Y = D × 0.5 (60° trigonometry)
     *
     * Font is Liberation Sans **Bold** at `0.28125 × SCALE` (= 36 pt at
     * SCALE = 128), edging `kAlias` — upstream picks aliased glyphs so
     * the output is deterministic across AA implementations.
     */
    private fun drawColorWheelNative(target: SkCanvas) {
        val save = target.save()
        try {
            target.translate(0.5f * SCALE, 0.5f * SCALE)

            val whiteDiscPaint = SkPaint().apply { color = SK_ColorWHITE }
            target.drawCircle(0f, 0f, 0.5f * SCALE, whiteDiscPaint)

            val sqrt3Over2 = 0.8660254037844387f
            val z = 0f
            val d = 0.3f * SCALE
            val x = d * sqrt3Over2
            val y = d * 0.5f

            val font = SkFont(
                ToolUtils.CreatePortableTypeface("Sans", SkFontStyle.Bold()),
                0.28125f * SCALE,
            ).apply { edging = SkFont.Edging.kAlias }

            drawCenterLetter('K', font, SK_ColorBLACK, z, z, target)
            drawCenterLetter('R', font, SK_ColorRED, z, d, target)
            drawCenterLetter('G', font, SK_ColorGREEN, -x, -y, target)
            drawCenterLetter('B', font, SK_ColorBLUE, x, -y, target)
            drawCenterLetter('C', font, SK_ColorCYAN, z, -d, target)
            drawCenterLetter('M', font, SK_ColorMAGENTA, x, y, target)
            drawCenterLetter('Y', font, SK_ColorYELLOW, -x, y, target)
        } finally {
            target.restoreToCount(save)
        }
    }

    /**
     * Mirrors `draw_center_letter(char, font, color, x, y, canvas)` —
     * measures the bounds of the single character at the given font,
     * then draws it centred on `(x, y)` (offsetting by `-bounds.center`).
     */
    private fun drawCenterLetter(
        ch: Char, font: SkFont, color: SkColor,
        x: Float, y: Float, target: SkCanvas,
    ) {
        val bounds = org.graphiks.math.SkRect.MakeEmpty()
        val text = ch.toString()
        font.measureText(text, text.length, SkTextEncoding.kUTF8, bounds)
        val paint = SkPaint().apply { this.color = color }
        target.drawSimpleText(
            text, text.length, SkTextEncoding.kUTF8,
            x - bounds.centerX(),
            y - bounds.centerY(),
            font, paint,
        )
    }

    private companion object {
        private const val SCALE: Int = 128
    }
}
