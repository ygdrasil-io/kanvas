package org.graphiks.kanvas.gpu.renderer.wgsl

const val ColorFilterIfsWgsl: String = """
struct ColorFilterIfsUniform {
    value: f32,
}
@group(1) @binding(0) var<uniform> uColorFilterIfs: ColorFilterIfsUniform;

fn color_filter_ifs(uv: vec2<f32>) -> vec4<f32> {
    var result: vec4<f32>;
    if (uColorFilterIfs.value < 0.2) {
        result = vec4<f32>(1.0, 0.0, 0.0, 1.0);
    } else if (uColorFilterIfs.value < 0.4) {
        result = vec4<f32>(0.0, 1.0, 0.0, 1.0);
    } else if (uColorFilterIfs.value < 0.6) {
        result = vec4<f32>(0.0, 0.0, 1.0, 1.0);
    } else if (uColorFilterIfs.value < 0.8) {
        result = vec4<f32>(1.0, 1.0, 0.0, 1.0);
    } else {
        result = vec4<f32>(1.0, 0.0, 1.0, 1.0);
    }
    return result;
}
"""

const val ColorFilterIfsSourceHash: String = "fragment:color_filter_ifs:v1"
const val ColorFilterIfsEntryPoint: String = "color_filter_ifs"
