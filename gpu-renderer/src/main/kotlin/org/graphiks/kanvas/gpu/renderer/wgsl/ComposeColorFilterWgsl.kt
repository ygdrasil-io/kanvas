package org.graphiks.kanvas.gpu.renderer.wgsl

const val ComposeColorFilterWgsl: String = """
@group(0) @binding(0) var inner: texture_2d<f32>;
@group(0) @binding(1) var inner_sampler: sampler;
@group(0) @binding(2) var outer: texture_2d<f32>;
@group(0) @binding(3) var outer_sampler: sampler;

fn compose_cf_source(inColor: vec4<f32>) -> vec4<f32> {
    return inColor;
}
"""

const val ComposeColorFilterSourceHash: String = "fragment:compose_cf:v1"
const val ComposeColorFilterEntryPoint: String = "compose_cf_source"
