package org.graphiks.kanvas.gpu.renderer.wgsl

/** WGSL snippet for the stencil-cover resolve pass: fullscreen vertex + fill fragment. */
const val StencilCoverResolveWgsl: String = """
@vertex
fn vs_fullscreen(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

struct StencilCoverOutput {
    @builtin(frag_depth) depth: f32,
    @location(0) color: vec4f,
}

@fragment
fn stencil_cover_resolve(@builtin(position) pos: vec4f) -> StencilCoverOutput {
    let fillColor = uniforms.color;
    let aa = vec2f(fwidth(pos.xy));
    let cover = 1.0;
    return StencilCoverOutput(0.0, fillColor * cover);
}
"""

/** Stable source hash for the stencil-cover resolve WGSL snippet. */
const val StencilCoverSnippetSourceHash: String = "fragment:stencil_cover_resolve:v1"

/** Fragment entry-point name for the stencil-cover resolve pass. */
const val StencilCoverEntryPoint: String = "stencil_cover_resolve"
