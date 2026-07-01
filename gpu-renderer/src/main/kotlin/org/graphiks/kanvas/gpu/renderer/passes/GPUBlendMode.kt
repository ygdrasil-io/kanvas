package org.graphiks.kanvas.gpu.renderer.passes

/**
 * WebGPU blend mode mapping.
 *
 * Porter-Duff (fixed-function) modes use [colorSrcFactor]/[colorDstFactor]
 * directly. Shader-based modes ([requiresDestinationRead] = true) need dual
 * source / destination-read blending and currently fall back to SrcOver until
 * the shader blending pipeline is implemented.
 */
enum class GPUBlendMode(
    val wgpuLabel: String,
    val colorSrcFactor: String,
    val colorDstFactor: String,
    val alphaSrcFactor: String,
    val alphaDstFactor: String,
    val requiresDestinationRead: Boolean = false,
) {
    // Porter-Duff modes — exact WebGPU fixed-function blend
    SRC_OVER("src_over", "One", "OneMinusSrcAlpha", "One", "OneMinusSrcAlpha"),
    SRC("src", "One", "Zero", "One", "Zero"),
    DST("dst", "Zero", "One", "Zero", "One"),
    SRC_IN("src_in", "DstAlpha", "Zero", "DstAlpha", "Zero"),
    DST_IN("dst_in", "Zero", "SrcAlpha", "Zero", "SrcAlpha"),
    SRC_OUT("src_out", "OneMinusDstAlpha", "Zero", "OneMinusDstAlpha", "Zero"),
    DST_OUT("dst_out", "Zero", "OneMinusSrcAlpha", "Zero", "OneMinusSrcAlpha"),
    SRC_ATOP("src_atop", "DstAlpha", "OneMinusSrcAlpha", "DstAlpha", "OneMinusSrcAlpha"),
    DST_ATOP("dst_atop", "OneMinusDstAlpha", "SrcAlpha", "OneMinusDstAlpha", "SrcAlpha"),
    XOR("xor", "OneMinusDstAlpha", "OneMinusSrcAlpha", "OneMinusDstAlpha", "OneMinusSrcAlpha"),
    PLUS("plus", "One", "One", "One", "One"),
    MODULATE("modulate", "Zero", "SrcColor", "Zero", "SrcAlpha"),
    // Advanced modes — need destination read + shader-based blending
    MULTIPLY("multiply", "DstColor", "OneMinusSrcAlpha", "One", "OneMinusSrcAlpha", requiresDestinationRead = true),
    SCREEN("screen", "One", "OneMinusSrcColor", "One", "OneMinusSrcAlpha", requiresDestinationRead = true),
    OVERLAY("overlay", "DstColor", "OneMinusSrcAlpha", "One", "OneMinusSrcAlpha", requiresDestinationRead = true),
    DARKEN("darken", "OneMinusDstColor", "OneMinusSrcAlpha", "One", "OneMinusSrcAlpha", requiresDestinationRead = true),
    LIGHTEN("lighten", "One", "OneMinusSrcAlpha", "One", "OneMinusSrcAlpha", requiresDestinationRead = true),
    DIFFERENCE("difference", "One", "OneMinusSrcAlpha", "One", "OneMinusSrcAlpha", requiresDestinationRead = true),
    EXCLUSION("exclusion", "One", "OneMinusSrcAlpha", "One", "OneMinusSrcAlpha", requiresDestinationRead = true),
}
