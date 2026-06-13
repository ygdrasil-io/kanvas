// KAN-054 -- bounded simple-latin glyph atlas sampling route.
// ASCII strict -- WGSL parser truncates on non-ASCII in wgpu4k 0.2.0.

struct VertexIn {
    @location(0) pos: vec2f,
    @location(1) uv: vec2f,
};

struct VertexOut {
    @builtin(position) position: vec4f,
    @location(0) uv: vec2f,
};

struct Uniforms {
    viewport: vec4f,
    paintColor: vec4f,
};

@group(0) @binding(0) var<uniform> uniforms: Uniforms;
@group(0) @binding(1) var atlasTex: texture_2d<f32>;
@group(0) @binding(2) var atlasSampler: sampler;

@vertex
fn vs_main(input: VertexIn) -> VertexOut {
    let x = (input.pos.x / uniforms.viewport.x) * 2.0 - 1.0;
    let y = 1.0 - (input.pos.y / uniforms.viewport.y) * 2.0;
    var out: VertexOut;
    out.position = vec4f(x, y, 0.0, 1.0);
    out.uv = input.uv;
    return out;
}

@fragment
fn fs_main(input: VertexOut) -> @location(0) vec4f {
    let coverage = textureSampleLevel(atlasTex, atlasSampler, input.uv, 0.0).r;
    let alpha = coverage * uniforms.paintColor.a;
    return vec4f(uniforms.paintColor.rgb * alpha, alpha);
}
