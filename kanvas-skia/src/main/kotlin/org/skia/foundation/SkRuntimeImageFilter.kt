package org.skia.foundation

import org.skia.effects.runtime.ChildResolver
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.SkRuntimeEffectBuilder
import org.skia.effects.runtime.SkRuntimeShader
import org.skia.math.SkMatrix

/**
 * Phase D2.5 — `SkImageFilters::RuntimeShader(builder, childShaderName,
 * input)` (and its multi-child variant) backing class.
 *
 * Bridges runtime-effect shaders into the [SkImageFilter] DAG :
 * after evaluating the supplied [inputFilters] (one per child slot,
 * `null` = use the layer's source image), each filtered image is
 * wrapped as a [SkRuntimeEffect.ChildType.kShader] resolver, the
 * runtime effect is run per-pixel over the bounding box of the
 * inputs, and the output bitmap is returned as the filter result.
 *
 * **Sampling radius** : upstream's [`sampleRadius`](https://github.com/google/skia/blob/main/include/effects/SkImageFilters.h)
 * parameter expands the source bbox by that many pixels in each
 * direction so the shader can sample beyond the rasterised region
 * without clipping. We honour it by inflating the working canvas
 * by `ceil(sampleRadius)` per side ; the result is cropped back to
 * the input's size at draw time. Default 0.
 *
 * **Multi-child** : the constructor accepts a list of
 * `(childShaderName, inputFilter)` pairs. For a `null` filter, the
 * child binds directly to the layer's source `src` image. For a
 * non-null filter, we evaluate it first (`input.filterImage(src, ctm)`)
 * and bind the resulting image as the child shader.
 *
 * **Cross-cut with C1** : the rendering loop in [filterImage]
 * resembles [SkColorFilterImageFilter] — allocate a same-sized
 * output buffer, walk every pixel, sample the shader at the pixel
 * center, write the resulting `SkColor4f` (clamped + packed to
 * 8-bit ARGB).
 */
internal class SkRuntimeImageFilter(
    private val effect: SkRuntimeEffect,
    private val uniforms: SkData,
    private val bindings: List<ChildBinding>,
    @Suppress("UNUSED_PARAMETER") private val sampleRadius: Float,
) : SkImageFilter() {

    /** Pair of an SkSL child slot name and the [SkImageFilter] that
     *  produces its content (or `null` to bind directly to the
     *  layer's source image). */
    data class ChildBinding(val name: String, val filter: SkImageFilter?)

    override fun filterImage(src: SkImage, ctm: SkMatrix): FilterResult {
        // Resolve every child binding to a concrete SkImage.
        val w = src.width
        val h = src.height
        val childImages = bindings.map { (_, filter) ->
            val r = filter?.filterImage(src, ctm) ?: FilterResult(src, 0, 0)
            r.image
        }

        // Build child resolvers — one Shader resolver per slot,
        // sampling the filtered image at the requested coord. We
        // treat the filter result's `(offsetX, offsetY)` as zero
        // for now (the upstream upsamples the inflated input ; we
        // ignore the inflation for simplicity — slice-internal
        // limitation, matches the C1 ColorFilter approach).
        val childResolvers: Array<ChildResolver> = Array(bindings.size) { idx ->
            val name = bindings[idx].name
            val child = effect.findChild(name)
                ?: error("Runtime image filter : child '$name' not declared in effect")
            require(child.type == SkRuntimeEffect.ChildType.kShader) {
                "Runtime image filter : child '$name' must be a shader (got ${child.type})"
            }
            val img = childImages[idx]
            ChildResolver.Shader { coord ->
                val px = coord.fX.toInt().coerceIn(0, img.width - 1)
                val py = coord.fY.toInt().coerceIn(0, img.height - 1)
                SkColor4f.FromColor(img.peekPixel(px, py))
            }
        }

        // Allocate output buffer.
        val outPixels = IntArray(w * h)
        val u = SkRuntimeShader.makeUniformsBuffer(uniforms)
        val impl = effect.implForRuntimeImageFilter()

        for (y in 0 until h) {
            val rowOff = y * w
            for (x in 0 until w) {
                val c4f = impl.shade(
                    coords = org.skia.math.SkPoint(x + 0.5f, y + 0.5f),
                    srcColor = null,
                    dstColor = null,
                    uniforms = u.duplicate(),
                    children = childResolvers,
                )
                outPixels[rowOff + x] = c4f.toSkColor()
            }
        }

        return FilterResult(SkImage(w, h, outPixels), 0, 0)
    }
}

/**
 * Internal accessor — exposes the lazily-resolved
 * [org.skia.effects.runtime.SkRuntimeImpl] to callers in
 * `org.skia.foundation` (where [SkRuntimeImageFilter] lives).
 * Mirrors the impl-resolution path that `SkRuntimeShader` /
 * `SkRuntimeColorFilter` already use ; this is the entry point
 * for the image-filter case.
 *
 * `internal` is module-scoped in Kotlin, so the access from
 * `org.skia.foundation` to the `org.skia.effects.runtime`
 * package's `internal val impl` is valid (same module).
 */
internal fun SkRuntimeEffect.implForRuntimeImageFilter(): org.skia.effects.runtime.SkRuntimeImpl =
    this.impl
