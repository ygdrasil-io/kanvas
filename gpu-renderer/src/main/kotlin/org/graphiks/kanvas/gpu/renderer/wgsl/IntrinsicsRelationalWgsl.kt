package org.graphiks.kanvas.gpu.renderer.wgsl

const val IntrinsicsRelationalWgsl: String = """
struct IntrinsicsRelationalUniform {
    testCase: i32,
    a: vec4<f32>,
    b: vec4<f32>,
}
@group(1) @binding(0) var<uniform> uIntrinsicsRel: IntrinsicsRelationalUniform;

fn intrinsics_relational(uv: vec2<f32>) -> vec4<f32> {
    var result: vec4<f32>;
    switch (uIntrinsicsRel.testCase) {
        case 0: { let cond = lessThan(uIntrinsicsRel.a, uIntrinsicsRel.b); result = select(vec4<f32>(0.0), vec4<f32>(1.0), cond); }
        case 1: { let cond = greaterThan(uIntrinsicsRel.a, uIntrinsicsRel.b); result = select(vec4<f32>(0.0), vec4<f32>(1.0), cond); }
        case 2: { let cond = equal(uIntrinsicsRel.a, uIntrinsicsRel.b); result = select(vec4<f32>(0.0), vec4<f32>(1.0), cond); }
        case 3: { let cond = notEqual(uIntrinsicsRel.a, uIntrinsicsRel.b); result = select(vec4<f32>(0.0), vec4<f32>(1.0), cond); }
        case 4: { let v = any(uIntrinsicsRel.a); result = select(vec4<f32>(0.0), vec4<f32>(1.0), vec4<bool>(v, v, v, v)); }
        case 5: { let v = all(uIntrinsicsRel.a); result = select(vec4<f32>(0.0), vec4<f32>(1.0), vec4<bool>(v, v, v, v)); }
        case 6: { let cond = isNan(uIntrinsicsRel.a); result = select(vec4<f32>(0.0), vec4<f32>(1.0), cond); }
        case 7: { let cond = isInf(uIntrinsicsRel.a); result = select(vec4<f32>(0.0), vec4<f32>(1.0), cond); }
        default: { result = vec4<f32>(0.0, 0.0, 0.0, 1.0); }
    }
    return result;
}
"""

const val IntrinsicsRelationalSourceHash: String = "fragment:intrinsics_relational:v1"
const val IntrinsicsRelationalEntryPoint: String = "intrinsics_relational"
