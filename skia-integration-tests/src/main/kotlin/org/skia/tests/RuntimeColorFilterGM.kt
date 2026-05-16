package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.effects.SkBuiltinColorFilterEffects
import org.skia.foundation.SkBitmap
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/runtimecolorfilter.cpp` (`RuntimeColorFilterGM`).
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
 */
public class RuntimeColorFilterGM : GM() {

    override fun getName(): String = "runtimecolorfilter"
    override fun getISize(): SkISize = SkISize.Make(256 * 3, 256 * 2)

    /** Synthetic 256×256 stand-in for `images/mandrill_256.png`. */
    private val image: SkImage by lazy {
        val w = 256
        val h = 256
        SkBitmap(w, h).apply {
            for (y in 0 until h) {
                for (x in 0 until w) {
                    // Smooth RGB gradient — covers a large area of
                    // the colour cube so the per-cell luma /
                    // tone-map filters produce visibly different
                    // outputs.
                    val r = (x * 255 / (w - 1)) and 0xFF
                    val g = (y * 255 / (h - 1)) and 0xFF
                    val b = ((x + y) * 255 / (w + h - 2)) and 0xFF
                    setPixel(x, y, SkColorSetARGB(0xFF, r, g, b))
                }
            }
        }.asImage()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // First row : gNoop, gLumaSrc.
        for (src in firstRowSksl) {
            drawFiltered(c, src)
            c.translate(256f, 0f)
        }
        // Wrap to second row.
        c.translate(-256f * 2, 256f)
        // Second row : gTernary, gIfs, gEarlyReturn.
        for (src in secondRowSksl) {
            drawFiltered(c, src)
            c.translate(256f, 0f)
        }
    }

    private fun drawFiltered(c: SkCanvas, sksl: String) {
        val effect = SkRuntimeEffect.MakeForColorFilter(sksl).effect
            ?: error("Unable to compile runtime color filter : $sksl")
        val paint = SkPaint().apply {
            colorFilter = effect.makeColorFilter(uniforms = null)
        }
        c.drawImage(image, 0f, 0f, SkSamplingOptions.Default, paint)
    }

    private companion object {
        // Same SkSL strings as upstream, sourced via the builtin
        // registry to avoid drift if the canonical-source ever
        // changes (the registry hashes them).
        val firstRowSksl: List<String> = listOf(
            SkBuiltinColorFilterEffects.NOOP_SKSL,
            SkBuiltinColorFilterEffects.LUMA_SRC_SKSL,
        )
        val secondRowSksl: List<String> = listOf(
            SkBuiltinColorFilterEffects.TERNARY_SKSL,
            SkBuiltinColorFilterEffects.IFS_SKSL,
            SkBuiltinColorFilterEffects.EARLY_RETURN_SKSL,
        )
    }
}
