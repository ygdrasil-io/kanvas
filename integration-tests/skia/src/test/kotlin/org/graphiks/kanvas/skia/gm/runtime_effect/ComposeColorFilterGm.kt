package org.graphiks.kanvas.skia.gm.runtime_effect

import org.graphiks.kanvas.gpu.renderer.wgsl.ComposeColorFilterWgsl
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/composecolorfilter.cpp::composeCF` (200 x 400).
 *
 * Draws a sweep gradient through composed color filters (Luma inner,
 * tint Matrix outer) using both direct Compose and SkSL compose path.
 *
 * @see https://github.com/google/skia/blob/main/gm/composecolorfilter.cpp
 */
class ComposeColorFilterGm : SkiaGm {
    override val name = "composeCF"
    override val renderFamily = RenderFamily.RUNTIME_EFFECT
    override val minSimilarity = 0.0
    override val width = 200
    override val height = 200

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val sweep = Shader.SweepGradient(
            Point(50f, 50f),
            stops = listOf(
                GradientStop(0f, Color.RED),
                GradientStop(0.33f, Color.GREEN),
                GradientStop(0.66f, Color.BLUE),
                GradientStop(1f, Color.RED),
            ),
        )

        val effect = RuntimeEffect.compile(ComposeColorFilterWgsl).getOrThrow()

        for (useSL in listOf(false, true)) {
            canvas.save()
            val cf0 = makeTintColorFilter(effect, 0xFF300000.toInt(), 0xFFA00000.toInt(), useSL)
            val cf1 = makeTintColorFilter(effect, 0xFF003000.toInt(), 0xFF00A000.toInt(), useSL)

            canvas.drawRect(Rect(0f, 0f, 100f, 100f), Paint(shader = sweep, colorFilter = cf0))
            canvas.translate(100f, 0f)
            canvas.drawRect(Rect(0f, 0f, 100f, 100f), Paint(shader = sweep, colorFilter = cf1))
            canvas.restore()
            canvas.translate(0f, 100f)
        }
    }

    private fun makeTintColorFilter(
        effect: RuntimeEffect, lo: Int, hi: Int, useSL: Boolean,
    ): ColorFilter {
        val rLo = (lo shr 16) and 0xFF; val gLo = (lo shr 8) and 0xFF
        val bLo = lo and 0xFF; val aLo = (lo shr 24) and 0xFF
        val rHi = (hi shr 16) and 0xFF; val gHi = (hi shr 8) and 0xFF
        val bHi = hi and 0xFF; val aHi = (hi shr 24) and 0xFF
        val tint = floatArrayOf(
            0f, 0f, 0f, (rHi - rLo) / 255f, rLo / 255f,
            0f, 0f, 0f, (gHi - gLo) / 255f, gLo / 255f,
            0f, 0f, 0f, (bHi - bLo) / 255f, bLo / 255f,
            0f, 0f, 0f, (aHi - aLo) / 255f, aLo / 255f,
        )
        val inner = ColorFilter.Luma
        val outer = ColorFilter.Matrix(tint)
        if (!useSL) return ColorFilter.Compose(outer, inner)
        return effect.makeColorFilter(UniformBlock {}, mapOf("inner" to inner, "outer" to outer))
    }
}
