package org.skia.effects.runtime

import org.skia.foundation.SkImageFilter

/**
 * Factory namespace for runtime-effect-backed [SkImageFilter] builders.
 *
 * Split from [org.skia.foundation.SkImageFilters] to keep the
 * `foundation` package free of `effects.runtime` imports (cycle break
 * for the upcoming `:cpu-raster` Gradle module extraction). Mirrors
 * Skia's `SkImageFilters::RuntimeShader(builder, …)` factory variants.
 */
public object SkRuntimeImageFilters {

    /**
     * Single-child variant — see [org.skia.foundation.SkImageFilters]
     * for general image-filter semantics. The provided [builder]
     * supplies the runtime effect + uniforms ; [childShaderName] is
     * the SkSL child slot name that receives [input] (or the layer's
     * source image when `input == null`).
     */
    public fun RuntimeShader(
        builder: SkRuntimeEffectBuilder,
        sampleRadius: Float = 0f,
        childShaderName: String,
        input: SkImageFilter? = null,
    ): SkImageFilter = SkRuntimeImageFilter(
        effect = builder.effect,
        uniforms = builder.snapshotUniforms(),
        bindings = listOf(SkRuntimeImageFilter.ChildBinding(childShaderName, input)),
        sampleRadius = sampleRadius,
    )

    /**
     * Multi-child variant — each entry in [childShaderNames] is paired
     * with the same-index entry in [inputs] ; a `null` input binds the
     * child slot to the layer's source image directly.
     */
    public fun RuntimeShader(
        builder: SkRuntimeEffectBuilder,
        childShaderNames: Array<String>,
        inputs: Array<SkImageFilter?>,
    ): SkImageFilter {
        require(childShaderNames.size == inputs.size) {
            "childShaderNames (${childShaderNames.size}) and inputs (${inputs.size}) must have the same length"
        }
        return SkRuntimeImageFilter(
            effect = builder.effect,
            uniforms = builder.snapshotUniforms(),
            bindings = childShaderNames.zip(inputs.toList()).map { (n, f) ->
                SkRuntimeImageFilter.ChildBinding(n, f)
            },
            sampleRadius = 0f,
        )
    }
}
