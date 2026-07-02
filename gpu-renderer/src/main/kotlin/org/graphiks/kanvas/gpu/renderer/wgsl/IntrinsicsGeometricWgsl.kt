package org.graphiks.kanvas.gpu.renderer.wgsl

const val IntrinsicsGeometricWgsl: String = """
struct IntrinsicsGeometricUniform {
    testCase: i32,
    a: vec3<f32>,
    b: vec3<f32>,
}
@group(1) @binding(0) var<uniform> uIntrinsicsGeo: IntrinsicsGeometricUniform;

fn intrinsics_geometric(uv: vec2<f32>) -> vec4<f32> {
    var result: vec4<f32>;
    switch (uIntrinsicsGeo.testCase) {
        case 0: { let v = length(uIntrinsicsGeo.a); result = vec4<f32>(v, v, v, 1.0); }
        case 1: { let v = distance(uIntrinsicsGeo.a, uIntrinsicsGeo.b); result = vec4<f32>(v, v, v, 1.0); }
        case 2: { let v = dot(uIntrinsicsGeo.a, uIntrinsicsGeo.b); result = vec4<f32>(v, v, v, 1.0); }
        case 3: { let v = cross(uIntrinsicsGeo.a, uIntrinsicsGeo.b); result = vec4<f32>(v, 1.0); }
        case 4: { let v = normalize(uIntrinsicsGeo.a); result = vec4<f32>(v, 1.0); }
        case 5: { let v = reflect(uIntrinsicsGeo.a, normalize(uIntrinsicsGeo.b)); result = vec4<f32>(v, 1.0); }
        case 6: { let v = refract(uIntrinsicsGeo.a, normalize(uIntrinsicsGeo.b), 0.5); result = vec4<f32>(v, 1.0); }
        default: { result = vec4<f32>(0.0, 0.0, 0.0, 1.0); }
    }
    return result;
}
"""

const val IntrinsicsGeometricSourceHash: String = "fragment:intrinsics_geometric:v1"
const val IntrinsicsGeometricEntryPoint: String = "intrinsics_geometric"
