package org.graphiks.kanvas.gpu.renderer.wgsl

const val BitmapShaderWgsl: String = """
@group(1) @binding(1) var texture_sampled: texture_2d<f32>;
@group(1) @binding(2) var texture_sampler: sampler;

fn bitmap_uv_clamp(uv: vec2<f32>) -> vec2<f32> {
    return clamp(uv, vec2(0.0, 0.0), vec2(1.0, 1.0));
}

fn bitmap_uv_repeat(uv: vec2<f32>) -> vec2<f32> {
    return fract(uv);
}

fn bitmap_uv_mirror(uv: vec2<f32>) -> vec2<f32> {
    let half = uv * 0.5;
    let t = half - floor(half);
    return 1.0 - 2.0 * abs(t - 0.5);
}

fn bitmap_uv_decal(uv: vec2<f32>) -> vec2<f32> {
    return uv;
}

fn bitmap_shader_clamp(uv: vec2<f32>) -> vec4<f32> {
    let clamped = bitmap_uv_clamp(uv);
    return textureSample(texture_sampled, texture_sampler, clamped);
}

fn bitmap_shader_repeat(uv: vec2<f32>) -> vec4<f32> {
    let repeated = bitmap_uv_repeat(uv);
    return textureSample(texture_sampled, texture_sampler, repeated);
}

fn bitmap_shader_mirror(uv: vec2<f32>) -> vec4<f32> {
    let mirrored = bitmap_uv_mirror(uv);
    return textureSample(texture_sampled, texture_sampler, mirrored);
}

fn bitmap_shader_decal(uv: vec2<f32>) -> vec4<f32> {
    let inside = all(uv >= vec2(0.0, 0.0)) && all(uv <= vec2(1.0, 1.0));
    if (inside) {
        return textureSample(texture_sampled, texture_sampler, uv);
    }
    return vec4(0.0, 0.0, 0.0, 0.0);
}

fn bitmap_shader_source(uv: vec2<f32>) -> vec4<f32> {
    return bitmap_shader_clamp(uv);
}
"""

const val BitmapShaderSnippetSourceHash: String = "fragment:bitmap_shader:v1"
const val BitmapShaderClampEntryPoint: String = "bitmap_shader_clamp"
const val BitmapShaderRepeatEntryPoint: String = "bitmap_shader_repeat"
const val BitmapShaderMirrorEntryPoint: String = "bitmap_shader_mirror"
const val BitmapShaderDecalEntryPoint: String = "bitmap_shader_decal"
