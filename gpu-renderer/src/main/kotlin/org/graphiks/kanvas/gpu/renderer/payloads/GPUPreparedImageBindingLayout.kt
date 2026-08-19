package org.graphiks.kanvas.gpu.renderer.payloads

/**
 * Backend-neutral expected topology for the closed prepared-image binding layout.
 *
 * Execution validates these facts against parser-backed WGSL reflection before using the identity.
 */
internal object GPUPreparedImageBindingLayoutTopology {
    const val IDENTITY = "prepared-image.group0.dynamic-uniform-texture-sampler.v1"
    const val GROUP = 0
    const val UNIFORM_BINDING = 0
    const val TEXTURE_BINDING = 1
    const val SAMPLER_BINDING = 2
    const val UNIFORM_MIN_BINDING_SIZE_BYTES = 112
}
