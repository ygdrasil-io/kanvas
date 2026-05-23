package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/bitmapshader.cpp::DEF_SIMPLE_GM(hugebitmapshader, ...)`.
 *
 * Creates a 1 × 60 000 alpha-only (`kAlpha_8`) bitmap where row `i` has
 * alpha value `i & 0xFF` — producing a repeating 0→255 alpha ramp. The
 * bitmap is wrapped in a `kMirror/kMirror` shader, the paint color is set
 * to `SK_ColorRED` with antiAlias on, and a circle (50, 50, 50) is drawn.
 *
 * The upstream GM's primary purpose is GPU regression testing : on GL it
 * uses `maxTextureSize + 1` as the height so the driver must fall back
 * from a single texture to tiled/mip rendering rather than drawing nothing.
 * In the CPU/raster path the height is fixed at 60 000, which is small
 * enough to allocate comfortably (60 KB of alpha data) and still exercises
 * the mirror-tiling and A8-shader pipelines.
 *
 * Reference image: `hugebitmapshader.png`, 100 × 100, default white BG.
 */
public class HugeBitmapShaderGM : GM() {

    override fun getName(): String = "hugebitmapshader"
    override fun getISize(): SkISize = SkISize.Make(100, 100)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val bitmapW = 1
        val bitmapH = 60_000

        // Build a 1 × 60 000 A8 bitmap : row i has alpha = i & 0xFF.
        // This produces a repeating 0→255 ramp that the kMirror shader
        // then mirrors — resulting in alternating 0→255→255→0 alpha bands.
        val bitmap = SkBitmap.allocPixels(SkImageInfo.MakeA8(bitmapW, bitmapH))
        for (i in 0 until bitmapH) {
            val alpha = i and 0xFF
            // setPixel for kAlpha_8 reads SkColorGetA(c), so pack alpha
            // into the A channel of the ARGB int.
            bitmap.setPixel(0, i, alpha shl 24)
        }

        val paint = SkPaint().apply {
            shader = bitmap.makeShader(
                tileX = SkTileMode.kMirror,
                tileY = SkTileMode.kMirror,
                sampling = SkSamplingOptions.Default,
            )
            color = SK_ColorRED
            isAntiAlias = true
        }

        c.drawCircle(50f, 50f, 50f, paint)
    }
}
