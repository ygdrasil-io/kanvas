package org.graphiks.kanvas.gpu.renderer.wgsl

const val PreparedTextA8SourceHash: String = "fragment:text.prepared_a8:v1"
const val PreparedTextA8EntryPoint: String = "fs_main"

object PreparedTextA8Shader {
    val WGSL_SOURCE: String = """
struct TextInstance {
    quadPosition: vec2<f32>,
    quadSize: vec2<f32>,
    uvOrigin: vec2<f32>,
    uvSize: vec2<f32>,
}

struct TextUniforms {
    paintAlpha: f32,
    _padding0: f32,
    _padding1: f32,
    _padding2: f32,
}

struct MaterialBlock {
    color: vec4<f32>,
}

struct VertexOutput {
    @builtin(position) position: vec4<f32>,
    @location(0) uv: vec2<f32>,
    @location(1) localPosition: vec2<f32>,
}

@group(0) @binding(0) var<uniform> text: TextUniforms;
@group(0) @binding(1) var<storage, read> instances: array<TextInstance>;
@group(1) @binding(0) var<uniform> material: MaterialBlock;
@group(1) @binding(1) var textAtlas: texture_2d<f32>;
@group(1) @binding(2) var textSampler: sampler;

@vertex
fn vs_main(
    @builtin(vertex_index) vertexIndex: u32,
    @builtin(instance_index) instanceIndex: u32,
) -> VertexOutput {
    let current = instances[instanceIndex];
    var localOffsets = array<vec2<f32>, 6>(
        vec2<f32>(0.0, 0.0),
        vec2<f32>(1.0, 0.0),
        vec2<f32>(1.0, 1.0),
        vec2<f32>(0.0, 0.0),
        vec2<f32>(1.0, 1.0),
        vec2<f32>(0.0, 1.0),
    );
    let local = localOffsets[vertexIndex];
    var output: VertexOutput;
    output.position = vec4<f32>(
        current.quadPosition.x + local.x * current.quadSize.x,
        current.quadPosition.y + local.y * current.quadSize.y,
        0.0,
        1.0,
    );
    output.uv = vec2<f32>(
        current.uvOrigin.x + local.x * current.uvSize.x,
        current.uvOrigin.y + local.y * current.uvSize.y,
    );
    output.localPosition = local;
    return output;
}

fn evaluate_prepared_material(localPosition: vec2<f32>) -> vec4<f32> {
    return material.color;
}

@fragment
fn fs_main(input: VertexOutput) -> @location(0) vec4<f32> {
    let paintStraightLinear = evaluate_prepared_material(input.localPosition);
    let paintAlpha = clamp(text.paintAlpha, 0.0, 1.0);
    let coverage = textureSample(textAtlas, textSampler, input.uv).r;
    let sourceAlpha = paintStraightLinear.a * paintAlpha * coverage;
    return vec4<f32>(
        paintStraightLinear.rgb * sourceAlpha,
        sourceAlpha,
    );
}
""".trimIndent()
}
