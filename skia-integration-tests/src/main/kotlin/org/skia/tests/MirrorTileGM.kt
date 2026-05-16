package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.math.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.math.SkColorSetARGB
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/mirrortile.cpp::mirror_tile` (140 × 370).
 *
 * Tests image-shader `kMirror` tile mode with scale factors of 1 and
 * −1, both nearest and linear filters, and with/without a half-pixel
 * offset between device and image space. The linear filter should
 * only differ from nearest when a half-pixel offset is present. Tests
 * mirror tiling on X and Y axes separately, on 1×3 / 3×1 RGB strips.
 *
 * Output : 8 row pairs (`{nearest, linear}` × `{no-offset, half-offset}`),
 * each pair drawing a 3-mirrored horizontal strip + a 3-mirrored
 * vertical strip. The resulting offscreen bitmap is then 8×-scaled
 * onto the main canvas so individual texels are visible.
 */
public class MirrorTileGM : GM() {

    override fun getName(): String = "mirror_tile"
    override fun getISize(): SkISize = SkISize.Make(140, 370)

    override fun onDraw(canvas: SkCanvas?) {
        val canvas0 = canvas ?: return

        // 1×3 horizontal strip (R / G / B).
        val imgx = SkBitmap(3, 1).apply {
            setPixel(0, 0, SkColorSetARGB(0xFF, 0xFF, 0, 0))
            setPixel(1, 0, SkColorSetARGB(0xFF, 0, 0xFF, 0))
            setPixel(2, 0, SkColorSetARGB(0xFF, 0, 0, 0xFF))
        }.asImage()
        // 3×1 vertical strip.
        val imgy = SkBitmap(1, 3).apply {
            setPixel(0, 0, SkColorSetARGB(0xFF, 0xFF, 0, 0))
            setPixel(0, 1, SkColorSetARGB(0xFF, 0, 0xFF, 0))
            setPixel(0, 2, SkColorSetARGB(0xFF, 0, 0, 0xFF))
        }.asImage()

        // Offscreen surface for the texel-precise rendering ; we then
        // 8×-zoom it onto the main canvas. We substitute Skia's
        // `canvas->makeSurface(...)` with the standalone
        // [SkSurface.MakeRaster] (no wrapping needed for a leaf draw).
        val surf = SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(140 / 8 + 1, 370 / 8 + 1))
        val c = surf.canvas
        c.clear(SK_ColorWHITE)

        val offsets = booleanArrayOf(false, true)
        val filters = arrayOf(SkFilterMode.kNearest, SkFilterMode.kLinear)
        for (offset in offsets) {
            for (fm in filters) {
                val paint = SkPaint()
                // Horizontal mirror, vertical clamp.
                paint.shader = imgx.makeShader(
                    SkTileMode.kMirror, SkTileMode.kClamp, SkSamplingOptions(fm),
                )
                c.save()
                c.translate(imgx.width.toFloat(), 0f)
                if (offset) c.translate(0.5f, 0f)
                c.drawRect(
                    SkRect.MakeXYWH(-imgx.width.toFloat(), 0f, 3f * imgx.width, 5f),
                    paint,
                )
                c.restore()

                // Vertical mirror, horizontal clamp.
                paint.shader = imgy.makeShader(
                    SkTileMode.kClamp, SkTileMode.kMirror, SkSamplingOptions(fm),
                )
                c.save()
                c.translate(3f * imgx.width + 3f, imgy.height.toFloat())
                if (offset) c.translate(0f, 0.5f)
                c.drawRect(
                    SkRect.MakeXYWH(0f, -imgy.height.toFloat(), 5f, 3f * imgy.height),
                    paint,
                )
                c.restore()

                c.translate(0f, 3f * imgy.height + 3f)
            }
        }

        canvas0.scale(8f, 8f)
        canvas0.drawImage(surf.makeImageSnapshot(), 0f, 0f)
    }
}
