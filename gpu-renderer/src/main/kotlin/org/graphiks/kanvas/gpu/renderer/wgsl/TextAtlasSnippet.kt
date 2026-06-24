package org.graphiks.kanvas.gpu.renderer.wgsl

const val TextAtlasA8Wgsl: String = """
@group(1) @binding(1) var a8_atlas_texture: texture_2d<f32>;
@group(1) @binding(2) var a8_atlas_sampler: sampler;

fn text_atlas_source(uv: vec2<f32>) -> vec4<f32> {
    let a8 = textureSample(a8_atlas_texture, a8_atlas_sampler, uv).r;
    return vec4<f32>(1.0, 1.0, 1.0, a8);
}
"""

const val TextAtlasSnippetSourceHash: String = "fragment:text_atlas_a8:v1"
const val TextAtlasA8EntryPoint: String = "text_atlas_source"
