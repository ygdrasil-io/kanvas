package org.graphiks.kanvas.gpu.renderer.wgsl

const val RtifDistortWgsl: String = """
@group(0) @binding(0) var child: texture_2d<f32>;
@group(0) @binding(1) var child_sampler: sampler;

fn rtif_distort_source(uv: vec2<f32>) -> vec4<f32> {
    var coord = uv;
    coord.x += sin(coord.y / 3.0) * 4.0;
    return textureSampleLevel(child, child_sampler, coord, 0.0);
}
"""

const val RtifDistortSourceHash: String = "fragment:rtif_distort:v1"
const val RtifDistortEntryPoint: String = "rtif_distort_source"
