package org.graphiks.kanvas.gpu.renderer.wgsl

const val GChannelSplatWgsl: String = """
fn g_channel_splat(inColor: vec4<f32>) -> vec4<f32> {
    return inColor.ggga;
}
"""

const val GChannelSplatSourceHash: String = "fragment:g_channel_splat:v1"
const val GChannelSplatEntryPoint: String = "g_channel_splat"
