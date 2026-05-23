package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.core.SrcRectConstraint
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkMipmapMode
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's [`gm/bleed.cpp`](https://github.com/google/skia/blob/main/gm/bleed.cpp)
 * `bleed_downscale` simple GM (360 × 240).
 *
 * Constructs a 32×32 image with a 10px red border and a 12px blue interior
 * (with 2px margin to the "src" rect), then draws it downscaled to a
 * single pixel and magnified to 100×100, exercising kStrict vs kFast
 * [SrcRectConstraint] across three sampling modes.
 *
 * Shows that [SrcRectConstraint.kFast] sees the red margin when the image
 * is scaled down far enough (via mip-mapping), while
 * [SrcRectConstraint.kStrict] prevents it.
 *
 * Upstream helper `ToolUtils::makeSurface(canvas, info)` maps to
 * [SkSurface.MakeRaster] for the raster-only `:kanvas-skia` backend.
 */
public class BleedDownscaleGM : GM() {

    override fun getName(): String = "bleed_downscale"
    override fun getISize(): SkISize = SkISize.Make(360, 240)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val (img, src) = makeImage()

        c.translate(10f, 10f)

        val constraints = arrayOf(
            SrcRectConstraint.kStrict,
            SrcRectConstraint.kFast,
        )
        val samplings = arrayOf(
            SkSamplingOptions(SkFilterMode.kNearest),
            SkSamplingOptions(SkFilterMode.kLinear),
            SkSamplingOptions(SkFilterMode.kLinear, SkMipmapMode.kLinear),
        )

        for (constraint in constraints) {
            c.save()
            for (sampling in samplings) {
                // Shrink the image to 1×1 with the given constraint + sampling
                val surf = SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(1, 1))
                surf.canvas.drawImageRect(
                    img,
                    src,
                    SkRect.MakeWH(1f, 1f),
                    sampling,
                    null,
                    constraint,
                )
                // Blow up the 1-pixel result to 100×100
                val snap = surf.makeImageSnapshot()
                c.drawImageRect(
                    snap,
                    SkRect.MakeWH(snap.width.toFloat(), snap.height.toFloat()),
                    SkRect.MakeWH(100f, 100f),
                    SkSamplingOptions(),
                )
                c.translate(120f, 0f)
            }
            c.restore()
            c.translate(0f, 120f)
        }
    }

    /**
     * Mirrors Skia's `make_image(canvas, srcR)` in `bleed.cpp`.
     *
     * Creates a 32×32 (N = 10+2+8+2+10) image:
     *  - outer 10px band of red
     *  - inner 12px blue region (8px core + 2px margin each side)
     *
     * Returns the image and the src rect that is the inner 8×8 blue-only
     * region (inset 2px inside the blue area).
     *
     * N = 32 is a power of two to keep mipmap filtering deterministic
     * across GPU backends (matches upstream comment).
     */
    private fun makeImage(): Pair<SkImage, SkRect> {
        val n = 10 + 2 + 8 + 2 + 10   // = 32
        val info = SkImageInfo.MakeN32Premul(n, n)
        val surface = SkSurface.MakeRaster(info)
        val sc = surface.canvas

        val paint = SkPaint()

        // Fill entire image with red (the outer border)
        paint.color = SK_ColorRED
        sc.drawRect(SkRect.MakeWH(n.toFloat(), n.toFloat()), paint)

        // Draw blue interior (inset 10px from each edge)
        val inner = SkRect.MakeLTRB(10f, 10f, (n - 10).toFloat(), (n - 10).toFloat())
        paint.color = SK_ColorBLUE
        sc.drawRect(inner, paint)

        // src rect = inner rect inset 2px further
        val srcRect = SkRect.MakeLTRB(
            inner.left + 2f,
            inner.top + 2f,
            inner.right - 2f,
            inner.bottom - 2f,
        )

        return Pair(surface.makeImageSnapshot(), srcRect)
    }
}
