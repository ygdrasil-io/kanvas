package org.graphiks.kanvas.surface

/**
 * Public color formats accepted by GPU surfaces.
 *
 * [gpuLabel] intentionally uses generic GPU wording. Backend-specific label
 * aliases are not preserved on this public surface because they expose an
 * implementation detail that callers should not depend on.
 */
enum class GPUColorFormat(val gpuLabel: String) {
    RGBA8_UNORM("rgba8unorm"),
    RGBA8_UNORM_SRGB("rgba8unorm-srgb"),
    BGRA8_UNORM("bgra8unorm"),
}
