package org.graphiks.kanvas.gpu.renderer.wgsl

const val GChannelSplatWgsl: String = """
fn g_channel_splat(uv: vec2<f32>) -> vec4<f32> {
    var inColor = vec4<f32>(0.0, 0.0, 0.0, 0.0);
    return inColor.ggga;
}
"""

const val GChannelSplatSourceHash: String = "fragment:g_channel_splat:v1"
const val GChannelSplatEntryPoint: String = "g_channel_splat"
