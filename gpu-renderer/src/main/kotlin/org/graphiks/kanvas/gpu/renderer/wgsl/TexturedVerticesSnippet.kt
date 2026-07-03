package org.graphiks.kanvas.gpu.renderer.wgsl

const val TexturedVerticesWgsl: String = """
struct VertexInput {
    @location(0) position: vec2<f32>,
    @location(1) uv: vec2<f32>,
};

struct VertexOutput {
    @builtin(position) position: vec4<f32>,
    @location(0) uv: vec2<f32>,
    @location(1) @interpolate(flat) alpha: f32,
};

struct Uniforms {
    alpha: f32,
};

@group(0) @binding(0) var<uniform> uniforms: Uniforms;
@group(1) @binding(1) var texture_sampled: texture_2d<f32>;
@group(1) @binding(2) var texture_sampler: sampler;

@vertex
fn vs_main(input: VertexInput) -> VertexOutput {
    return VertexOutput(
        vec4<f32>(input.position, 0.0, 1.0),
        input.uv,
        uniforms.alpha,
    );
}

@fragment
fn fs_main(input: VertexOutput) -> @location(0) vec4<f32> {
    let color = textureSample(texture_sampled, texture_sampler, input.uv);
    return vec4<f32>(color.rgb, color.a * input.alpha);
}
"""

const val TexturedVerticesSnippetSourceHash: String = "vertex:textured_vertices:v1"
const val TexturedVerticesShaderEntryPoint: String = "vs_main"
const val TexturedVerticesFragmentEntryPoint: String = "fs_main"
