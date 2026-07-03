package org.graphiks.kanvas.gpu.renderer.wgsl

const val KawaseMixWgsl: String = """
struct KawaseMixUniforms {
    in_inverseScale: f32,
    in_mix: f32,
}
@group(1) @binding(0) var<uniform> uKawaseMix: KawaseMixUniforms;
@group(0) @binding(0) var in_blur: texture_2d<f32>;
@group(0) @binding(1) var in_blur_sampler: sampler;
@group(0) @binding(2) var in_original: texture_2d<f32>;
@group(0) @binding(3) var in_original_sampler: sampler;

fn kawase_mix_source(uv: vec2<f32>) -> vec4<f32> {
    let b = textureSampleLevel(in_blur, in_blur_sampler, uv, 0.0);
    let o = textureSampleLevel(in_original, in_original_sampler, uv, 0.0);
    return mix(o, b, uKawaseMix.in_mix);
}
"""

const val KawaseMixSourceHash: String = "fragment:kawase_mix:v1"
const val KawaseMixEntryPoint: String = "kawase_mix_source"
