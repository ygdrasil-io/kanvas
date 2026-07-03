package org.graphiks.kanvas.gpu.renderer.wgsl

const val TexturedVerticesDualBlendWgsl: String = """
struct VertexInput {
    @location(0) position: vec2<f32>,
    @location(1) uv1: vec2<f32>,
    @location(2) uv2: vec2<f32>,
};

struct VertexOutput {
    @builtin(position) position: vec4<f32>,
    @location(0) uv1: vec2<f32>,
    @location(1) uv2: vec2<f32>,
    @location(2) @interpolate(flat) alpha: f32,
    @location(3) @interpolate(flat) blendModeIdx: i32,
};

struct Uniforms {
    alpha: f32,
    blendMode: i32,
};

@group(0) @binding(0) var<uniform> uniforms: Uniforms;
@group(1) @binding(1) var texture1_sampled: texture_2d<f32>;
@group(1) @binding(2) var texture1_sampler: sampler;
@group(1) @binding(3) var texture2_sampled: texture_2d<f32>;
@group(1) @binding(4) var texture2_sampler: sampler;

@vertex
fn vs_main(input: VertexInput) -> VertexOutput {
    return VertexOutput(
        vec4<f32>(input.position, 0.0, 1.0),
        input.uv1,
        input.uv2,
        uniforms.alpha,
        uniforms.blendMode,
    );
}

@fragment
fn fs_main(input: VertexOutput) -> @location(0) vec4<f32> {
    let c1 = textureSample(texture1_sampled, texture1_sampler, input.uv1);
    let c2 = textureSample(texture2_sampled, texture2_sampler, input.uv2);
    var result: vec4<f32>;
    let srcAlpha = c1.a;
    let invSrcAlpha = 1.0 - srcAlpha;
    switch input.blendModeIdx {
        default { result = c1 + c2 * invSrcAlpha; }
        case 1  { result = c2 + c1 * (1.0 - c2.a); }
        case 2  { result = c1; }
        case 3  { result = c2; }
        case 4  { result = c1 * c2; }
    }
    return vec4<f32>(result.rgb, result.a * input.alpha);
}
"""

const val TexturedVerticesDualBlendSnippetSourceHash: String = "vertex:textured_vertices_dual:v1"
const val TexturedVerticesDualBlendShaderEntryPoint: String = "vs_main"
const val TexturedVerticesDualBlendFragmentEntryPoint: String = "fs_main"
