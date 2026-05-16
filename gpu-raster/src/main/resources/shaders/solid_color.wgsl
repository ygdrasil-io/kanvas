// G1.2 shader -- full-screen triangle, fragment outputs a uniform
// color. Pair with `setScissorRect(x, y, w, h)` on the render pass
// to paint just the rect under the scissor; the vertex stage emits
// the same big triangle for every draw and lets the rasterizer
// clip to the scissor.
//
// ASCII strict in comments -- WGSL parser truncates on non-ASCII in
// wgpu4k 0.2.0 (cf. MIGRATION_PLAN_GPU_WEBGPU.md G0 post-mortem #4).

struct Uniforms {
    color: vec4f,
};

@binding(0) @group(0) var<uniform> uniforms: Uniforms;

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

@fragment
fn fs_main() -> @location(0) vec4f {
    // SrcOver blending in the pipeline (src=One, dst=OneMinusSrcAlpha)
    // expects PREMUL fragment output. Source colors enter the uniform
    // unpremultiplied (Skia's SkColor convention is non-premul) -- premul
    // here, in the shader, so the blend math sees correct values for
    // translucent fills. G2.1 unlocks alpha < 1.0; opaque (alpha == 1.0)
    // remains a no-op since rgb * 1.0 == rgb.
    let c = uniforms.color;
    return vec4f(c.rgb * c.a, c.a);
}
