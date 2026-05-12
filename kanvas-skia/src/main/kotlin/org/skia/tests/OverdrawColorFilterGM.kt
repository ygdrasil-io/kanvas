package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.effects.SkOverdrawColorFilter
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSetARGB
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkISize

/**
 * Port of upstream Skia's
 * [`gm/overdrawcolorfilter.cpp`](https://github.com/google/skia/blob/main/gm/overdrawcolorfilter.cpp)
 * (registered `DEF_GM` name `overdrawcolorfilter`).
 *
 * Draws seven 100×100 alpha tiles (α = 0, 1, 2, 3, 4, 5, 6) laid out in a
 * 2×4 grid (one cell left blank), each through an [SkOverdrawColorFilter]
 * keyed by the palette
 * `{0x80FF0000, 0x8000FF00, 0x800000FF, 0x80FFFF00, 0x8000FFFF, 0x80FF00FF}`.
 * The tiles are `kAlpha_8` `SkBitmap`s — their only payload is the alpha
 * byte, decoded by the colour filter into one of the six palette entries
 * (or transparent for α = 0).
 *
 * C++ original:
 * ```cpp
 * static const SkColor colors[SkOverdrawColorFilter::kNumColors] = {
 *     0x80FF0000, 0x8000FF00, 0x800000FF, 0x80FFFF00, 0x8000FFFF, 0x80FF00FF,
 * };
 * SkPaint paint;
 * paint.setColorFilter(SkOverdrawColorFilter::MakeWithSkColors(colors));
 * SkSamplingOptions sampling;
 *
 * SkImageInfo info = SkImageInfo::MakeA8(100, 100);
 * SkBitmap bitmap;
 * bitmap.allocPixels(info);
 * bitmap.eraseARGB(0, 0, 0, 0);
 * canvas->drawImage(bitmap.asImage(), 0, 0, sampling, &paint);
 * // ... repeats for α = 1..6 at (0,100), (0,200), (0,300), (100,0),
 * //                            (100,100), (100,200).
 * ```
 *
 * Now that Phase G4a lands `kAlpha_8` `SkBitmap` accessors, we follow
 * upstream verbatim — no RGBA stand-in.
 */
public class OverdrawColorFilterGM : GM() {

    override fun getName(): String = "overdrawcolorfilter"
    override fun getISize(): SkISize = SkISize.Make(200, 400)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val palette = intArrayOf(
            0x80FF0000.toInt(),
            0x8000FF00.toInt(),
            0x800000FF.toInt(),
            0x80FFFF00.toInt(),
            0x8000FFFF.toInt(),
            0x80FF00FF.toInt(),
        )

        val paint = SkPaint().apply {
            colorFilter = SkOverdrawColorFilter.MakeWithSkColors(palette)
        }
        val sampling = SkSamplingOptions.Default

        // Single Alpha_8 bitmap reused — upstream re-erases the same buffer
        // before each drawImage call (matches `SkBitmap bitmap; bitmap.allocPixels(info);`
        // followed by seven `bitmap.eraseARGB(N, 0, 0, 0); canvas->drawImage(...)`).
        val bitmap = SkBitmap.allocPixels(SkImageInfo.MakeA8(100, 100))

        // Upstream draws α = 0..6 at the seven (x, y) positions below.
        // `eraseARGB(N, 0, 0, 0)` == `eraseColor(SkColorSetARGB(N, 0, 0, 0))`
        // — on a kAlpha_8 bitmap only the alpha byte survives.
        val positions = arrayOf(
            0 to (0f to 0f),
            1 to (0f to 100f),
            2 to (0f to 200f),
            3 to (0f to 300f),
            4 to (100f to 0f),
            5 to (100f to 100f),
            6 to (100f to 200f),
        )
        for ((alpha, pos) in positions) {
            bitmap.eraseColor(SkColorSetARGB(alpha, 0, 0, 0))
            c.drawImage(bitmap.asImage(), pos.first, pos.second, sampling, paint)
        }
    }
}
