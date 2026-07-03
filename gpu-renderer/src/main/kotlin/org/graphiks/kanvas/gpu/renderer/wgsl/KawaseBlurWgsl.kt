package org.graphiks.kanvas.gpu.renderer.wgsl

const val KawaseBlurWgsl: String = """
struct KawaseBlurUniforms {
    in_inverseScale: f32,
    in_blurOffset: vec2<f32>,
}
@group(1) @binding(0) var<uniform> uKawaseBlur: KawaseBlurUniforms;
@group(0) @binding(0) var src: texture_2d<f32>;
@group(0) @binding(1) var src_sampler: sampler;

fn kawase_blur_source(uv: vec2<f32>) -> vec4<f32> {
    let offset = uKawaseBlur.in_blurOffset * uKawaseBlur.in_inverseScale;
    let c00 = textureSampleLevel(src, src_sampler, uv + vec2<f32>(-offset.x, -offset.y), 0.0);
    let c01 = textureSampleLevel(src, src_sampler, uv + vec2<f32>(0.0, -offset.y), 0.0);
    let c02 = textureSampleLevel(src, src_sampler, uv + vec2<f32>(offset.x, -offset.y), 0.0);
    let c10 = textureSampleLevel(src, src_sampler, uv + vec2<f32>(-offset.x, 0.0), 0.0);
    let c11 = textureSampleLevel(src, src_sampler, uv, 0.0);
    let c12 = textureSampleLevel(src, src_sampler, uv + vec2<f32>(offset.x, 0.0), 0.0);
    let c20 = textureSampleLevel(src, src_sampler, uv + vec2<f32>(-offset.x, offset.y), 0.0);
    let c21 = textureSampleLevel(src, src_sampler, uv + vec2<f32>(0.0, offset.y), 0.0);
    let c22 = textureSampleLevel(src, src_sampler, uv + vec2<f32>(offset.x, offset.y), 0.0);
    return (c00 + c01 + c02 + c10 + c11 + c12 + c20 + c21 + c22) / 9.0;
}
"""

const val KawaseBlurSourceHash: String = "fragment:kawase_blur:v1"
const val KawaseBlurEntryPoint: String = "kawase_blur_source"
