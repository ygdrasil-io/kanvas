package org.graphiks.kanvas.gpu.renderer.wgsl

const val RuntimeFunctionsWgsl: String = """
fn double_value(x: f32) -> f32 {
    return x * 2.0;
}

fn add_colors(a: vec4<f32>, b: vec4<f32>) -> vec4<f32> {
    return vec4<f32>(a.r + b.r, a.g + b.g, a.b + b.b, a.a + b.a);
}

fn runtime_functions_main(uv: vec2<f32>) -> vec4<f32> {
    let base = vec4<f32>(uv, 0.0, 1.0);
    let doubled = vec4<f32>(double_value(base.r), double_value(base.g), double_value(base.b), base.a);
    return add_colors(base, doubled);
}
"""

const val RuntimeFunctionsSourceHash: String = "fragment:runtime_functions_main:v1"
const val RuntimeFunctionsEntryPoint: String = "runtime_functions_main"
