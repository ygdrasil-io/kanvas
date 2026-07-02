package org.graphiks.kanvas.gpu.renderer.materials

import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor

object BlendWgslBuilder {
    fun buildWgsl(dst: GPUMaterialDescriptor, src: GPUMaterialDescriptor, mode: String): String {
        val dstFields = childFields("dst", dst)
        val srcFields = childFields("src", src)
        val dstEval = childEval("dst", dst)
        val srcEval = childEval("src", src)
        val blendFn = blendFormula(mode)
        return """
struct BlendBlock {
    ${dstFields}
    ${srcFields}
    _pad0: u32, _pad1: u32, _pad2: u32,
}
@group(0) @binding(0) var<uniform> blend: BlendBlock;

@vertex fn vs_main(@builtin(vertex_index) vi: u32) -> @builtin(position) vec4f {
    let x = f32((vi << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(vi & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

@fragment fn fs_main(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    ${dstEval}
    ${srcEval}
    return ${blendFn};
}
""".trimIndent()
    }

    fun packUniforms(dst: GPUMaterialDescriptor, src: GPUMaterialDescriptor, mode: String): ByteArray {
        val bb = java.nio.ByteBuffer.allocate(64 + (32 * 2))
            .order(java.nio.ByteOrder.nativeOrder())
        packChild("dst", dst, bb)
        packChild("src", src, bb)
        bb.putInt(0); bb.putInt(0); bb.putInt(0)
        return bb.array()
    }

    private fun childFields(prefix: String, child: GPUMaterialDescriptor): String = when (child) {
        is GPUMaterialDescriptor.LinearGradient -> """
            ${prefix}_start: vec2f,
            ${prefix}_end: vec2f,
            ${prefix}_color: vec4f,
            ${prefix}_pad: vec4f,
        """.trimIndent()
        is GPUMaterialDescriptor.SolidColor -> """
            ${prefix}_color: vec4f,
            ${prefix}_pad: vec4f,
            ${prefix}_pad2: vec4f,
        """.trimIndent()
        else -> """${prefix}_color: vec4f,${prefix}_pad: vec4f,${prefix}_pad2: vec4f,"""
    }

    private fun childEval(prefix: String, child: GPUMaterialDescriptor): String = when (child) {
        is GPUMaterialDescriptor.LinearGradient -> """
    let ${prefix}_dir = blend.${prefix}_end - blend.${prefix}_start;
    let ${prefix}_t = dot(pos.xy - blend.${prefix}_start, ${prefix}_dir) / dot(${prefix}_dir, ${prefix}_dir);
    let ${prefix}_tc = clamp(${prefix}_t, 0.0, 1.0);
    let ${prefix}_result = mix(blend.${prefix}_color, blend.${prefix}_pad, ${prefix}_tc);""".trimIndent()
        is GPUMaterialDescriptor.SolidColor -> """
    let ${prefix}_result = blend.${prefix}_color;""".trimIndent()
        else -> """let ${prefix}_result = vec4f(0.0, 0.0, 0.0, 0.0);"""
    }

    private fun blendFormula(mode: String): String = when (mode.uppercase()) {
        "SRC_OVER" -> "src_result + dst_result * (1.0 - src_result.a)"
        "DST_OVER" -> "dst_result + src_result * (1.0 - dst_result.a)"
        "SRC_IN" -> "src_result * dst_result.a"
        "DST_IN" -> "dst_result * src_result.a"
        "SRC_OUT" -> "src_result * (1.0 - dst_result.a)"
        "DST_OUT" -> "dst_result * (1.0 - src_result.a)"
        "SRC_ATOP" -> "dst_result * src_result.a + src_result * (1.0 - dst_result.a)"
        "DST_ATOP" -> "src_result * dst_result.a + dst_result * (1.0 - src_result.a)"
        "XOR" -> "src_result * (1.0 - dst_result.a) + dst_result * (1.0 - src_result.a)"
        "PLUS" -> "src_result + dst_result"
        "MODULATE" -> "src_result * dst_result"
        else -> "src_result * dst_result"
    } + ";"

    private fun packChild(prefix: String, child: GPUMaterialDescriptor, bb: java.nio.ByteBuffer) {
        when (child) {
            is GPUMaterialDescriptor.LinearGradient -> {
                bb.putFloat(child.startX); bb.putFloat(child.startY)
                bb.putFloat(child.endX); bb.putFloat(child.endY)
                bb.putFloat(child.startR * child.startA)
                bb.putFloat(child.startG * child.startA)
                bb.putFloat(child.startB * child.startA)
                bb.putFloat(child.startA)
                bb.putFloat(child.endR * child.endA)
                bb.putFloat(child.endG * child.endA)
                bb.putFloat(child.endB * child.endA)
                bb.putFloat(child.endA)
            }
            is GPUMaterialDescriptor.SolidColor -> {
                val r = child.r * child.a
                val g = child.g * child.a
                val b = child.b * child.a
                bb.putFloat(r); bb.putFloat(g); bb.putFloat(b); bb.putFloat(child.a)
                bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f)
                bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f)
            }
            else -> {
                bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f)
                bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f)
                bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f)
            }
        }
    }
}
