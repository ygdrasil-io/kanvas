package org.graphiks.kanvas.gpu.renderer.wgsl

const val IntrinsicsCommonWgsl: String = """
struct IntrinsicsCommonUniform {
    testCase: i32,
    a: f32,
    b: f32,
    edge: f32,
}
@group(1) @binding(0) var<uniform> uIntrinsicsCommon: IntrinsicsCommonUniform;

fn intrinsics_common(uv: vec2<f32>) -> vec4<f32> {
    var result: f32 = 0.0;
    switch (uIntrinsicsCommon.testCase) {
        case 0: { result = mix(uIntrinsicsCommon.a, uIntrinsicsCommon.b, 0.5); }
        case 1: { result = clamp(uIntrinsicsCommon.a, 0.0, 1.0); }
        case 2: { result = saturate(uIntrinsicsCommon.a); }
        case 3: { result = step(uIntrinsicsCommon.edge, uIntrinsicsCommon.a); }
        case 4: { result = smoothstep(0.0, 1.0, uIntrinsicsCommon.a); }
        case 5: { result = abs(uIntrinsicsCommon.a); }
        case 6: { result = sign(uIntrinsicsCommon.a); }
        case 7: { result = floor(uIntrinsicsCommon.a); }
        case 8: { result = ceil(uIntrinsicsCommon.a); }
        case 9: { result = fract(uIntrinsicsCommon.a); }
        case 10: { result = mod(uIntrinsicsCommon.a, uIntrinsicsCommon.b); }
        case 11: { result = min(uIntrinsicsCommon.a, uIntrinsicsCommon.b); }
        case 12: { result = max(uIntrinsicsCommon.a, uIntrinsicsCommon.b); }
        default: { result = 0.0; }
    }
    return vec4<f32>(result, result, result, 1.0);
}
"""

const val IntrinsicsCommonSourceHash: String = "fragment:intrinsics_common:v1"
const val IntrinsicsCommonEntryPoint: String = "intrinsics_common"
