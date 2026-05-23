package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions

/**
 * Port of Skia's `gm/imagefilters.cpp::DEF_SIMPLE_GM(fast_slow_blurimagefilter, …)`
 * (registered as `fast_slow_blurimagefilter`, 620 × 260).
 *
 * Compares blur when the draw is tightly clipped ("fast" path) versus
 * slightly looser clip (clip outset by 1 — "slower" path). Expects the
 * two to produce the same pixels modulo the extra border pixels when the
 * clip is larger.
 */
public class FastSlowBlurImageFilterGM : GM() {

    override fun getName(): String = "fast_slow_blurimagefilter"
    override fun getISize(): SkISize = SkISize.Make(620, 260)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val image = makeImage(c)
        val r = SkRect.MakeIWH(image.width, image.height)

        c.translate(10f, 10f)
        var sigma = 8f
        while (sigma <= 128f) {
            val paint = SkPaint().apply {
                imageFilter = SkImageFilters.Blur(sigma, sigma, null)
            }

            c.save()
            // outset = 0: tight clip (fast path); outset = 1: clip larger than image (slower path)
            for (outset in 0..1) {
                c.save()
                c.clipRect(r.makeOutset(outset.toFloat(), outset.toFloat()))
                c.drawImage(image, 0f, 0f, SkSamplingOptions.Default, paint)
                c.restore()
                c.translate(0f, r.height() + 20f)
            }
            c.restore()
            c.translate(r.width() + 20f, 0f)

            sigma *= 2f
        }
    }

    private fun makeImage(canvas: SkCanvas) =
        run {
            val info = SkImageInfo.MakeN32Premul(100, 100)
            // Use canvas.makeSurface for a compatible surface, fall back to MakeRaster.
            val surface = canvas.makeSurface(info) ?: SkSurface.MakeRaster(info)
            surface.canvas.drawRect(SkRect.MakeXYWH(25f, 25f, 50f, 50f), SkPaint())
            surface.makeImageSnapshot()
        }
}
