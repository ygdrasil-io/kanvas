package org.graphiks.kanvas.gpu.renderer.wgsl

const val IntrinsicsTrigWgsl: String = """
struct IntrinsicsTrigUniform {
    testCase: i32,
    x: f32,
    y: f32,
}
@group(1) @binding(0) var<uniform> uIntrinsicsTrig: IntrinsicsTrigUniform;

fn intrinsics_trig(uv: vec2<f32>) -> vec4<f32> {
    var result: f32 = 0.0;
    switch (uIntrinsicsTrig.testCase) {
        case 0: { result = sin(uIntrinsicsTrig.x); }
        case 1: { result = cos(uIntrinsicsTrig.x); }
        case 2: { result = tan(uIntrinsicsTrig.x); }
        case 3: { result = asin(uIntrinsicsTrig.x); }
        case 4: { result = acos(uIntrinsicsTrig.x); }
        case 5: { result = atan2(uIntrinsicsTrig.y, uIntrinsicsTrig.x); }
        case 6: { result = sinh(uIntrinsicsTrig.x); }
        case 7: { result = cosh(uIntrinsicsTrig.x); }
        case 8: { result = tanh(uIntrinsicsTrig.x); }
        default: { result = 0.0; }
    }
    return vec4<f32>(result, result, result, 1.0);
}
"""

const val IntrinsicsTrigSourceHash: String = "fragment:intrinsics_trig:v1"
const val IntrinsicsTrigEntryPoint: String = "intrinsics_trig"
