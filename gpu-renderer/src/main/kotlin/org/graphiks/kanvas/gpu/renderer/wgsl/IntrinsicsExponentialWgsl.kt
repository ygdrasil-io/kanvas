package org.graphiks.kanvas.gpu.renderer.wgsl

const val IntrinsicsExponentialWgsl: String = """
struct IntrinsicsExponentialUniform {
    testCase: i32,
    x: f32,
    y: f32,
}
@group(1) @binding(0) var<uniform> uIntrinsicsExp: IntrinsicsExponentialUniform;

fn intrinsics_exponential(uv: vec2<f32>) -> vec4<f32> {
    var result: f32 = 0.0;
    switch (uIntrinsicsExp.testCase) {
        case 0: { result = pow(uIntrinsicsExp.x, uIntrinsicsExp.y); }
        case 1: { result = exp(uIntrinsicsExp.x); }
        case 2: { result = exp2(uIntrinsicsExp.x); }
        case 3: { result = log(uIntrinsicsExp.x); }
        case 4: { result = log2(uIntrinsicsExp.x); }
        case 5: { result = sqrt(uIntrinsicsExp.x); }
        case 6: { result = inversesqrt(uIntrinsicsExp.x); }
        default: { result = 0.0; }
    }
    return vec4<f32>(result, result, result, 1.0);
}
"""

const val IntrinsicsExponentialSourceHash: String = "fragment:intrinsics_exponential:v1"
const val IntrinsicsExponentialEntryPoint: String = "intrinsics_exponential"
