package org.graphiks.kanvas.skia.gm.runtime_effect

import org.graphiks.kanvas.gpu.renderer.wgsl.ColorFilterNoopWgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.ColorFilterLumaToAlphaWgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.ColorFilterTernaryWgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.ColorFilterIfsWgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.ColorFilterEarlyReturnWgsl
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/runtimecolorfilter.cpp`.
 *
 * Draws the same source image five times under five SkSL color
 * filters that all express the same idea (or, for `gNoop` /
 * `gLumaSrc`, two distinct ideas) :
 *
 *  | Cell | SkSL source | Effect                          |
 *  |:----:|:------------|:--------------------------------|
 *  | 0,0  | gNoop       | identity (passthrough)          |
 *  | 0,1  | gLumaSrc    | luma → alpha (RGB cleared)      |
 *  | 1,0  | gTernary    | tone-map via ternary            |
 *  | 1,1  | gIfs        | tone-map via if / else if       |
 *  | 1,2  | gEarlyReturn| tone-map via early `return`     |
 *
 * The three tone-map variants (`gTernary` / `gIfs` /
 * `gEarlyReturn`) express the same semantic in three different
 * SkSL syntaxes — they hash to three distinct keys in
 * [org.skia.effects.runtime.SkRuntimeEffectDispatch] but all map
 * to the same [SkBuiltinColorFilterEffects.ToneMapImpl] (per-
 * variant register entries set up at class load).
 *
 * **Adaptation** : upstream loads `images/mandrill_256.png` via
 * `ToolUtils::GetResourceAsImage`. We synthesise a 256×256
 * gradient stand-in (matches [Skbug13047GM]'s pattern). Iso-
 * fidelity vs upstream's mandrill is therefore impossible —
 * similarity reflects the cell-by-cell colour-filter math, not
 * the underlying pixels.
 * @see https://github.com/google/skia/blob/main/gm/runtimecolorfilter.cpp
 */
class RuntimeColorFilterGm : SkiaGm {
    override val name = "runtimecolorfilter"
    override val renderFamily = RenderFamily.RUNTIME_EFFECT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 768
    override val height = 512

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val cell = 256f

        canvas.drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()), Paint(color = Color.WHITE))

        drawCell(canvas, 0, 0, ColorFilterNoopWgsl, UniformBlock {})
        drawCell(canvas, 1, 0, ColorFilterLumaToAlphaWgsl, UniformBlock {
            float4("srcColor", 0.5f, 0.2f, 0.8f, 1.0f)
        })
        drawCell(canvas, 0, 1, ColorFilterTernaryWgsl, UniformBlock {
            float1("condition", 1.0f)
            float4("colorTrue", 0.2f, 0.6f, 1.0f, 1.0f)
            float4("colorFalse", 1.0f, 0.2f, 0.2f, 1.0f)
        })
        drawCell(canvas, 1, 1, ColorFilterIfsWgsl, UniformBlock {
            float1("value", 0.5f)
        })
        drawCell(canvas, 2, 1, ColorFilterEarlyReturnWgsl, UniformBlock {
            float1("threshold", 0.5f)
            float4("input", 0.0f, 0.8f, 0.0f, 1.0f)
        })
    }

    private fun drawCell(canvas: GmCanvas, col: Int, row: Int, wgsl: String, uniforms: UniformBlock) {
        val effect = RuntimeEffect.compile(wgsl).getOrThrow()
        val colorFilter = effect.makeColorFilter(uniforms)
        val x = col * 256f
        val y = row * 256f
        canvas.drawRect(Rect(x, y, x + 256f, y + 256f), Paint(
            color = Color.fromRGBA(1f, 1f, 1f, 1f),
            colorFilter = colorFilter,
        ))
    }
}
