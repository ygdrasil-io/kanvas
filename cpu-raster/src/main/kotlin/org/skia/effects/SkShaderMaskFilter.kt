package org.skia.effects

import org.skia.math.SkColorGetA
import org.skia.foundation.SkMaskFilter
import org.skia.foundation.SkShader

/**
 * Mirrors Skia's
 * [`SkShaderMaskFilter`](https://github.com/google/skia/blob/main/include/effects/SkShaderMaskFilter.h)
 * — a [SkMaskFilter] whose coverage value at every pixel is the **alpha
 * of the wrapped shader's output**, multiplied with the rasteriser's
 * coverage mask.
 *
 * Typical use case is shaping a path's coverage by an arbitrary alpha
 * pattern — e.g. masking a drawn shape with a gradient's alpha or a
 * runtime-effect output. The shader is evaluated in device space at
 * each pixel of the input mask, and its alpha channel is combined with
 * the existing coverage via byte-multiplication
 * `out = (cov × shader.α + 127) / 255`.
 *
 * **Status** : upstream marks this filter as deprecated and slated for
 * removal, but a handful of GMs (`shadermaskfilter_gradient`,
 * `shadermaskfilter_image`) still reference it. The Kotlin port mirrors
 * the pre-deprecation public surface so those GMs keep compiling. The
 * filter reports [margin] = 0 — it never widens the coverage region.
 *
 * Note that the shader is expected to have already been set up for the
 * current draw via [SkShader.setupForDraw] before the rasterise pass
 * begins — the device that drives the mask filter is responsible for
 * the per-draw lifecycle. Filters that need to be self-contained should
 * wrap the shader call in a custom [SkMaskFilter] subclass instead.
 */
public object SkShaderMaskFilter {

    /**
     * Mirrors Skia's `SkShaderMaskFilter::Make(sk_sp<SkShader>)`.
     *
     * @param shader the shader whose per-pixel alpha will modulate the
     *   coverage mask. The shader is held by reference — callers must
     *   call [SkShader.setupForDraw] before the rasterise pass, and the
     *   filter assumes the shader stays valid for the duration of the
     *   draw.
     * @return a [SkMaskFilter] that multiplies coverage by `shader.α`.
     */
    public fun Make(shader: SkShader): SkMaskFilter = ShaderMaskImpl(shader)

    private class ShaderMaskImpl(private val shader: SkShader) : SkMaskFilter() {
        override fun margin(): Int = 0

        override fun filterMask(src: ByteArray, w: Int, h: Int): ByteArray {
            require(src.size == w * h) { "src.size (${src.size}) != $w × $h" }
            val out = ByteArray(src.size)
            if (w == 0 || h == 0) return out
            val row = IntArray(w)
            for (y in 0 until h) {
                shader.shadeRow(0, y, w, row)
                val base = y * w
                for (x in 0 until w) {
                    val cov = src[base + x].toInt() and 0xFF
                    val a = SkColorGetA(row[x])
                    out[base + x] = (((cov * a) + 127) / 255).toByte()
                }
            }
            return out
        }
    }
}
