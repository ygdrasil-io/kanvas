package org.graphiks.kanvas.gpu.renderer.wgsl

const val ColorFilterEarlyReturnWgsl: String = """
struct ColorFilterEarlyReturnUniform {
    threshold: f32,
    input: vec4<f32>,
}
@group(1) @binding(0) var<uniform> uColorFilterER: ColorFilterEarlyReturnUniform;

fn color_filter_early_return(uv: vec2<f32>) -> vec4<f32> {
    if (uColorFilterER.input.a < uColorFilterER.threshold) {
        return vec4<f32>(0.0, 0.0, 0.0, 0.0);
    }
    return uColorFilterER.input;
}
"""

const val ColorFilterEarlyReturnSourceHash: String = "fragment:color_filter_early_return:v1"
const val ColorFilterEarlyReturnEntryPoint: String = "color_filter_early_return"
