package org.graphiks.kanvas.gpu.renderer.wgsl

const val RippleWgsl: String = """
struct RippleUniform {
    time: f32,
    amplitude: f32,
    frequency: f32,
    center: vec2<f32>,
}
@group(1) @binding(0) var<uniform> uRipple: RippleUniform;

fn ripple_main(uv: vec2<f32>) -> vec4<f32> {
    let dist = distance(uv, uRipple.center);
    let wave = sin(dist * uRipple.frequency - uRipple.time) * uRipple.amplitude;
    let c = wave * 0.5 + 0.5;
    return vec4<f32>(c, c, c, 1.0);
}
"""

const val RippleSourceHash: String = "fragment:ripple_main:v1"
const val RippleEntryPoint: String = "ripple_main"
