package org.graphiks.kanvas.gpu.renderer.wgsl

const val SpiralRTWgsl: String = """
struct SpiralRTUniform {
    center: vec4<f32>,
    color1: vec4<f32>,
    color2: vec4<f32>,
    params: vec4<f32>,
}
@group(1) @binding(0) var<uniform> uSpiralRT: SpiralRTUniform;

fn spiral_rt_source(uv: vec2<f32>) -> vec4<f32> {
    let cx = uSpiralRT.center.x;
    let cy = uSpiralRT.center.y;
    let dx = uv.x - cx;
    let dy = uv.y - cy;
    let dist = sqrt(dx * dx + dy * dy);
    let angle = atan2(dy, dx);
    let spiral = sin(angle + dist * uSpiralRT.params.x) * 0.5 + 0.5;
    return mix(uSpiralRT.color1, uSpiralRT.color2, spiral);
}
"""

const val SpiralRTSourceHash: String = "fragment:spiral_rt:v1"
const val SpiralRTEntryPoint: String = "spiral_rt_source"
