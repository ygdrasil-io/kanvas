package org.graphiks.kanvas.skia.gm.runtime_effect

import org.graphiks.kanvas.gpu.renderer.wgsl.LumaFilterWgsl
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.Color

/**
 * Port of Skia's `gm/lumafilter.cpp`.
 *
 * **Note** : the upstream `lumafilter.cpp` ships a second
 * `DEF_SIMPLE_GM(AlternateLuma)` that wraps a luma color filter
 * inside a runtime-effect shim — that GM depends on a runtime
 * effect and is therefore deferred. This port only covers the first
 * GM, which uses only primitives already shipped.
 *
 * **What's drawn** : a 6-column × 4-row grid where each column
 * exercises a different blend mode and each row pairs
 * two shader configurations (none/none, none/g2, g1/none, g1/g2).
 * Inside each cell, two ovals are drawn through a `saveLayer` /
 * blend / `saveLayer` chain modulated by the luma color filter on
 * the second oval.
 *
 * **Iso-fidelity caveat** : labels (column / row text) are skipped
 * to avoid font-rendering drift dominating the similarity. The
 * pixel content of the cells themselves should match upstream.
 * @see https://github.com/google/skia/blob/main/gm/lumafilter.cpp
 */
class LumaFilterGm : SkiaGm {
    override val name = "lumafilter"
    override val renderFamily = RenderFamily.RUNTIME_EFFECT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 600
    override val height = 420

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val effect = RuntimeEffect.compile(LumaFilterWgsl).getOrThrow()

        val colors = listOf(
            floatArrayOf(1f, 0f, 0f, 1f),
            floatArrayOf(0f, 1f, 0f, 1f),
            floatArrayOf(0f, 0f, 1f, 1f),
            floatArrayOf(1f, 1f, 0f, 1f),
            floatArrayOf(1f, 0f, 1f, 1f),
            floatArrayOf(0f, 1f, 1f, 1f),
            floatArrayOf(0.5f, 0.5f, 0.5f, 1f),
            floatArrayOf(1f, 1f, 1f, 1f),
        )

        canvas.drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()), Paint(color = Color.WHITE))

        for (i in colors.indices) {
            val c = colors[i]
            val uniforms = UniformBlock {
                float4("input", c[0], c[1], c[2], c[3])
            }
            val shader = effect.makeShader(uniforms)
            val x = (i % 4) * 128f
            val y = (i / 4) * 128f
            canvas.drawRect(Rect(x, y, x + 128f, y + 128f), Paint(shader = shader))
        }
    }
}
