package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.effects.SkOverdrawColorFilter
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSetARGB
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkISize

/**
 * Port of upstream Skia's
 * [`gm/overdrawcolorfilter.cpp`](https://github.com/google/skia/blob/main/gm/overdrawcolorfilter.cpp).
 *
 * Upstream draws seven 100×100 alpha tiles (`α = 0, 1, 2, 3, 4, 5, 6`)
 * laid out in a 2×4 grid (one cell left blank), each through an
 * [SkOverdrawColorFilter] keyed by the palette
 * `{0x80FF0000, 0x8000FF00, 0x800000FF, 0x80FFFF00, 0x8000FFFF, 0x80FF00FF}`.
 * The tiles are `kAlpha_8` `SkBitmap`s — their only payload is the alpha
 * byte, decoded by the colour filter into one of the six palette
 * entries (or transparent for `α = 0`).
 *
 * **Partial port** : `kAlpha_8` bitmaps are **not yet supported** by
 * `:kanvas-skia`'s [SkBitmap] (see Phase G4a in
 * `MIGRATION_PLAN_GM_PORT.md`). We substitute with `kRGBA_8888` tiles
 * that carry the alpha byte in their `A` channel and zero in `RGB` —
 * functionally equivalent for this filter (which only consults
 * `SkColor.alpha`), but the source-pixel encoding (premul vs the
 * upstream Alpha8 unpacking) differs slightly so iso-pixel parity
 * with `original-888/overdrawcolorfilter.png` is not expected. When
 * Phase G4a lands the substitution can be removed.
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

        // Upstream draws α = 0..6 at the seven (x, y) positions below.
        val tiles = arrayOf(
            0 to (0f to 0f),
            1 to (0f to 100f),
            2 to (0f to 200f),
            3 to (0f to 300f),
            4 to (100f to 0f),
            5 to (100f to 100f),
            6 to (100f to 200f),
        )
        for ((alpha, pos) in tiles) {
            val tile = makeAlphaTile(100, 100, alpha)
            c.drawImage(tile.asImage(), pos.first, pos.second, sampling, paint)
        }
    }

    /**
     * `kRGBA_8888` stand-in for upstream's `SkImageInfo::MakeA8` tile.
     * All pixels are `SkColorSetARGB(alpha, 0, 0, 0)` — the only data
     * the [SkOverdrawColorFilter] consumes is the alpha byte.
     */
    private fun makeAlphaTile(w: Int, h: Int, alpha: Int): SkBitmap {
        val bm = SkBitmap(w, h)
        val packed = SkColorSetARGB(alpha and 0xFF, 0, 0, 0)
        for (y in 0 until h) {
            for (x in 0 until w) {
                bm.setPixel(x, y, packed)
            }
        }
        return bm
    }
}
