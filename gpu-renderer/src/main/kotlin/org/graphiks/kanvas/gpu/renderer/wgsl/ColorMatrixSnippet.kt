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

fn srgb_to_linear(channel: f32) -> f32 {
    if (channel <= 0.04045) {
        return channel / 12.92;
    }
    return pow((channel + 0.055) / 1.055, 2.4);
}

fn linear_to_srgb(channel: f32) -> f32 {
    if (channel <= 0.0031308) {
        return channel * 12.92;
    }
    return 1.055 * pow(channel, 1.0 / 2.4) - 0.055;
}

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

@fragment
fn fs_main() -> @location(0) vec4f {
    let c = clamp(uniforms.color, vec4f(0.0), vec4f(1.0));
    let linear = vec4f(
        srgb_to_linear(c.r),
        srgb_to_linear(c.g),
        srgb_to_linear(c.b),
        c.a,
    );
    let filtered = clamp(
        vec4f(
            dot(uniforms.m0, linear),
            dot(uniforms.m1, linear),
            dot(uniforms.m2, linear),
            dot(uniforms.m3, linear),
        ) + uniforms.m4,
        vec4f(0.0),
        vec4f(1.0),
    );
    let encoded = vec3f(
        linear_to_srgb(filtered.r),
        linear_to_srgb(filtered.g),
        linear_to_srgb(filtered.b),
    );
    return vec4f(encoded * filtered.a, filtered.a);
}
"""
