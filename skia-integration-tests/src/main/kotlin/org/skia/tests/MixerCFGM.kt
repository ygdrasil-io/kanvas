package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkColor4f
import org.skia.foundation.SkColorFilter
import org.skia.foundation.SkColorFilters
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkLumaColorFilter
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSweepGradient
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect

/**
 * Port of upstream Skia's
 * [`gm/mixercolorfilter.cpp`](https://github.com/google/skia/blob/main/gm/mixercolorfilter.cpp)
 * (registered `DEF_GM` name `mixerCF`, tileSize = 200×250, tileCount = 5).
 *
 * Exercises `SkColorFilters::Lerp(t, cf0, cf1)` across three rows :
 *
 *  - Row 0 : `cf0 = nullptr` (identity), `cf1 = green tint`.
 *  - Row 1 : `cf0 = red tint`, `cf1 = nullptr` (identity).
 *  - Row 2 : `cf0 = red tint`, `cf1 = green tint`.
 *
 * Each row draws 5 tiles of a sweep gradient (red/green/blue/red around the
 * centre), each tile coloured by `paint.setColor4f` of a different
 * paintColor entry and filtered via `Lerp(i / (count - 1), cf0, cf1)`.
 *
 * The tint filter is the canonical Skia idiom : a 4×5 colour matrix
 * `[0,0,0,(hi-lo)/255, lo/255]` per channel, composed with
 * [SkLumaColorFilter.Make] so that the input's luminance becomes the
 * lerp factor between `lo` and `hi`. Identical to
 * [ComposeColorFilterGM]'s `MakeTintColorFilter` helper.
 *
 * Reference : `mixerCF.png`, 1200 × 900, white background.
 */
public class MixerCFGM : GM() {

    override fun getName(): String = "mixerCF"

    override fun getISize(): SkISize = SkISize.Make(
        (TILE_W * 1.2f * TILE_COUNT).toInt(),
        (TILE_H * 1.2f * 3).toInt(),
    )

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val gradColors = intArrayOf(
            SkColorSetARGB(0xFF, 0xFF, 0, 0),   // red
            SkColorSetARGB(0xFF, 0, 0xFF, 0),   // green
            SkColorSetARGB(0xFF, 0, 0, 0xFF),   // blue
            SkColorSetARGB(0xFF, 0xFF, 0, 0),   // red (close the sweep)
        )
        val sweep = SkSweepGradient.Make(
            center = SkPoint(TILE_W / 2f, TILE_H / 2f),
            startAngle = 0f, endAngle = 360f,
            colors = gradColors,
            positions = null,
            tileMode = SkTileMode.kClamp,
        )

        val paint = SkPaint().apply { shader = sweep }

        val cf0 = makeTintColorFilter(0xFF300000.toInt(), 0xFFA00000.toInt())  // red tint
        val cf1 = makeTintColorFilter(0xFF003000.toInt(), 0xFF00A000.toInt())  // green tint

        mixRow(c, paint, null, cf1)
        mixRow(c, paint, cf0, null)
        mixRow(c, paint, cf0, cf1)
    }

    private fun mixRow(
        canvas: SkCanvas,
        paint: SkPaint,
        cf0: SkColorFilter?,
        cf1: SkColorFilter?,
    ) {
        // Upstream cycles 4 paint colours across the row's `tileCount` tiles
        // to test how the paint colour flows through the colour-filter chain.
        val paintColors = arrayOf(
            SkColor4f(1f, 1f, 1f, 1f),    // opaque white
            SkColor4f(1f, 1f, 1f, 0.5f),  // translucent white
            SkColor4f(0.5f, 0.5f, 1f, 1f),    // opaque pale blue
            SkColor4f(0.5f, 0.5f, 1f, 0.5f),  // translucent pale blue
        )

        canvas.translate(0f, TILE_H * 0.1f)
        canvas.save()
        for (i in 0 until TILE_COUNT) {
            paint.color4f = paintColors[i % paintColors.size]
            val t = i.toFloat() / (TILE_COUNT - 1)
            paint.colorFilter = lerp(t, cf0, cf1)
            canvas.translate(TILE_W * 0.1f, 0f)
            canvas.drawRect(SkRect.MakeWH(TILE_W, TILE_H), paint)
            canvas.translate(TILE_W * 1.1f, 0f)
        }
        canvas.restore()
        canvas.translate(0f, TILE_H * 1.1f)
    }

    /**
     * Upstream `SkColorFilters::Lerp(t, cf0, cf1)` accepts nullptr arguments
     * as "the identity filter". Our [SkColorFilters.Lerp] requires non-null
     * inputs, so we substitute [IDENTITY_FILTER] (a 4×5 identity matrix) in
     * those slots. Yields the same per-pixel maths.
     */
    private fun lerp(t: Float, cf0: SkColorFilter?, cf1: SkColorFilter?): SkColorFilter? {
        if (cf0 == null && cf1 == null) return null
        val dst = cf0 ?: IDENTITY_FILTER
        val src = cf1 ?: IDENTITY_FILTER
        return SkColorFilters.Lerp(t, dst, src)
    }

    /**
     * Mirrors upstream's `MakeTintColorFilter(lo, hi)` — a tint matrix
     * `[0, 0, 0, (hi-lo)/255, lo/255]` per channel composed with
     * [SkLumaColorFilter] so that input luminance maps `lo..hi`.
     */
    private fun makeTintColorFilter(lo: Int, hi: Int): SkColorFilter {
        val rLo = SkColorGetR(lo); val gLo = SkColorGetG(lo)
        val bLo = SkColorGetB(lo); val aLo = SkColorGetA(lo)
        val rHi = SkColorGetR(hi); val gHi = SkColorGetG(hi)
        val bHi = SkColorGetB(hi); val aHi = SkColorGetA(hi)
        val tint = floatArrayOf(
            0f, 0f, 0f, (rHi - rLo) / 255f, rLo / 255f,
            0f, 0f, 0f, (gHi - gLo) / 255f, gLo / 255f,
            0f, 0f, 0f, (bHi - bLo) / 255f, bLo / 255f,
            0f, 0f, 0f, (aHi - aLo) / 255f, aLo / 255f,
        )
        return SkColorFilters.Compose(SkColorFilters.Matrix(tint), SkLumaColorFilter.Make())
    }

    private companion object {
        private const val TILE_W: Float = 200f
        private const val TILE_H: Float = 250f
        private const val TILE_COUNT: Int = 5

        /**
         * 4 × 5 identity colour matrix — `R'=R, G'=G, B'=B, A'=A`, no bias.
         * Used as the stand-in for upstream's `nullptr` colour-filter slot
         * in `SkColorFilters::Lerp(t, nullptr, x)` calls.
         */
        private val IDENTITY_FILTER: SkColorFilter = SkColorFilters.Matrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
    }
}
