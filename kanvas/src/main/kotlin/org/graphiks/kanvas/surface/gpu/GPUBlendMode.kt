package org.graphiks.kanvas.surface.gpu

enum class GPUBlendMode(
    val wgpuLabel: String,
    val colorSrcFactor: String,
    val colorDstFactor: String,
    val alphaSrcFactor: String,
    val alphaDstFactor: String,
) {
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
    MULTIPLY("multiply", "DstColor", "OneMinusSrcAlpha", "One", "OneMinusSrcAlpha"),
    SCREEN("screen", "One", "OneMinusSrcColor", "One", "OneMinusSrcAlpha"),
    OVERLAY("overlay", "DstColor", "OneMinusSrcAlpha", "One", "OneMinusSrcAlpha"),
    DARKEN("darken", "OneMinusDstColor", "OneMinusSrcAlpha", "One", "OneMinusSrcAlpha"),
    LIGHTEN("lighten", "One", "OneMinusSrcAlpha", "One", "OneMinusSrcAlpha"),
    DIFFERENCE("difference", "One", "OneMinusSrcAlpha", "One", "OneMinusSrcAlpha"),
    EXCLUSION("exclusion", "One", "OneMinusSrcAlpha", "One", "OneMinusSrcAlpha"),
}
