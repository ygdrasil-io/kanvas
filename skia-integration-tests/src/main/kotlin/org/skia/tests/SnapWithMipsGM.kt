package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SkColorType
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkMipmapMode
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/surface.cpp::snap_with_mips` (80 × 75).
 *
 * Creates a 32×32 raster surface and repeatedly fills it with alternating
 * colours (grey / blue) with a small contrasting centre square, snaps with
 * `withDefaultMipmaps()`, and draws the mipmapped image three times per
 * colour column at decreasing scale (`canvas.scale(.4f, .4f)` each row).
 *
 * C++ body (`DEF_SIMPLE_GM(snap_with_mips, canvas, 80, 75)`):
 * ```cpp
 * auto ii = SkImageInfo::Make({32, 32}, ct, kPremul_SkAlphaType, colorSpace);
 * auto surface = SkSurfaces::Raster(ii);
 * auto nextImage = [&](SkColor color) {
 *     surface->getCanvas()->clear(color);
 *     SkPaint p; p.setColor(~color | 0xFF000000);
 *     surface->getCanvas()->drawRect(MakeLTRB(w*2/5,h*2/5,w*3/5,h*3/5), p);
 *     return surface->makeImageSnapshot()->withDefaultMipmaps();
 * };
 * static const SkSamplingOptions kSampling{kLinear, kLinear};
 * canvas->save();
 * for (y : 0..3) {
 *   canvas->save();
 *   for (x : 0..2) {
 *     canvas->drawImage(nextImage(kColors[x]), 0, 0, kSampling);
 *     canvas->translate(32+8, 0);
 *   }
 *   canvas->restore();
 *   canvas->translate(0, 32+8);
 *   canvas->scale(.4f, .4f);
 * }
 * canvas->restore();
 * ```
 */
public class SnapWithMipsGM : GM() {

    override fun getName(): String = "snap_with_mips"
    override fun getISize(): SkISize = SkISize.Make(80, 75)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // The upstream picks the canvas' color type or falls back to RGBA_8888.
        // We always use RGBA_8888 on our raster backend.
        val ii = SkImageInfo.Make(32, 32, SkColorType.kRGBA_8888, SkAlphaType.kPremul)
        val surface = SkSurface.MakeRaster(ii)
        val kPad = 8
        val kSampling = SkSamplingOptions(SkFilterMode.kLinear, SkMipmapMode.kLinear)

        fun nextImage(color: Int): org.skia.foundation.SkImage {
            surface.canvas.clear(color)
            val w = surface.width.toFloat()
            val h = surface.height.toFloat()
            val contrastColor = (color.inv()) or (0xFF shl 24)  // ~color | 0xFF000000
            val paint = SkPaint().apply { this.color = contrastColor }
            surface.canvas.drawRect(
                SkRect.MakeLTRB(w * 2f / 5f, h * 2f / 5f, w * 3f / 5f, h * 3f / 5f),
                paint,
            )
            return surface.makeImageSnapshot().withDefaultMipmaps()
        }

        // 0xFFF0F0F0 = light grey; SK_ColorBLUE = 0xFF0000FF
        val kColors = intArrayOf(0xFFF0F0F0.toInt(), SK_ColorBLUE)

        c.save()
        for (y in 0 until 3) {
            c.save()
            for (x in 0 until 2) {
                val image = nextImage(kColors[x])
                c.drawImage(image, 0f, 0f, kSampling)
                c.translate((ii.width + kPad).toFloat(), 0f)
            }
            c.restore()
            c.translate(0f, (ii.width + kPad).toFloat())
            c.scale(0.4f, 0.4f)
        }
        c.restore()
    }
}
