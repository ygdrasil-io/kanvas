package org.graphiks.kanvas.gpu.renderer.wgsl

const val ColorFilterTernaryWgsl: String = """
struct ColorFilterTernaryUniform {
    condition: f32,
    colorTrue: vec4<f32>,
    colorFalse: vec4<f32>,
}
@group(1) @binding(0) var<uniform> uColorFilterTernary: ColorFilterTernaryUniform;

fn color_filter_ternary(inColor: vec4<f32>) -> vec4<f32> {
    return select(uColorFilterTernary.colorFalse, uColorFilterTernary.colorTrue, uColorFilterTernary.condition > 0.5);
}
"""

const val ColorFilterTernarySourceHash: String = "fragment:color_filter_ternary:v1"
const val ColorFilterTernaryEntryPoint: String = "color_filter_ternary"
