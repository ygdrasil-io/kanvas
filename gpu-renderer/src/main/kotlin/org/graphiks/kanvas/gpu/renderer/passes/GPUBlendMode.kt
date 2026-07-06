package org.graphiks.kanvas.gpu.renderer.passes

/**
 * Blend mode mapping to GPU fixed-function blend factors.
 *
 * Porter-Duff (fixed-function) modes use [colorSrcFactor]/[colorDstFactor]
 * directly. Shader-based modes ([requiresDestinationRead] = true) need dual
 * source / destination-read blending and currently fall back to SrcOver until
 * the shader blending pipeline is implemented.
 *
 * [gpuLabel] intentionally uses generic GPU wording. Backend-specific label
 * aliases are not preserved because renderer diagnostics should not expose the
 * concrete implementation behind the GPU abstraction.
 */
enum class GPUBlendMode(
    val gpuLabel: String,
    val colorSrcFactor: GPUBlendFactor,
    val colorDstFactor: GPUBlendFactor,
    val alphaSrcFactor: GPUBlendFactor,
    val alphaDstFactor: GPUBlendFactor,
    val requiresDestinationRead: Boolean = false,
) {
    // Porter-Duff modes: exact GPU fixed-function blend.
    SRC_OVER("src_over", GPUBlendFactor.One, GPUBlendFactor.OneMinusSrcAlpha, GPUBlendFactor.One, GPUBlendFactor.OneMinusSrcAlpha),
    SRC("src", GPUBlendFactor.One, GPUBlendFactor.Zero, GPUBlendFactor.One, GPUBlendFactor.Zero),
    DST("dst", GPUBlendFactor.Zero, GPUBlendFactor.One, GPUBlendFactor.Zero, GPUBlendFactor.One),
    SRC_IN("src_in", GPUBlendFactor.DstAlpha, GPUBlendFactor.Zero, GPUBlendFactor.DstAlpha, GPUBlendFactor.Zero),
    DST_IN("dst_in", GPUBlendFactor.Zero, GPUBlendFactor.SrcAlpha, GPUBlendFactor.Zero, GPUBlendFactor.SrcAlpha),
    SRC_OUT("src_out", GPUBlendFactor.OneMinusDstAlpha, GPUBlendFactor.Zero, GPUBlendFactor.OneMinusDstAlpha, GPUBlendFactor.Zero),
    DST_OUT("dst_out", GPUBlendFactor.Zero, GPUBlendFactor.OneMinusSrcAlpha, GPUBlendFactor.Zero, GPUBlendFactor.OneMinusSrcAlpha),
    SRC_ATOP("src_atop", GPUBlendFactor.DstAlpha, GPUBlendFactor.OneMinusSrcAlpha, GPUBlendFactor.DstAlpha, GPUBlendFactor.OneMinusSrcAlpha),
    DST_ATOP("dst_atop", GPUBlendFactor.OneMinusDstAlpha, GPUBlendFactor.SrcAlpha, GPUBlendFactor.OneMinusDstAlpha, GPUBlendFactor.SrcAlpha),
    XOR("xor", GPUBlendFactor.OneMinusDstAlpha, GPUBlendFactor.OneMinusSrcAlpha, GPUBlendFactor.OneMinusDstAlpha, GPUBlendFactor.OneMinusSrcAlpha),
    PLUS("plus", GPUBlendFactor.One, GPUBlendFactor.One, GPUBlendFactor.One, GPUBlendFactor.One),
    MODULATE("modulate", GPUBlendFactor.Zero, GPUBlendFactor.Src, GPUBlendFactor.Zero, GPUBlendFactor.SrcAlpha),
    // Advanced modes — need destination read + shader-based blending
    MULTIPLY("multiply", GPUBlendFactor.Dst, GPUBlendFactor.OneMinusSrcAlpha, GPUBlendFactor.One, GPUBlendFactor.OneMinusSrcAlpha, requiresDestinationRead = true),
    SCREEN("screen", GPUBlendFactor.One, GPUBlendFactor.OneMinusSrc, GPUBlendFactor.One, GPUBlendFactor.OneMinusSrcAlpha, requiresDestinationRead = true),
    OVERLAY("overlay", GPUBlendFactor.Dst, GPUBlendFactor.OneMinusSrcAlpha, GPUBlendFactor.One, GPUBlendFactor.OneMinusSrcAlpha, requiresDestinationRead = true),
    DARKEN("darken", GPUBlendFactor.OneMinusDst, GPUBlendFactor.OneMinusSrcAlpha, GPUBlendFactor.One, GPUBlendFactor.OneMinusSrcAlpha, requiresDestinationRead = true),
    LIGHTEN("lighten", GPUBlendFactor.One, GPUBlendFactor.OneMinusSrcAlpha, GPUBlendFactor.One, GPUBlendFactor.OneMinusSrcAlpha, requiresDestinationRead = true),
    DIFFERENCE("difference", GPUBlendFactor.One, GPUBlendFactor.OneMinusSrcAlpha, GPUBlendFactor.One, GPUBlendFactor.OneMinusSrcAlpha, requiresDestinationRead = true),
    EXCLUSION("exclusion", GPUBlendFactor.One, GPUBlendFactor.OneMinusSrcAlpha, GPUBlendFactor.One, GPUBlendFactor.OneMinusSrcAlpha, requiresDestinationRead = true),
}
