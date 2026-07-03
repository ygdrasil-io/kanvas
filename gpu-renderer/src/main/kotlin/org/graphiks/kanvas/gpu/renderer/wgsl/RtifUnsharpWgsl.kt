package org.graphiks.kanvas.gpu.renderer.wgsl

const val RtifUnsharpWgsl: String = """
@group(0) @binding(0) var content: texture_2d<f32>;
@group(0) @binding(1) var content_sampler: sampler;
@group(0) @binding(2) var blurred: texture_2d<f32>;
@group(0) @binding(3) var blurred_sampler: sampler;

fn rtif_unsharp_source(uv: vec2<f32>) -> vec4<f32> {
    let c = textureSampleLevel(content, content_sampler, uv, 0.0);
    let b = textureSampleLevel(blurred, blurred_sampler, uv, 0.0);
    return clamp(c + (c - b) * 4.0, vec4<f32>(0.0), vec4<f32>(1.0));
}
"""

const val RtifUnsharpSourceHash: String = "fragment:rtif_unsharp:v1"
const val RtifUnsharpEntryPoint: String = "rtif_unsharp_source"
