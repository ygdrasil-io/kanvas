package org.graphiks.kanvas.gpu.renderer.wgsl

const val UnsharpRTWgsl: String = """
@group(0) @binding(0) var child: texture_2d<f32>;
@group(0) @binding(1) var child_sampler: sampler;

fn unsharp_rt_source(uv: vec2<f32>) -> vec4<f32> {
    let texelSize = vec2<f32>(1.0 / 256.0, 0.0);
    let stepX = vec2<f32>(texelSize.x, 0.0);
    let stepY = vec2<f32>(0.0, texelSize.x);
    let c = textureSampleLevel(child, child_sampler, uv, 0.0);
    let n = textureSampleLevel(child, child_sampler, uv + stepY, 0.0);
    let s_val = textureSampleLevel(child, child_sampler, uv - stepY, 0.0);
    let e = textureSampleLevel(child, child_sampler, uv + stepX, 0.0);
    let w = textureSampleLevel(child, child_sampler, uv - stepX, 0.0);
    return clamp(c * 5.0 - n - s_val - e - w, vec4<f32>(0.0), vec4<f32>(1.0));
}
"""

const val UnsharpRTSourceHash: String = "fragment:unsharp_rt:v1"
const val UnsharpRTEntryPoint: String = "unsharp_rt_source"
