package org.graphiks.kanvas.gpu.renderer.wgsl

const val BlurWgsl: String = """
struct BlurUniforms {
    color: vec4f,
    center: vec4f,
    radius: f32,
};

@group(0) @binding(0) var<uniform> uniforms: BlurUniforms;

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

@fragment
fn fs_main(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let dx = pos.x - uniforms.center.x;
    let dy = pos.y - uniforms.center.y;
    let dist = sqrt(dx * dx + dy * dy);
    let sigma = max(uniforms.radius * 2.0, 0.5);
    let blur = exp(-dist * dist / (2.0 * sigma * sigma));
    return vec4f(uniforms.color.rgb, uniforms.color.a * blur);
}
"""


