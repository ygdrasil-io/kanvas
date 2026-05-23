package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/thinrects.cpp::DEF_SIMPLE_GM_CAN_FAIL(clipped_thinrect, …)`
 * (256 × 256).
 *
 * Draws an AA-filled red rect that extends 0.5 px beyond a 5-px-tall anti-aliased
 * clip, into a small (10 × 10) off-screen surface, then zooms that surface up
 * (200 × 200, starting at y=10) to make the single-pixel thin-rect / clip
 * interaction visible.
 *
 * The upstream GM returns `DrawResult::kSkip` when `canvas->makeSurface` is
 * unavailable (GPU-only feature). In the Kotlin port `makeSurface` always
 * succeeds (falls back to `SkSurface.MakeRaster`), so the skip branch is
 * never taken.
 */
public class ClippedThinRectGM : GM() {

    override fun getName(): String = "clipped_thinrect"
    override fun getISize(): SkISize = SkISize.Make(256, 256)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Upstream: canvas->makeSurface(canvas->imageInfo().makeWH(10, 10))
        // We don't expose SkCanvas.imageInfo() — use MakeN32Premul(10, 10)
        // which matches the default raster surface colour type/alpha.
        val info = SkImageInfo.MakeN32Premul(10, 10)
        val zoomed: SkSurface = c.makeSurface(info) ?: SkSurface.MakeRaster(info)
        val zoomedCanvas = zoomed.canvas

        val p = SkPaint().apply {
            color = SK_ColorRED
            isAntiAlias = true
            style = SkPaint.Style.kFill_Style
        }

        zoomedCanvas.save()
        // clipRect with doAntialias=true — 5-px tall clip at y=5
        zoomedCanvas.clipRect(SkRect.MakeXYWH(0f, 5f, 256f, 10f), doAntiAlias = true)
        // rect whose bottom edge (y=5.5) extends 0.5 px below the top of the clip
        zoomedCanvas.drawRect(SkRect.MakeXYWH(0f, 0f, 100f, 5.5f), p)
        zoomedCanvas.restore()

        // Zoom-in: render the 10×10 surface at 200×200 starting at y=10.
        // Should show one line of red from the 1/2-px coverage, not two lines.
        val img = zoomed.makeImageSnapshot()
        val src = SkRect.MakeIWH(img.width, img.height)
        val dst = SkRect.MakeXYWH(0f, 10f, 200f, 200f)
        c.drawImageRect(img, src, dst, SkSamplingOptions.Default)
    }
}
