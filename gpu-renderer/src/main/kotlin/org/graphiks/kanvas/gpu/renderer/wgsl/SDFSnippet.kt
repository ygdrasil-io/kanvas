package org.graphiks.kanvas.gpu.renderer.wgsl

const val SDFSamplingWgsl: String = """
@group(1) @binding(1) var sdf_atlas_texture: texture_2d<f32>;
@group(1) @binding(2) var sdf_atlas_sampler: sampler;

fn sdf_sample(uv: vec2<f32>) -> vec4<f32> {
    let sdf = textureSample(sdf_atlas_texture, sdf_atlas_sampler, uv).r;
    let alpha = smoothstep(0.45, 0.55, sdf);
    return vec4<f32>(1.0, 1.0, 1.0, alpha);
}
"""

const val SDFSnippetSourceHash: String = "fragment:sdf_sampling:v1"
const val SDFSamplingEntryPoint: String = "sdf_sample"
