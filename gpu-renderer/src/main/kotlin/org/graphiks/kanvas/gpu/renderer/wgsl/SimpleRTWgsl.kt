package org.graphiks.kanvas.gpu.renderer.wgsl

const val SimpleRTWgsl: String = """
struct SimpleRTUniform {
    gColor: vec4<f32>,
}
@group(1) @binding(0) var<uniform> uSimpleRT: SimpleRTUniform;

fn simple_rt_source(uv: vec2<f32>) -> vec4<f32> {
    return uSimpleRT.gColor;
}
"""

const val SimpleRTSourceHash: String = "fragment:simple_rt:v1"
const val SimpleRTEntryPoint: String = "simple_rt_source"
