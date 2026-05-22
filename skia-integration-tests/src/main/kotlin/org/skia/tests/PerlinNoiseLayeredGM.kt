package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkShaders
import org.graphiks.math.SkColorMatrix
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/perlinnoise.cpp::PerlinNoiseLayeredGM` (500 × 500).
 *
 * Regression test for crbug/40045485 (Intel GPUs corrupting perlin noise
 * inside saveLayers). Builds a `SkImageFilters::ColorFilter` chain that
 * funnels an identity colour-matrix transform over a perlin-noise shader
 * source, then `drawPaint`s through it inside two stacked saveLayers :
 *  - first saveLayer carries a (default) paint ;
 *  - second saveLayer is `(nullptr, nullptr)` — the no-bounds /
 *    no-restore-paint variant.
 *
 * On the raster backend the two layers stack identically ; the original
 * bug only reproduced on Intel GPUs.
 */
public class PerlinNoiseLayeredGM : GM() {

    override fun getName(): String = "perlinnoise_layered"
    override fun getISize(): SkISize = SkISize.Make(500, 500)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val perlin = SkImageFilters.ColorFilter(
            cf = SkColorFilters.Matrix(SkColorMatrix()),
            input = SkImageFilters.Shader(
                SkShaders.MakeFractalNoise(0.3f, 0.3f, 1, 4f),
            ),
        )

        // First layer : explicit (default) paint.
        c.saveLayer(null, SkPaint())
        run {
            val p = SkPaint().apply { imageFilter = perlin }
            c.drawPaint(p)
        }
        c.restore()

        // Second layer : (nullptr, nullptr).
        c.saveLayer(null, null)
        run {
            val p = SkPaint().apply { imageFilter = perlin }
            c.drawPaint(p)
        }
        c.restore()
    }
}
