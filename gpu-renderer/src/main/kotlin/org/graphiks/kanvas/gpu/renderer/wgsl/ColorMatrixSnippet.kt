package org.graphiks.kanvas.gpu.renderer.wgsl

const val ColorMatrixWgsl: String = """
@group(1) @binding(1) var input_texture: texture_2d<f32>;
@group(1) @binding(2) var input_sampler: sampler;

struct ColorMatrixUniforms {
    m0: vec4<f32>,
    m1: vec4<f32>,
    m2: vec4<f32>,
    m3: vec4<f32>,
    bias: vec4<f32>,
};

@group(2) @binding(0) var<uniform> uniforms: ColorMatrixUniforms;

fn color_matrix_apply(coord: vec2<i32>) -> vec4<f32> {
    let uv = vec2<f32>(f32(coord.x), f32(coord.y));
    let src = textureSampleLevel(input_texture, input_sampler, uv, 0.0);
    let r = dot(vec4(src.r, src.g, src.b, src.a), uniforms.m0) + uniforms.bias.x;
    let g = dot(vec4(src.r, src.g, src.b, src.a), uniforms.m1) + uniforms.bias.y;
    let b = dot(vec4(src.r, src.g, src.b, src.a), uniforms.m2) + uniforms.bias.z;
    let a = dot(vec4(src.r, src.g, src.b, src.a), uniforms.m3) + uniforms.bias.w;
    return vec4<f32>(r, g, b, a);
}

fn color_matrix_identity(coord: vec2<i32>) -> vec4<f32> {
    let uv = vec2<f32>(f32(coord.x), f32(coord.y));
    return textureSampleLevel(input_texture, input_sampler, uv, 0.0);
}
"""

const val ColorMatrixSnippetSourceHash: String = "fragment:color_matrix:v1"
const val ColorMatrixApplyEntryPoint: String = "color_matrix_apply"
const val ColorMatrixIdentityEntryPoint: String = "color_matrix_identity"
