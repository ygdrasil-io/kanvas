package org.graphiks.kanvas.gpu.renderer.wgsl

internal const val SimpleRTEffectId: String = "runtime.simple_rt"
internal const val SimpleRTDescriptorVersion: Int = 1
internal const val SimpleRTUniformSchemaHash: String = "schema:simple_rt:v1"
internal const val SimpleRTUniformBlockSizeBytes: Int = 16
internal const val SimpleRTBindingPlanHash: String = "binding:simple_rt:v1"
internal const val SimpleRTModuleHash: String = "module:simple_rt:v1"
internal const val SimpleRTReflectionHash: String = "reflection:simple_rt:v1"

const val SimpleRTWgsl: String = """
struct SimpleRTUniform {
    gColor: vec4<f32>,
}
@group(1) @binding(0) var<uniform> uSimpleRT: SimpleRTUniform;

fn simple_rt_source(uv: vec2<f32>) -> vec4<f32> {
    return uSimpleRT.gColor;
}
"""

const val SimpleRTSourceHash: String = "fragment:simple_rt:v1"
const val SimpleRTEntryPoint: String = "simple_rt_source"
