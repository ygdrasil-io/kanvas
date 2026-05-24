package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPixmap
import org.skia.foundation.SkSamplingOptions
import org.graphiks.math.SkISize
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Port of Skia's `gm/copy_to_4444.cpp::DEF_SIMPLE_GM(format4444, canvas, 64, 64)`.
 *
 * Verifies ARGB_4444 pixel storage and raw-pixel write-back via four
 * 16×16 blocks (the canvas is pre-scaled 16×) :
 *
 *  - (0,0) — solid RED drawn via [SkCanvas.clear] on a 1×1 4444 bitmap.
 *  - (1,1) — solid BLUE drawn via [SkCanvas.clear].
 *  - (2,2) — RED pixel written by packing the raw 16-bit value
 *    `(a<<0)|(b<<4)|(g<<8)|(r<<12)` and pushed via [SkBitmap.writePixels].
 *  - (3,3) — BLUE pixel the same way.
 *
 * C++ original :
 * ```cpp
 * DEF_SIMPLE_GM(format4444, canvas, 64, 64) {
 *     canvas->scale(16, 16);
 *     SkBitmap bitmap;
 *     SkImageInfo imageInfo = SkImageInfo::Make(
 *         1, 1, kARGB_4444_SkColorType, kPremul_SkAlphaType);
 *     bitmap.allocPixels(imageInfo);
 *     SkCanvas offscreen(bitmap);
 *     offscreen.clear(SK_ColorRED);
 *     canvas->drawImage(bitmap.asImage(), 0, 0);
 *     offscreen.clear(SK_ColorBLUE);
 *     canvas->drawImage(bitmap.asImage(), 1, 1);
 *     auto pack4444 = [](unsigned a, unsigned r, unsigned g, unsigned b) -> uint16_t {
 *         return (a << 0) | (b << 4) | (g << 8) | (r << 12);
 *     };
 *     uint16_t red4444  = pack4444(0xF, 0xF, 0x0, 0x0);
 *     uint16_t blue4444 = pack4444(0xF, 0x0, 0x0, 0x0F);
 *     SkPixmap redPixmap(imageInfo, &red4444, 2);
 *     if (bitmap.writePixels(redPixmap, 0, 0)) {
 *         canvas->drawImage(bitmap.asImage(), 2, 2);
 *     }
 *     SkPixmap bluePixmap(imageInfo, &blue4444, 2);
 *     if (bitmap.writePixels(bluePixmap, 0, 0)) {
 *         canvas->drawImage(bitmap.asImage(), 3, 3);
 *     }
 * }
 * ```
 */
public class Format4444GM : GM() {

    override fun getName(): String = "format4444"

    override fun getISize(): SkISize = SkISize.Make(64, 64)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Scale so that each 1×1 bitmap pixel paints as a 16×16 block.
        c.scale(16f, 16f)

        val imageInfo = SkImageInfo.Make(1, 1, SkColorType.kARGB_4444, SkAlphaType.kPremul)
        val bitmap = SkBitmap.allocPixels(imageInfo)
        val offscreen = SkCanvas(bitmap)

        // Block (0,0) — RED via canvas.clear
        offscreen.clear(SK_ColorRED)
        c.drawImage(bitmap.asImage(), 0f, 0f, SkSamplingOptions.Default, null)

        // Block (1,1) — BLUE via canvas.clear
        offscreen.clear(SK_ColorBLUE)
        c.drawImage(bitmap.asImage(), 1f, 1f, SkSamplingOptions.Default, null)

        // pack4444: (a<<0)|(b<<4)|(g<<8)|(r<<12)  —  matches upstream's lambda
        val red4444  = pack4444(0xF, 0xF, 0x0, 0x0)
        val blue4444 = pack4444(0xF, 0x0, 0x0, 0xF)

        // Block (2,2) — RED via writePixels
        val redBuf = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(red4444)
        redBuf.flip()
        val redPixmap = SkPixmap(imageInfo, redBuf, 2)
        if (bitmap.writePixels(redPixmap, 0, 0)) {
            c.drawImage(bitmap.asImage(), 2f, 2f, SkSamplingOptions.Default, null)
        }

        // Block (3,3) — BLUE via writePixels
        val blueBuf = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(blue4444)
        blueBuf.flip()
        val bluePixmap = SkPixmap(imageInfo, blueBuf, 2)
        if (bitmap.writePixels(bluePixmap, 0, 0)) {
            c.drawImage(bitmap.asImage(), 3f, 3f, SkSamplingOptions.Default, null)
        }
    }

    /**
     * Packs four 4-bit channel values into a 16-bit ARGB_4444 word using
     * the upstream layout: `(a<<0) | (b<<4) | (g<<8) | (r<<12)`.
     * Each input should be in `[0x0, 0xF]`.
     */
    private fun pack4444(a: Int, r: Int, g: Int, b: Int): Short =
        ((a and 0xF) or ((b and 0xF) shl 4) or ((g and 0xF) shl 8) or ((r and 0xF) shl 12)).toShort()
}
