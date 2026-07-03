package org.graphiks.kanvas.gpu.renderer.wgsl

const val ColorFilterIfsWgsl: String = """
struct ColorFilterIfsUniform {
    value: f32,
}
@group(1) @binding(0) var<uniform> uColorFilterIfs: ColorFilterIfsUniform;

fn color_filter_ifs(inColor: vec4<f32>) -> vec4<f32> {
    var out = inColor;
    if (uColorFilterIfs.value < 0.2) {
        out = vec4<f32>(0.2, 0.6, 1.0, 1.0);
    } else if (uColorFilterIfs.value < 0.4) {
        out = vec4<f32>(1.0, 0.2, 0.2, 1.0);
    } else if (uColorFilterIfs.value < 0.6) {
        out = vec4<f32>(0.2, 1.0, 0.2, 1.0);
    } else if (uColorFilterIfs.value < 0.8) {
        out = vec4<f32>(0.2, 0.2, 1.0, 1.0);
    }
    return out;
}
"""

const val ColorFilterIfsSourceHash: String = "fragment:color_filter_ifs:v1"
const val ColorFilterIfsEntryPoint: String = "color_filter_ifs"
