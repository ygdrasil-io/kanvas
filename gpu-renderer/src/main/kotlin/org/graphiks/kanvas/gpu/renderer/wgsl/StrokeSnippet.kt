package org.graphiks.kanvas.gpu.renderer.wgsl

const val StrokeWgsl: String = """
struct StrokeUniforms {
    color: vec4f,
    params: vec4f,
    dashParams: vec4f,
};

@group(0) @binding(0) var<uniform> uniforms: StrokeUniforms;

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

@fragment
fn fs_main(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let capJoin = uniforms.params.x;
    let strokeWidth = uniforms.params.y;
    let dashLen = uniforms.dashParams.x;
    let gapLen = uniforms.dashParams.y;
    let edgeDist = uniforms.params.z;
    let halfW = strokeWidth * 0.5;
    let halfH = uniforms.params.w * 0.5;
    let cx = uniforms.params.z;
    let cy = uniforms.params.w;
    let dx = abs(pos.x - cx) / max(halfW, 1.0);
    let dy = abs(pos.y - cy) / max(halfH, 1.0);
    let inside = 1.0 - smoothstep(0.4, 0.6, max(dx, dy));
    return vec4f(uniforms.color.rgb, uniforms.color.a * inside);
}
"""

const val StrokeSnippetSourceHash: String = "fragment:stroke:v1"
const val StrokeEntryPoint: String = "stroke_fill"
