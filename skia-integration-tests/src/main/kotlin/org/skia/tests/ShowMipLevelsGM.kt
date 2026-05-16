package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorBLUE
import org.skia.math.SK_ColorGREEN
import org.skia.math.SK_ColorRED
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkMipmapBuilder
import org.skia.foundation.SkMipmapMode
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's
 * [`gm/showmiplevels.cpp`](https://github.com/google/skia/blob/main/gm/showmiplevels.cpp)
 * `showmiplevels_explicit` GM (1130 × 970).
 *
 * Loads `images/ship.png` (188 × 180), promotes it to a raster image,
 * and uses [SkMipmapBuilder] (R-final.5) to **explicitly** colour each
 * mip level a solid red / green / blue cycle. The hand-painted pyramid
 * is attached to the source image via [SkMipmapBuilder.attachTo] —
 * downstream sampling at every mipmap mode therefore reveals which
 * level is selected by the rasteriser :
 *  - rows 0–1 (`SkMipmapMode.kNone`) draw the ship at every scale
 *    (no mips consulted) ; row 0 (`kNearest`) blockier than row 1
 *    (`kLinear`).
 *  - rows 2–3 (`kNearest` mip mode) snap to one of the painted levels
 *    — solid red / green / blue for everything below the second column.
 *  - rows 4–5 (`kLinear` mip mode) blend across painted levels.
 *
 * **kanvas-skia divergence.** Upstream uses
 * `SkSurfaces::WrapPixels(builder.level(i))` to obtain a surface that
 * paints back into the builder. Our [SkSurfaces.WrapPixels] copies the
 * pixmap into a fresh bitmap (no zero-copy on the JVM raster backend),
 * which would silently discard the per-level draw. We use the
 * kanvas-skia [SkMipmapBuilder.levelSurface] extension that returns a
 * surface backed directly by the builder's per-level bitmap — same
 * draw-into-builder semantics, just routed through
 * `SkSurface.MakeRasterDirect(builder.bitmap)` instead of `WrapPixels`.
 */
public class ShowMipLevelsGM : GM() {

    override fun getName(): String = "showmiplevels_explicit"
    override fun getISize(): SkISize = SkISize.Make(1130, 970)

    private var image: org.skia.foundation.SkImage? = null

    override fun onOnceBeforeDraw() {
        val src = ToolUtils.GetResourceAsImage("images/ship.png") ?: return
        // The C++ GM calls `makeRasterImage(nullptr)` to ensure the
        // source is plain raster — kanvas-skia's [SkImage] is always
        // raster, so this is a no-op for us.
        val builder = SkMipmapBuilder(src.imageInfo())
        val colors = intArrayOf(SK_ColorRED, SK_ColorGREEN, SK_ColorBLUE)
        for (i in 0 until builder.countLevels()) {
            val surf = builder.levelSurface(i) ?: continue
            surf.canvas.drawColor(colors[i % colors.size])
        }
        image = builder.attachTo(src) ?: src
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.drawColor(0xFFDDDDDD.toInt())
        c.translate(10f, 10f)

        val mipModes = arrayOf(SkMipmapMode.kNone, SkMipmapMode.kNearest, SkMipmapMode.kLinear)
        val filterModes = arrayOf(SkFilterMode.kNearest, SkFilterMode.kLinear)
        for (mm in mipModes) {
            for (fm in filterModes) {
                c.translate(0f, drawDownscaling(c, SkSamplingOptions(fm, mm)))
            }
        }
    }

    private fun drawDownscaling(canvas: SkCanvas, sampling: SkSamplingOptions): Float {
        val img = image ?: return 0f
        canvas.save()
        try {
            val paint = SkPaint()
            val r = SkRect.MakeWH(150f, 150f)
            var scale = 1f
            while (scale >= 0.1f) {
                val matrix = SkMatrix.MakeScale(scale, scale)
                paint.shader = img.makeShader(SkTileMode.kRepeat, SkTileMode.kRepeat, sampling, matrix)
                canvas.drawRect(r, paint)
                canvas.translate(r.width() + 10f, 0f)
                scale *= 0.7f
            }
            return r.height() + 10f
        } finally {
            canvas.restore()
        }
    }
}
