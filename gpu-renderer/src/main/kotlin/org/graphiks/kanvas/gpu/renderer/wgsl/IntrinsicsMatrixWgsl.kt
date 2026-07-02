package org.graphiks.kanvas.gpu.renderer.wgsl

const val IntrinsicsMatrixWgsl: String = """
struct IntrinsicsMatrixUniform {
    testCase: i32,
    input: mat4x4<f32>,
    vec: vec4<f32>,
}
@group(1) @binding(0) var<uniform> uIntrinsicsMat: IntrinsicsMatrixUniform;

fn intrinsics_matrix(uv: vec2<f32>) -> vec4<f32> {
    var result: vec4<f32>;
    switch (uIntrinsicsMat.testCase) {
        case 0: { let d = determinant(uIntrinsicsMat.input); result = vec4<f32>(d, d, d, 1.0); }
        case 1: { let t = transpose(uIntrinsicsMat.input); result = t[0]; }
        case 2: { let v = uIntrinsicsMat.input * uIntrinsicsMat.vec; result = v; }
        default: { result = vec4<f32>(0.0, 0.0, 0.0, 1.0); }
    }
    return result;
}
"""

const val IntrinsicsMatrixSourceHash: String = "fragment:intrinsics_matrix:v1"
const val IntrinsicsMatrixEntryPoint: String = "intrinsics_matrix"
