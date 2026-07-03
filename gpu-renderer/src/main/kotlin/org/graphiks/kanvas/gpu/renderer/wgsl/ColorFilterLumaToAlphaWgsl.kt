package org.graphiks.kanvas.gpu.renderer.wgsl

const val ColorFilterLumaToAlphaWgsl: String = """
fn color_filter_luma_to_alpha(inColor: vec4<f32>) -> vec4<f32> {
    let luma = dot(inColor.rgb, vec3<f32>(0.2126, 0.7152, 0.0722));
    return vec4<f32>(inColor.rgb, luma);
}
"""

const val ColorFilterLumaToAlphaSourceHash: String = "fragment:color_filter_luma_to_alpha:v1"
const val ColorFilterLumaToAlphaEntryPoint: String = "color_filter_luma_to_alpha"
