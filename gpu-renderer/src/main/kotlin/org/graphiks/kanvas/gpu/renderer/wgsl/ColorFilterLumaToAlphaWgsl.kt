package org.graphiks.kanvas.gpu.renderer.wgsl

const val ColorFilterLumaToAlphaWgsl: String = """
struct ColorFilterLumaToAlphaUniform { srcColor: vec4<f32>, }
@group(1) @binding(0) var<uniform> uColorFilterLTA: ColorFilterLumaToAlphaUniform;

fn color_filter_luma_to_alpha(uv: vec2<f32>) -> vec4<f32> {
    let luma = dot(uColorFilterLTA.srcColor.rgb, vec3<f32>(0.2126, 0.7152, 0.0722));
    return vec4<f32>(uColorFilterLTA.srcColor.rgb, luma);
}
"""

const val ColorFilterLumaToAlphaSourceHash: String = "fragment:color_filter_luma_to_alpha:v1"
const val ColorFilterLumaToAlphaEntryPoint: String = "color_filter_luma_to_alpha"
