package org.graphiks.kanvas.gpu.renderer.wgsl

const val ArithmodeWgsl: String = """
struct ArithmodeUniform {
    mode: i32,
    src: vec4<f32>,
    dst: vec4<f32>,
}
@group(1) @binding(0) var<uniform> uArithmode: ArithmodeUniform;

fn arithmode_main(uv: vec2<f32>) -> vec4<f32> {
    var result: vec4<f32>;
    switch (uArithmode.mode) {
        case 0: { result = uArithmode.src + uArithmode.dst; }
        case 1: { result = uArithmode.src - uArithmode.dst; }
        case 2: {
            result = vec4<f32>(
                abs(uArithmode.src.r - uArithmode.dst.r),
                abs(uArithmode.src.g - uArithmode.dst.g),
                abs(uArithmode.src.b - uArithmode.dst.b),
                abs(uArithmode.src.a - uArithmode.dst.a),
            );
        }
        default: { result = uArithmode.src; }
    }
    return result;
}
"""

const val ArithmodeSourceHash: String = "fragment:arithmode_main:v1"
const val ArithmodeEntryPoint: String = "arithmode_main"
