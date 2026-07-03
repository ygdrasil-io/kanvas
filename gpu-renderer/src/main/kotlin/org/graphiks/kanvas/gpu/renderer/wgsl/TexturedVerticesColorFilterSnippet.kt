package org.graphiks.kanvas.gpu.renderer.wgsl

const val TexturedVerticesColorFilterWgsl: String = """
struct VertexInput {
    @location(0) position: vec2<f32>,
    @location(1) uv: vec2<f32>,
};

struct VertexOutput {
    @builtin(position) position: vec4<f32>,
    @location(0) uv: vec2<f32>,
    @location(1) @interpolate(flat) alpha: f32,
    @location(2) @interpolate(flat) matrixRow0: vec4<f32>,
    @location(3) @interpolate(flat) matrixRow1: vec4<f32>,
    @location(4) @interpolate(flat) matrixRow2: vec4<f32>,
    @location(5) @interpolate(flat) matrixRow3: vec4<f32>,
};

struct Uniforms {
    alpha: f32,
    colorMatrix: mat4x4<f32>,
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
        uniforms.colorMatrix[0],
        uniforms.colorMatrix[1],
        uniforms.colorMatrix[2],
        uniforms.colorMatrix[3],
    );
}

@fragment
fn fs_main(input: VertexOutput) -> @location(0) vec4<f32> {
    let color = textureSample(texture_sampled, texture_sampler, input.uv);
    let m = mat4x4<f32>(input.matrixRow0, input.matrixRow1, input.matrixRow2, input.matrixRow3);
    let filtered = m * color;
    return vec4<f32>(filtered.rgb, filtered.a * input.alpha);
}
"""

const val TexturedVerticesColorFilterSnippetSourceHash: String = "vertex:textured_vertices_cf:v1"
const val TexturedVerticesColorFilterShaderEntryPoint: String = "vs_main"
const val TexturedVerticesColorFilterFragmentEntryPoint: String = "fs_main"
