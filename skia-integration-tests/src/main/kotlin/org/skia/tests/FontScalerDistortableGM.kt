package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontArguments
import org.skia.foundation.SkFontMetrics
import org.skia.foundation.SkFontVariation
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTypeface
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/fontscalerdistortable.cpp::FontScalerDistortableGM`
 * (`fontscalerdistortable`, 550 × 700).
 *
 * Renders a 2 × 5 grid of variable-font instances : each cell exercises
 * a different `wght` axis design value (linearly stepped between
 * `axisMin = 0.5` and `axisMax = 2.0` across the 10 cells), drawn as
 * the string `"abc"` at point sizes 6 → 22.
 *
 * The pipeline upstream :
 *  1. load `fonts/Distortable.ttf` (a variable test-font carrying a
 *     `wght` axis) via `ToolUtils::CreateTypefaceFromResource` ;
 *  2. for each `(row, col)` cell, build an `SkFontArguments` carrying
 *     a `VariationPosition::Coordinate('wght', styleValue)` and call
 *     `distortable->makeClone(args)` to obtain the per-cell typeface ;
 *  3. draw 17 rows of `"abc"` (sizes 6 .. 22 px) into a column starting
 *     at `(30 + col*100, 20)` after a `rotate(col*5°)` applied around
 *     `(x, 200)` — visually shears each column to test scaler stability.
 *  4. between row 0 and row 1 the GM enables `font.setSubpixel(true)` /
 *     `setLinearMetrics(true)` / `setBaselineSnap(false)` to exercise
 *     the subpixel-positioning code paths.
 *
 * **OpenType variable-font caveat** : `Distortable.ttf` is a variable
 * font whose visible difference between `wght` instances comes from the
 * OpenType `gvar` table. The pure Kotlin backend currently supports the
 * simple-glyph subset of `gvar`, so this GM is a structural guard for
 * clone/axis plumbing and a fidelity ratchet for future variable-outline
 * work. Score < 100% is expected until the remaining variable-font
 * interpolation gaps are closed.
 */
public class FontScalerDistortableGM : GM() {

    override fun getName(): String = "fontscalerdistortable"
    override fun getISize(): SkISize = SkISize.Make(550, 700)

    init {
        setBGColor(0xFFFFFFFF.toInt())
    }

    private data class Info(
        val distortable: SkTypeface?,
        val axisTag: SkFontVariation.Tag,
        val axisMin: Float,
        val axisMax: Float,
    )

    private lateinit var info: Info

    /**
     * 2 × 5 grid of typeface clones, one per cell, each at a different
     * `wght` axis design value. Lazily filled on the first [onDraw].
     */
    private val typeface: Array<Array<SkTypeface?>> =
        Array(ROWS) { arrayOfNulls(COLS) }

    override fun onOnceBeforeDraw() {
        // Load Distortable.ttf — a 16-KB upstream test font carrying a
        // `wght` variable axis. Falls back to the default portable
        // typeface if the resource is missing (matches upstream's
        // `if (!fInfo.distortable) fInfo.distortable = DefaultPortableTypeface()`).
        val distortable = ToolUtils.CreateTypefaceFromResource("fonts/Distortable.ttf")
            ?: ToolUtils.DefaultPortableTypeface()
        info = Info(
            distortable = distortable,
            axisTag = SkFontVariation.Tag.of("wght"),
            axisMin = 0.5f,
            axisMax = 2.0f,
        )

        for (row in 0 until ROWS) {
            for (col in 0 until COLS) {
                // Linear step across the [axisMin, axisMax] range,
                // mirroring `SkScalarInterp(min, max, t)` upstream :
                //   t = (row*COLS + col) / (ROWS*COLS).
                val t = (row * COLS + col).toFloat() / (ROWS * COLS).toFloat()
                val styleValue = info.axisMin + t * (info.axisMax - info.axisMin)
                val coords = listOf(
                    SkFontArguments.VariationPosition.Coordinate.of(info.axisTag, styleValue),
                    SkFontArguments.VariationPosition.Coordinate.of(info.axisTag, styleValue),
                )
                val args = SkFontArguments().setVariationDesignPosition(
                    SkFontArguments.VariationPosition(coords)
                )
                typeface[row][col] = distortable.makeClone(args)
            }
        }
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val paint = SkPaint().apply { isAntiAlias = true }
        val font = SkFont().apply { edging = SkFont.Edging.kSubpixelAntiAlias }
        val text = "abc"
        val metrics = SkFontMetrics()

        for (row in 0 until ROWS) {
            for (col in 0 until COLS) {
                val x = 10f
                var y = 20f

                font.typeface = typeface[row][col] ?: ToolUtils.DefaultPortableTypeface()

                c.save()
                try {
                    c.translate(30f + col * 100f, 20f)
                    c.rotate(col * 5f, x, y * 10f)

                    // Tick mark column — a tall thin black bar to the
                    // left of each cell so the eye can compare baseline
                    // alignment across the cell stack.
                    val barPaint = SkPaint().apply { isAntiAlias = true }
                    val r = SkRect(0f, 0f, 0f, 0f)
                    r.setLTRB(x - 3f, 15f, x - 1f, 280f)
                    c.drawRect(r, barPaint)

                    // 17 rows of `"abc"` at sizes 6 → 22 px, advancing
                    // `y` by the recommended line spacing per row.
                    var ps = 6
                    while (ps <= 22) {
                        font.size = ps.toFloat()
                        c.drawSimpleText(
                            text,
                            text.length,
                            org.skia.foundation.SkTextEncoding.kUTF8,
                            x,
                            y,
                            font,
                            paint,
                        )
                        y += font.getMetrics(metrics)
                        ps++
                    }
                } finally {
                    c.restore()
                }
            }
            // Between row 0 and row 1 (and notionally onward), the
            // upstream GM advances the canvas down by 360 and turns on
            // subpixel + linearMetrics + baselineSnap=false so the
            // second row exercises the subpixel scaler code path.
            c.translate(0f, 360f)
            font.isSubpixel = true
            font.isLinearMetrics = true
            font.isBaselineSnap = false
        }
    }

    public companion object {
        private const val ROWS: Int = 2
        private const val COLS: Int = 5
    }
}
