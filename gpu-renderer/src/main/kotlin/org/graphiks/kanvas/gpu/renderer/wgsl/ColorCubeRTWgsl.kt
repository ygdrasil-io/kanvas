package org.graphiks.kanvas.gpu.renderer.wgsl

const val ColorCubeRTWgsl: String = """
struct ColorCubeRTUniforms {
    rg_scale: f32,
    rg_bias: f32,
    b_scale: f32,
    inv_size: f32,
}
@group(1) @binding(0) var<uniform> uColorCubeRT: ColorCubeRTUniforms;
@group(0) @binding(0) var child: texture_2d<f32>;
@group(0) @binding(1) var child_sampler: sampler;
@group(0) @binding(2) var color_cube: texture_2d<f32>;
@group(0) @binding(3) var color_cube_sampler: sampler;

fn color_cube_rt_source(uv: vec2<f32>) -> vec4<f32> {
    let c = textureSampleLevel(child, child_sampler, uv, 0.0);
    let lutCoord = vec2<f32>(
        uColorCubeRT.rg_scale * c.r + uColorCubeRT.rg_bias,
        uColorCubeRT.rg_scale * c.g + uColorCubeRT.rg_bias
    );
    let bCoord = uColorCubeRT.b_scale * c.b + uColorCubeRT.rg_bias;
    let lookup0 = textureSampleLevel(color_cube, color_cube_sampler, lutCoord, 0.0);
    let t = fract(bCoord * uColorCubeRT.inv_size);
    let offset = floor(bCoord * uColorCubeRT.inv_size) * uColorCubeRT.inv_size;
    let lookup1 = textureSampleLevel(color_cube, color_cube_sampler, lutCoord + vec2<f32>(offset, 0.0), 0.0);
    return mix(lookup0, lookup1, vec4<f32>(t));
}
"""

const val ColorCubeRTSourceHash: String = "fragment:color_cube_rt:v1"
const val ColorCubeRTEntryPoint: String = "color_cube_rt_source"
