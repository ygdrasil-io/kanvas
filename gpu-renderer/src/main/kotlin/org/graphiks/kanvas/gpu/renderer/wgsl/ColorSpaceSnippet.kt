package org.graphiks.kanvas.gpu.renderer.wgsl

const val ColorSpaceWgsl: String = """
fn to_linear(c: vec4f) -> vec4f {
    let low = c.rgb / 12.92;
    let high = pow((c.rgb + 0.055) / 1.055, vec3f(2.4));
    let mask = vec3f(c.rgb <= 0.04045);
    return vec4f(select(high, low, mask), c.a);
}

fn to_srgb(c: vec4f) -> vec4f {
    let low = c.rgb * 12.92;
    let high = 1.055 * pow(c.rgb, vec3f(1.0/2.4)) - 0.055;
    let mask = vec3f(c.rgb <= 0.0031308);
    return vec4f(select(high, low, mask), c.a);
}

fn interpolate_srgb(a: vec4f, b: vec4f, t: f32) -> vec4f {
    let al = to_linear(a);
    let bl = to_linear(b);
    return to_srgb(mix(al, bl, t));
}

fn interpolate_linear(a: vec4f, b: vec4f, t: f32) -> vec4f {
    return mix(a, b, t);
}
"""

const val ColorSpaceSnippetSourceHash: String = "fragment:color_space:v1"
