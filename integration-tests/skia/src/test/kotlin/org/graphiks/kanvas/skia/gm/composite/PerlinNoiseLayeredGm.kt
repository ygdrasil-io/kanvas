package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/perlinnoise.cpp::PerlinNoiseLayeredGM` (500 × 500).
 *
 * Regression test for crbug/40045485 (Intel GPUs corrupting perlin noise
 * inside saveLayers). Draws fractal noise through two stacked saveLayers.
 * @see https://github.com/google/skia/blob/main/gm/perlinnoise.cpp
 */
class PerlinNoiseLayeredGm : SkiaGm {
    override val name = "perlinnoise_layered"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 500
    override val height = 500

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val shader = Shader.FractalNoise(0.3f, 0.3f, 1, 4, null)
        val noisePaint = Paint(shader = shader)
        val fullRect = Rect(0f, 0f, width.toFloat(), height.toFloat())

        // First layer: explicit (default) paint
        canvas.saveLayer(null, Paint())
        canvas.drawRect(fullRect, noisePaint)
        canvas.restore()

        // Second layer: (nullptr, nullptr)
        canvas.saveLayer(null, null)
        canvas.drawRect(fullRect, noisePaint)
        canvas.restore()
    }
}
