package org.graphiks.kanvas.gpu.renderer.materials

import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor

object BlendWgslBuilder {
    fun buildWgsl(dst: GPUMaterialDescriptor, src: GPUMaterialDescriptor, mode: String): String {
        val dstFields = childFields("dst", dst)
        val srcFields = childFields("src", src)
        val dstEval = childEval("dst", dst)
        val srcEval = childEval("src", src)
        val blendFn = blendFormula(mode)
        val hasImageDraw = (dst is GPUMaterialDescriptor.ImageDraw && dst.rgbaPixels.isNotEmpty()) ||
            (src is GPUMaterialDescriptor.ImageDraw && src.rgbaPixels.isNotEmpty())
        val texDecl = if (hasImageDraw) """
@group(1) @binding(1) var blend_image_texture: texture_2d<f32>;
@group(1) @binding(2) var blend_image_sampler: sampler;
""".trimIndent() else ""
        return """
struct BlendBlock {
    ${dstFields}
    ${srcFields}
    _pad0: u32, _pad1: u32, _pad2: u32,
}
@group(0) @binding(0) var<uniform> blend: BlendBlock;
${texDecl}

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
        is GPUMaterialDescriptor.RadialGradient -> """
            ${prefix}_center: vec2f,
            ${prefix}_radius: f32,
            ${prefix}_pad0: f32,
            ${prefix}_color: vec4f,
            ${prefix}_pad: vec4f,
        """.trimIndent()
        is GPUMaterialDescriptor.SweepGradient -> """
            ${prefix}_center: vec2f,
            ${prefix}_startAngle: f32,
            ${prefix}_endAngle: f32,
            ${prefix}_color: vec4f,
            ${prefix}_pad: vec4f,
        """.trimIndent()
        is GPUMaterialDescriptor.SolidColor -> """
            ${prefix}_color: vec4f,
            ${prefix}_pad: vec4f,
            ${prefix}_pad2: vec4f,
        """.trimIndent()
        is GPUMaterialDescriptor.ImageDraw -> """
            ${prefix}_color: vec4f,
            ${prefix}_pad: vec4f,
            ${prefix}_pad2: vec4f,
        """.trimIndent()
        else -> error("Unsupported blend child: ${child.kind}")
    }

    private fun childEval(prefix: String, child: GPUMaterialDescriptor): String = when (child) {
        is GPUMaterialDescriptor.LinearGradient -> """
    let ${prefix}_dir = blend.${prefix}_end - blend.${prefix}_start;
    let ${prefix}_t = dot(pos.xy - blend.${prefix}_start, ${prefix}_dir) / dot(${prefix}_dir, ${prefix}_dir);
    let ${prefix}_tc = clamp(${prefix}_t, 0.0, 1.0);
    let ${prefix}_result = mix(blend.${prefix}_color, blend.${prefix}_pad, ${prefix}_tc);""".trimIndent()
        is GPUMaterialDescriptor.RadialGradient -> """
    let ${prefix}_d = pos.xy - blend.${prefix}_center;
    let ${prefix}_t = length(${prefix}_d) / blend.${prefix}_radius;
    let ${prefix}_tc = clamp(${prefix}_t, 0.0, 1.0);
    let ${prefix}_result = mix(blend.${prefix}_color, blend.${prefix}_pad, ${prefix}_tc);""".trimIndent()
        is GPUMaterialDescriptor.SweepGradient -> """
    let ${prefix}_d = pos.xy - blend.${prefix}_center;
    let ${prefix}_a = atan2(${prefix}_d.y, ${prefix}_d.x);
    var ${prefix}_u = ${prefix}_a / 6.2831853071795864;
    if (${prefix}_u < 0.0) { ${prefix}_u = ${prefix}_u + 1.0; }
    let ${prefix}_sweep = blend.${prefix}_endAngle - blend.${prefix}_startAngle;
    let ${prefix}_t = (${prefix}_u - blend.${prefix}_startAngle / 360.0) * (360.0 / max(${prefix}_sweep, 1.0e-12));
    let ${prefix}_tc = clamp(${prefix}_t, 0.0, 1.0);
    let ${prefix}_result = mix(blend.${prefix}_color, blend.${prefix}_pad, ${prefix}_tc);""".trimIndent()
        is GPUMaterialDescriptor.SolidColor -> """
    let ${prefix}_result = blend.${prefix}_color;""".trimIndent()
        is GPUMaterialDescriptor.ImageDraw -> """
    let ${prefix}_uv = vec2f(pos.x * 0.5 + 0.5, pos.y * -0.5 + 0.5);
    let ${prefix}_sampled = textureSample(blend_image_texture, blend_image_sampler, ${prefix}_uv);
    let ${prefix}_result = ${prefix}_sampled * blend.${prefix}_color;""".trimIndent()
        else -> error("Unsupported blend child: ${child.kind}")
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
            is GPUMaterialDescriptor.RadialGradient -> {
                bb.putFloat(child.centerX); bb.putFloat(child.centerY)
                bb.putFloat(child.radius); bb.putFloat(0f) // pad
                bb.putFloat(child.startR * child.startA)
                bb.putFloat(child.startG * child.startA)
                bb.putFloat(child.startB * child.startA)
                bb.putFloat(child.startA)
                bb.putFloat(child.endR * child.endA)
                bb.putFloat(child.endG * child.endA)
                bb.putFloat(child.endB * child.endA)
                bb.putFloat(child.endA)
            }
            is GPUMaterialDescriptor.SweepGradient -> {
                bb.putFloat(child.centerX); bb.putFloat(child.centerY)
                bb.putFloat(child.startAngle); bb.putFloat(child.endAngle)
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
            is GPUMaterialDescriptor.ImageDraw -> {
                bb.putFloat(1f); bb.putFloat(1f); bb.putFloat(1f); bb.putFloat(1f)
                bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f)
                bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f)
            }
            else -> error("Unsupported blend child: ${child.kind}")
        }
    }
}
