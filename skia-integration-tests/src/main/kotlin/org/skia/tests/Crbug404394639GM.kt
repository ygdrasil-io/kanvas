package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.graphiks.math.SK_ColorCYAN
import org.graphiks.math.SK_ColorMAGENTA
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode

/**
 * Port of Skia's `gm/image.cpp::crbug_404394639`
 * (`DEF_SIMPLE_GM(crbug_404394639, canvas, 500, 500)`).
 *
 * Builds a very large (500 × 40000) cyan-to-magenta linear-gradient source
 * image, then rescales it to 500×500 via `SkImage::makeScaled` with
 * `kLinear` sampling, and draws the result.
 *
 * The GM was filed to verify that `makeScaled` does not crash or corrupt
 * pixels when the source height exceeds 32768 (the historic Skia limit
 * that triggered undefined behaviour in the old raster mip-generation path).
 *
 * Exercises the raster `SkImage::makeScaled` path on an image taller than
 * 32768 pixels.
 */
public class Crbug404394639GM : GM() {
    override fun getName(): String = "crbug_404394639"
    override fun getISize(): SkISize = SkISize.Make(500, 500)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // ── Build the 500×40000 raster source image. ────────────────────
        val sourceWidth = 500
        val sourceHeight = 40000
        val sourceInfo = SkImageInfo.MakeN32Premul(sourceWidth, sourceHeight)

        val surf = SkSurface.MakeRaster(sourceInfo)
        val surfCanvas = surf.canvas

        // Cyan → magenta linear gradient (vertical).
        val pts = arrayOf(
            SkPoint.Make(0f, 0f),
            SkPoint.Make(0f, sourceHeight.toFloat()),
        )
        val colors = intArrayOf(SK_ColorCYAN, SK_ColorMAGENTA)
        val shader = SkLinearGradient.Make(pts[0], pts[1], colors, null, SkTileMode.kClamp)

        val paint = SkPaint().apply { this.shader = shader }
        surfCanvas.drawRect(
            SkRect.MakeXYWH(0f, 0f, sourceWidth.toFloat(), sourceHeight.toFloat()),
            paint,
        )

        val largeSourceImage = surf.makeImageSnapshot()

        // ── makeScaled: 500×40000 → 500×500 with kLinear sampling. ──────
        val sampling = SkSamplingOptions(SkFilterMode.kLinear)
        val targetInfo = sourceInfo.makeWH(500, 500)
        val scaledImage = largeSourceImage.makeScaled(targetInfo, sampling) ?: return

        c.drawImage(scaledImage, 0f, 0f)
    }
}
