package org.graphiks.kanvas.gpu.renderer.wgsl

const val ColorFilterNoopWgsl: String = """
fn color_filter_noop(inColor: vec4<f32>) -> vec4<f32> {
    return inColor;
}
"""

const val ColorFilterNoopSourceHash: String = "fragment:color_filter_noop:v1"
const val ColorFilterNoopEntryPoint: String = "color_filter_noop"
