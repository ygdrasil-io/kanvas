package org.graphiks.kanvas.gpu.renderer.wgsl

const val GaussianBlurHorizontalWgsl: String = """
@group(1) @binding(1) var input_texture: texture_2d<f32>;
@group(1) @binding(2) var input_sampler: sampler;

struct BlurUniforms {
    radius: f32,
    sigma: f32,
    kernel_size: u32,
};

@group(2) @binding(0) var<uniform> uniforms: BlurUniforms;

fn gaussian_weight(x: f32, sigma: f32) -> f32 {
    let inv_sigma2 = 1.0 / (2.0 * sigma * sigma);
    let two_pi_sigma2 = 6.283185307 * sigma * sigma;
    return exp(-x * x * inv_sigma2) / two_pi_sigma2;
}

fn blur_sample_h(coord: vec2<i32>, kernel_radius: i32, sigma: f32) -> vec4<f32> {
    var result = vec4(0.0, 0.0, 0.0, 0.0);
    var total_weight = 0.0;
    for (var i: i32 = -kernel_radius; i <= kernel_radius; i = i + 1) {
        let sample_coord = coord + vec2<i32>(i, 0);
        let sample_uv = vec2<f32>(f32(sample_coord.x), f32(sample_coord.y));
        let w = gaussian_weight(f32(i), sigma);
        result = result + w * textureSampleLevel(input_texture, input_sampler, sample_uv, 0.0);
        total_weight = total_weight + w;
    }
    return result / total_weight;
}

fn blur_sample_v(coord: vec2<i32>, kernel_radius: i32, sigma: f32) -> vec4<f32> {
    var result = vec4(0.0, 0.0, 0.0, 0.0);
    var total_weight = 0.0;
    for (var i: i32 = -kernel_radius; i <= kernel_radius; i = i + 1) {
        let sample_coord = coord + vec2<i32>(0, i);
        let sample_uv = vec2<f32>(f32(sample_coord.x), f32(sample_coord.y));
        let w = gaussian_weight(f32(i), sigma);
        result = result + w * textureSampleLevel(input_texture, input_sampler, sample_uv, 0.0);
        total_weight = total_weight + w;
    }
    return result / total_weight;
}

fn gaussian_blur_h(coord: vec2<i32>) -> vec4<f32> {
    let r = i32(uniforms.radius);
    let s = uniforms.sigma;
    return blur_sample_h(coord, r, s);
}

fn gaussian_blur_v(coord: vec2<i32>) -> vec4<f32> {
    let r = i32(uniforms.radius);
    let s = uniforms.sigma;
    return blur_sample_v(coord, r, s);
}
"""

const val GaussianBlurDownsampleWgsl: String = """
@group(1) @binding(1) var input_texture: texture_2d<f32>;
@group(1) @binding(2) var input_sampler: sampler;

fn downsample_2x(coord: vec2<i32>, size: vec2<f32>) -> vec4<f32> {
    let uv = vec2<f32>(f32(coord.x), f32(coord.y)) / size;
    let half_texel = 0.5 / size;
    let c0 = textureSampleLevel(input_texture, input_sampler, uv + half_texel * vec2(-1.0, -1.0), 0.0);
    let c1 = textureSampleLevel(input_texture, input_sampler, uv + half_texel * vec2(1.0, -1.0), 0.0);
    let c2 = textureSampleLevel(input_texture, input_sampler, uv + half_texel * vec2(-1.0, 1.0), 0.0);
    let c3 = textureSampleLevel(input_texture, input_sampler, uv + half_texel * vec2(1.0, 1.0), 0.0);
    return (c0 + c1 + c2 + c3) * 0.25;
}
"""

const val GaussianBlurUpsampleWgsl: String = """
@group(1) @binding(1) var input_texture: texture_2d<f32>;
@group(1) @binding(2) var input_sampler: sampler;

fn upsample_2x(uv: vec2<f32>, size: vec2<f32>) -> vec4<f32> {
    let half_texel = 0.5 / size;
    let c0 = textureSampleLevel(input_texture, input_sampler, uv + half_texel * vec2(-0.5, -0.5), 0.0);
    let c1 = textureSampleLevel(input_texture, input_sampler, uv + half_texel * vec2(0.5, -0.5), 0.0);
    let c2 = textureSampleLevel(input_texture, input_sampler, uv + half_texel * vec2(-0.5, 0.5), 0.0);
    let c3 = textureSampleLevel(input_texture, input_sampler, uv + half_texel * vec2(0.5, 0.5), 0.0);
    return (c0 + c1 + c2 + c3) * 0.25;
}
"""

const val BlurSnippetSourceHash: String = "fragment:gaussian_blur:v1"
const val GaussianBlurHorizontalEntryPoint: String = "gaussian_blur_h"
const val GaussianBlurVerticalEntryPoint: String = "gaussian_blur_v"
const val GaussianBlurDownsampleEntryPoint: String = "downsample_2x"
const val GaussianBlurUpsampleEntryPoint: String = "upsample_2x"
