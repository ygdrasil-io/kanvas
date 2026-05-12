package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorBLACK
import org.skia.foundation.SK_ColorBLUE
import org.skia.foundation.SK_ColorGREEN
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SkConicalGradient
import org.skia.foundation.SkFont
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRadialGradient
import org.skia.foundation.SkShader
import org.skia.foundation.SkSweepGradient
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/gradients_degenerate.cpp::DegenerateGradientGM`
 * (`getName() = "degenerate_gradients"`).
 *
 * Stress-tests every gradient kind with degenerate parameters that the
 * sampler must treat as a "single-stop fill". Hard stops at
 * `{0, 0, 0.5, 1, 1}` make the red and green borders invisible for
 * repeating tile modes, so the average colour is white/blue/black.
 *
 * Each row exercises one gradient maker (linear, radial, sweep-0,
 * sweep-45, 2pt-conic-0, 2pt-conic-1) under four tile modes
 * (decal, repeat, mirror, clamp), drawn with a 2-px stroke+fill.
 *
 * Some makers (radial r=0, sweep with start==end, 2pt-conic
 * fully-collapsed) construct a degenerate shader that
 * `:kanvas-skia` cannot synthesise (the `require()` guards in
 * `SkRadialGradient.Make` / `SkSweepGradient.Make` reject them).
 * When the maker returns `null` we leave `paint.shader` unset, so the
 * paint draws its solid colour (black, per `paint.color = SK_ColorBLACK`)
 * — matching upstream's degenerate-gradient fallback for clamp mode
 * (the "average colour" path). Other tile modes won't visually match
 * upstream for these rows, which is the dominant cause of the lower
 * similarity score here.
 */
public class DegenerateGradientGM : GM() {

    override fun getName(): String = "degenerate_gradients"
    override fun getISize(): SkISize = SkISize.Make(800, 800)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.translate((3 * TILE_GAP).toFloat(), (3 * TILE_GAP).toFloat())
        drawTileHeader(c)

        drawRow(c, "linear: empty, blue, blue, green", ::makeLinear)
        drawRow(c, "radial:  empty, blue, blue, green", ::makeRadial)
        drawRow(c, "sweep-0: empty, blue, blue, green", ::makeSweepZeroAng)
        drawRow(c, "sweep-45: empty, blue, blue, red 45 degree sector then green", ::makeSweep)
        drawRow(c, "2pt-conic-0: empty, blue, blue, green", ::make2ptConicZeroRad)
        drawRow(c, "2pt-conic-1: empty, blue, blue, full red circle on green", ::make2ptConic)
    }

    private fun drawTileHeader(canvas: SkCanvas) {
        canvas.save()
        val font = SkFont(ToolUtils.DefaultPortableTypeface(), 12f)
        for (i in TILE_NAMES.indices) {
            canvas.drawString(TILE_NAMES[i], 0f, 0f, font, SkPaint())
            canvas.translate((TILE_SIZE + TILE_GAP).toFloat(), 0f)
        }
        canvas.restore()
        canvas.translate(0f, (2 * TILE_GAP).toFloat())
    }

    private fun drawRow(canvas: SkCanvas, desc: String, factory: (SkTileMode) -> SkShader?) {
        canvas.save()
        val text = SkPaint().apply { isAntiAlias = true }
        val font = SkFont(ToolUtils.DefaultPortableTypeface(), 12f)

        canvas.translate(0f, TILE_GAP.toFloat())
        canvas.drawString(desc, 0f, 0f, font, text)
        canvas.translate(0f, TILE_GAP.toFloat())

        val paint = SkPaint().apply {
            color = SK_ColorBLACK
            style = SkPaint.Style.kStrokeAndFill_Style
            strokeWidth = 2.0f
        }

        for (i in TILE_MODES.indices) {
            paint.shader = factory(TILE_MODES[i])
            canvas.drawRect(SkRect.MakeWH(TILE_SIZE.toFloat(), TILE_SIZE.toFloat()), paint)
            canvas.translate((TILE_SIZE + TILE_GAP).toFloat(), 0f)
        }

        canvas.restore()
        canvas.translate(0f, (3 * TILE_GAP + TILE_SIZE).toFloat())
    }

    // Same position — degenerate linear.
    private fun makeLinear(mode: SkTileMode): SkShader? =
        SkLinearGradient.Make(CENTER, CENTER, COLORS, POS, mode)

    // Radius 0 — `SkRadialGradient.Make` rejects this with `require(radius > 0)`.
    private fun makeRadial(mode: SkTileMode): SkShader? = null

    // Start = end = 45° — `SkSweepGradient.Make` rejects `start < end`.
    private fun makeSweep(mode: SkTileMode): SkShader? = null

    // Start = end = 0° — same rejection.
    private fun makeSweepZeroAng(mode: SkTileMode): SkShader? = null

    // Same position, radii both TILE_SIZE / 2 — equal nonzero radii at
    // same centre is itself a degenerate conic that
    // `SkConicalGradient.Make` collapses to null.
    private fun make2ptConic(mode: SkTileMode): SkShader? =
        SkConicalGradient.Make(
            CENTER, (TILE_SIZE / 2).toFloat(),
            CENTER, (TILE_SIZE / 2).toFloat(),
            COLORS, POS, mode,
        )

    // Same position, both radii 0 — fully degenerate; returns null.
    private fun make2ptConicZeroRad(mode: SkTileMode): SkShader? =
        SkConicalGradient.Make(
            CENTER, 0f,
            CENTER, 0f,
            COLORS, POS, mode,
        )

    private companion object {
        val COLORS: IntArray = intArrayOf(
            SK_ColorRED, SK_ColorWHITE, SK_ColorBLUE, SK_ColorBLACK, SK_ColorGREEN,
        )
        val POS: FloatArray = floatArrayOf(0.0f, 0.0f, 0.5f, 1.0f, 1.0f)

        val TILE_MODES: Array<SkTileMode> = arrayOf(
            SkTileMode.kDecal, SkTileMode.kRepeat, SkTileMode.kMirror, SkTileMode.kClamp,
        )
        val TILE_NAMES: Array<String> = arrayOf("decal", "repeat", "mirror", "clamp")

        const val TILE_SIZE: Int = 100
        const val TILE_GAP: Int = 10

        val CENTER: SkPoint = SkPoint((TILE_SIZE / 2).toFloat(), (TILE_SIZE / 2).toFloat())
    }
}
