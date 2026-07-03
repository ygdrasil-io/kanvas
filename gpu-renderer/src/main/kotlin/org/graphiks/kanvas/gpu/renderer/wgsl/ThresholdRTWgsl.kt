package org.graphiks.kanvas.gpu.renderer.wgsl

const val ThresholdRTWgsl: String = """
struct ThresholdRTUniforms {
    cutoff: f32,
    slope: f32,
}
@group(1) @binding(0) var<uniform> uThresholdRT: ThresholdRTUniforms;
@group(0) @binding(0) var before_map: texture_2d<f32>;
@group(0) @binding(1) var before_sampler: sampler;
@group(0) @binding(2) var after_map: texture_2d<f32>;
@group(0) @binding(3) var after_sampler: sampler;
@group(0) @binding(4) var threshold_map: texture_2d<f32>;
@group(0) @binding(5) var threshold_sampler: sampler;

fn threshold_rt_source(uv: vec2<f32>) -> vec4<f32> {
    let before = textureSampleLevel(before_map, before_sampler, uv, 0.0);
    let after = textureSampleLevel(after_map, after_sampler, uv, 0.0);
    let threshold = textureSampleLevel(threshold_map, threshold_sampler, uv, 0.0);
    let t = (threshold.r - uThresholdRT.cutoff) * uThresholdRT.slope;
    let st = clamp(t, 0.0, 1.0);
    return mix(before, after, st);
}
"""

const val ThresholdRTSourceHash: String = "fragment:threshold_rt:v1"
const val ThresholdRTEntryPoint: String = "threshold_rt_source"
