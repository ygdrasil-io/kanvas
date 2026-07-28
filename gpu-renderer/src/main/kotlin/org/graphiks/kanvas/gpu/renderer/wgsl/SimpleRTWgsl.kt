package org.graphiks.kanvas.gpu.renderer.wgsl

internal const val SimpleRTEffectId: String = "runtime.simple_rt"
internal const val SimpleRTDescriptorVersion: Int = 1
internal const val SimpleRTUniformSchemaHash: String = "schema:simple_rt:v1"
internal const val SimpleRTUniformBlockSizeBytes: Int = 16
internal const val SimpleRTBindingPlanHash: String = "binding:simple_rt:v1"

const val SimpleRTWgsl: String = """
struct SimpleRTUniform {
    gColor: vec4<f32>,
}
@group(1) @binding(0) var<uniform> uSimpleRT: SimpleRTUniform;

fn simple_rt_source(uv: vec2<f32>) -> vec4<f32> {
    return uSimpleRT.gColor;
}
"""

const val SimpleRTEntryPoint: String = "simple_rt_source"

val SimpleRTSourceHash: String = wgslSourceContentHash(SimpleRTWgsl)
internal val SimpleRTModuleHash: String =
    wgslModuleContentHash(SimpleRTWgsl, SimpleRTEntryPoint)
internal val SimpleRTReflectionHash: String = WgslReflectionReport(
    sourceId = SimpleRTSourceHash,
    entryPoints = emptyList(),
    bindings = listOf(
        WgslBindingReflection(
            group = 1,
            binding = 0,
            name = "uSimpleRT",
            resourceKind = "uniformBuffer",
            access = "read",
            minBindingSize = SimpleRTUniformBlockSizeBytes,
        ),
    ),
    layouts = listOf(
        WgslLayoutReflection(
            structName = "SimpleRTUniform",
            addressSpace = "uniform",
            size = SimpleRTUniformBlockSizeBytes,
            alignment = 16,
            members = listOf(
                WgslLayoutMemberReflection(
                    name = "gColor",
                    type = "vec4<f32>",
                    offset = 0,
                    size = 16,
                    alignment = 16,
                ),
            ),
        ),
    ),
).reflectionFactsHash()
