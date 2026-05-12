package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkColorFilter
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkColorGetB
import org.skia.foundation.SkColorGetG
import org.skia.foundation.SkColorGetR
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/colorfilters.cpp::ColorFiltersGM`
 * (`DEF_GM(return new ColorFiltersGM;)`, registered name
 * `"lightingcolorfilter"`, 620 × 430).
 *
 * Renders a 600 × 50 rainbow linear-gradient bar through 7
 * [SkColorFilters.Lighting]-style filters stacked vertically with a
 * 10-pixel gutter and 10-pixel top / left padding :
 *
 *  1. `nullptr` filter (baseline gradient).
 *  2. `Lighting(0xFF0000, 0)` — preserve only R.
 *  3. `Lighting(0x00FF00, 0)` — preserve only G.
 *  4. `Lighting(0x0000FF, 0)` — preserve only B.
 *  5. `Lighting(0x000000, 0xFF0000)` — black source + add R.
 *  6. `Lighting(0x000000, 0x00FF00)` — black source + add G.
 *  7. `Lighting(0x000000, 0x0000FF)` — black source + add B.
 *
 * **Adaptation** — `SkColorFilters::Lighting(mul, add)` lives upstream
 * as `R' = R · mul/255 + add`, `G' = G · mul/255 + add`, `B' = B · mul/255 + add`,
 * `A' = A`. `:kanvas-skia` doesn't expose a `Lighting` factory yet —
 * we synthesise the equivalent through [SkColorFilters.Matrix] using
 * the same per-channel matrix upstream's
 * `SkColorMatrix::setScale + postTranslate` builds (see
 * `src/effects/SkColorMatrixFilter.cpp::SkColorFilters::Lighting`).
 *
 * C++ original :
 * ```cpp
 * class ColorFiltersGM : public skiagm::GM {
 *     SkString getName() const override { return SkString("lightingcolorfilter"); }
 *     SkISize getISize() override { return {620, 430}; }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkRect r = {0, 0, 600, 50};
 *         SkPaint paint;
 *         paint.setShader(make_shader(r));
 *
 *         const struct { InstallPaint fProc; uint32_t fData0, fData1; } rec[] = {
 *             { install_nothing, 0, 0 },
 *             { install_lighting, 0xFF0000, 0 },
 *             { install_lighting, 0x00FF00, 0 },
 *             { install_lighting, 0x0000FF, 0 },
 *             { install_lighting, 0x000000, 0xFF0000 },
 *             { install_lighting, 0x000000, 0x00FF00 },
 *             { install_lighting, 0x000000, 0x0000FF },
 *         };
 *
 *         canvas->translate(10, 10);
 *         for (size_t i = 0; i < std::size(rec); ++i) {
 *             rec[i].fProc(&paint, rec[i].fData0, rec[i].fData1);
 *             canvas->drawRect(r, paint);
 *             canvas->translate(0, r.height() + 10);
 *         }
 *     }
 * };
 * ```
 */
public class ColorFiltersGM : GM() {

    override fun getName(): String = "lightingcolorfilter"

    override fun getISize(): SkISize = SkISize.Make(620, 430)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val r = SkRect.MakeLTRB(0f, 0f, 600f, 50f)

        val paint = SkPaint().apply { shader = makeShader(r) }

        // (mul, add) pairs — `null` mul means "no filter (baseline)".
        val rec: Array<Pair<Int?, Int>> = arrayOf(
            null to 0,
            0xFF0000 to 0,
            0x00FF00 to 0,
            0x0000FF to 0,
            0x000000 to 0xFF0000,
            0x000000 to 0x00FF00,
            0x000000 to 0x0000FF,
        )

        c.translate(10f, 10f)
        for ((mul, add) in rec) {
            paint.colorFilter = if (mul == null) null else lighting(mul, add)
            c.drawRect(r, paint)
            c.translate(0f, r.height() + 10f)
        }
    }

    /**
     * `make_shader(bounds)` — upstream's red→green→blue→black→cyan→
     * magenta→yellow linear gradient spanning the rect's diagonal.
     */
    private fun makeShader(bounds: SkRect): SkLinearGradient = SkLinearGradient.Make(
        SkPoint(bounds.left, bounds.top),
        SkPoint(bounds.right, bounds.bottom),
        intArrayOf(
            0xFFFF0000.toInt(),   // red
            0xFF00FF00.toInt(),   // green
            0xFF0000FF.toInt(),   // blue
            0xFF000000.toInt(),   // black
            0xFF00FFFF.toInt(),   // cyan
            0xFFFF00FF.toInt(),   // magenta
            0xFFFFFF00.toInt(),   // yellow
        ),
        null,
        SkTileMode.kClamp,
    )

    /**
     * Synthesises `SkColorFilters::Lighting(mul, add)` —
     *
     * Upstream (`src/effects/SkColorMatrixFilter.cpp`) :
     * ```cpp
     * SkColorMatrix m;
     * m.setScale(mul.R/255, mul.G/255, mul.B/255, 1);
     * m.postTranslate(add.R/255, add.G/255, add.B/255, 0);
     * return SkColorFilters::Matrix(m);
     * ```
     *
     * The resulting per-channel matrix (row-major, RGBA-bias layout) is :
     * ```
     * [mulR/255,        0,        0, 0, addR/255]
     * [       0, mulG/255,        0, 0, addG/255]
     * [       0,        0, mulB/255, 0, addB/255]
     * [       0,        0,        0, 1,        0]
     * ```
     *
     * `mul` and `add` are packed as RGB triplets (lower 24 bits of an
     * ARGB word — alpha ignored, matching upstream's behaviour).
     */
    private fun lighting(mul: Int, add: Int): SkColorFilter {
        val mulR = SkColorGetR(mul) / 255f
        val mulG = SkColorGetG(mul) / 255f
        val mulB = SkColorGetB(mul) / 255f
        val addR = SkColorGetR(add) / 255f
        val addG = SkColorGetG(add) / 255f
        val addB = SkColorGetB(add) / 255f
        val matrix = floatArrayOf(
            mulR, 0f,   0f,   0f, addR,
            0f,   mulG, 0f,   0f, addG,
            0f,   0f,   mulB, 0f, addB,
            0f,   0f,   0f,   1f, 0f,
        )
        return SkColorFilters.Matrix(matrix)
    }
}
