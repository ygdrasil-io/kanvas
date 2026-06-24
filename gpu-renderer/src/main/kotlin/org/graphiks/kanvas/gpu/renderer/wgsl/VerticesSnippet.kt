package org.graphiks.kanvas.gpu.renderer.wgsl

const val VerticesWgsl: String = """
struct VertexInput {
    @location(0) position: vec2<f32>,
    @location(1) color: vec4<f32>,
};

struct VertexOutput {
    @builtin(position) position: vec4<f32>,
    @location(0) color: vec4<f32>,
};

@vertex
fn vs_main(input: VertexInput) -> VertexOutput {
    return VertexOutput(
        position = vec4<f32>(input.position, 0.0, 1.0),
        color = input.color,
    );
}

@fragment
fn fs_main(input: VertexOutput) -> @location(0) vec4<f32> {
    return input.color;
}
"""

const val VerticesSnippetSourceHash: String = "vertex:vertices:v1"
const val VerticesShaderEntryPoint: String = "vs_main"
const val VerticesFragmentEntryPoint: String = "fs_main"
