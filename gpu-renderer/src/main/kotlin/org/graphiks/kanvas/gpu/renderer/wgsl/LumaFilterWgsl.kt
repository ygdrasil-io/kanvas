package org.graphiks.kanvas.gpu.renderer.wgsl

const val LumaFilterWgsl: String = """
struct LumaFilterUniform { input: vec4<f32>, }
@group(1) @binding(0) var<uniform> uLumaFilter: LumaFilterUniform;

fn luma_filter_main(uv: vec2<f32>) -> vec4<f32> {
    let luma = dot(uLumaFilter.input.rgb, vec3<f32>(0.2126, 0.7152, 0.0722));
    return vec4<f32>(luma, luma, luma, uLumaFilter.input.a);
}
"""

const val LumaFilterSourceHash: String = "fragment:luma_filter_main:v1"
const val LumaFilterEntryPoint: String = "luma_filter_main"
