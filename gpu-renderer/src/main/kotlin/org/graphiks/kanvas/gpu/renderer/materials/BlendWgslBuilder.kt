package org.graphiks.kanvas.gpu.renderer.materials

import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import kotlin.math.pow

object BlendWgslBuilder {
    fun buildWgsl(dst: GPUMaterialDescriptor, src: GPUMaterialDescriptor, mode: String): String {
        val dstFields = childFields("dst", dst)
        val srcFields = childFields("src", src)
        val dstEval = childEval("dst", dst)
        val srcEval = childEval("src", src)
        val blendMode = GPUBlendMode.entries.singleOrNull {
            it.name.equals(mode, ignoreCase = true) || it.gpuLabel.equals(mode, ignoreCase = true)
        } ?: error("Unsupported blend mode: $mode")
        val blendFormula = GPUBlendFormulaLibrary.selectedBlendFunctionWgsl(blendMode)
        val hasImageDraw = dst is GPUMaterialDescriptor.ImageDraw || src is GPUMaterialDescriptor.ImageDraw
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

struct VertexOutput {
    @builtin(position) pos: vec4f,
    @location(0) uv: vec2f,
}

@vertex fn vs_main(@builtin(vertex_index) vi: u32) -> VertexOutput {
    let verts = array<vec2f, 3>(
        vec2f(-1.0, -1.0),
        vec2f(3.0, -1.0),
        vec2f(-1.0, 3.0),
    );
    let pos = verts[vi];
    return VertexOutput(vec4f(pos, 0.0, 1.0), vec2f(pos.x * 0.5 + 0.5, 1.0 - (pos.y * 0.5 + 0.5)));
}

${blendFormula}

@fragment fn fs_main(@location(0) uv: vec2f) -> @location(0) vec4f {
    ${dstEval}
    ${srcEval}
    return kanvasBlendPremul(src_result, dst_result);
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
    let ${prefix}_t = dot(uv - blend.${prefix}_start, ${prefix}_dir) / dot(${prefix}_dir, ${prefix}_dir);
    let ${prefix}_tc = clamp(${prefix}_t, 0.0, 1.0);
    let ${prefix}_result = mix(blend.${prefix}_color, blend.${prefix}_pad, ${prefix}_tc);""".trimIndent()
        is GPUMaterialDescriptor.RadialGradient -> """
    let ${prefix}_d = uv - blend.${prefix}_center;
    let ${prefix}_t = length(${prefix}_d) / blend.${prefix}_radius;
    let ${prefix}_tc = clamp(${prefix}_t, 0.0, 1.0);
    let ${prefix}_result = mix(blend.${prefix}_color, blend.${prefix}_pad, ${prefix}_tc);""".trimIndent()
        is GPUMaterialDescriptor.SweepGradient -> """
    let ${prefix}_d = uv - blend.${prefix}_center;
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
    let ${prefix}_uv = uv;
    let ${prefix}_sampled = textureSample(blend_image_texture, blend_image_sampler, ${prefix}_uv);
    let ${prefix}_result = ${prefix}_sampled * blend.${prefix}_color;""".trimIndent()
        else -> error("Unsupported blend child: ${child.kind}")
    }

    private fun packChild(prefix: String, child: GPUMaterialDescriptor, bb: java.nio.ByteBuffer) {
        when (child) {
            is GPUMaterialDescriptor.LinearGradient -> {
                bb.putFloat(child.startX); bb.putFloat(child.startY)
                bb.putFloat(child.endX); bb.putFloat(child.endY)
                bb.putFloat(srgbToLinear(child.startR) * child.startA)
                bb.putFloat(srgbToLinear(child.startG) * child.startA)
                bb.putFloat(srgbToLinear(child.startB) * child.startA)
                bb.putFloat(child.startA)
                bb.putFloat(srgbToLinear(child.endR) * child.endA)
                bb.putFloat(srgbToLinear(child.endG) * child.endA)
                bb.putFloat(srgbToLinear(child.endB) * child.endA)
                bb.putFloat(child.endA)
            }
            is GPUMaterialDescriptor.RadialGradient -> {
                bb.putFloat(child.centerX); bb.putFloat(child.centerY)
                bb.putFloat(child.radius); bb.putFloat(0f) // pad
                bb.putFloat(srgbToLinear(child.startR) * child.startA)
                bb.putFloat(srgbToLinear(child.startG) * child.startA)
                bb.putFloat(srgbToLinear(child.startB) * child.startA)
                bb.putFloat(child.startA)
                bb.putFloat(srgbToLinear(child.endR) * child.endA)
                bb.putFloat(srgbToLinear(child.endG) * child.endA)
                bb.putFloat(srgbToLinear(child.endB) * child.endA)
                bb.putFloat(child.endA)
            }
            is GPUMaterialDescriptor.SweepGradient -> {
                bb.putFloat(child.centerX); bb.putFloat(child.centerY)
                bb.putFloat(child.startAngle); bb.putFloat(child.endAngle)
                bb.putFloat(srgbToLinear(child.startR) * child.startA)
                bb.putFloat(srgbToLinear(child.startG) * child.startA)
                bb.putFloat(srgbToLinear(child.startB) * child.startA)
                bb.putFloat(child.startA)
                bb.putFloat(srgbToLinear(child.endR) * child.endA)
                bb.putFloat(srgbToLinear(child.endG) * child.endA)
                bb.putFloat(srgbToLinear(child.endB) * child.endA)
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

    private fun srgbToLinear(c: Float): Float {
        return if (c <= 0.04045f) c / 12.92f
        else ((c + 0.055f) / 1.055f).pow(2.4f)
    }
}
