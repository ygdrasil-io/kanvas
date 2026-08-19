package org.graphiks.kanvas.gpu.renderer.wgsl

const val ColorMatrixWgsl: String = """
struct ColorMatrixUniforms {
    color: vec4f,
    m0: vec4f,
    m1: vec4f,
    m2: vec4f,
    m3: vec4f,
    m4: vec4f,
};

@group(0) @binding(0) var<uniform> uniforms: ColorMatrixUniforms;

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

@fragment
fn fs_main() -> @location(0) vec4f {
    let c = uniforms.color;
    let filtered = clamp(
        vec4f(
            dot(uniforms.m0, c),
            dot(uniforms.m1, c),
            dot(uniforms.m2, c),
            dot(uniforms.m3, c),
        ) + uniforms.m4,
        vec4f(0.0),
        vec4f(1.0),
    );
    return vec4f(filtered.rgb * filtered.a, filtered.a);
}
"""
