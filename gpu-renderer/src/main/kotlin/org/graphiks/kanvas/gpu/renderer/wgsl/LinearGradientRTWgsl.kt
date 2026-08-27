package org.graphiks.kanvas.gpu.renderer.wgsl

/**
 * Registered two-stop linear-gradient runtime effect.
 *
 * Its input is in local pixel coordinates.  The program deliberately has no
 * source compiler or child effects: callers select this exact descriptor and
 * provide the closed 64-byte uniform block.
 */
internal const val LinearGradientRTEffectId: String = "runtime.linear_gradient_rt"
internal const val LinearGradientRTDescriptorVersion: Int = 1
internal const val LinearGradientRTUniformSchemaHash: String = "schema:linear_gradient_rt:v2"
internal const val LinearGradientRTUniformBlockSizeBytes: Int = 64
internal const val LinearGradientRTBindingPlanHash: String = "binding:linear_gradient_rt:v2"

const val LinearGradientRTWgsl: String = """
struct LinearGradientRTUniform {
    start: vec4<f32>,
    end: vec4<f32>,
    startColor: vec4<f32>,
    endColor: vec4<f32>,
}
@group(1) @binding(0) var<uniform> uLinearGradientRT: LinearGradientRTUniform;

fn linear_gradient_rt_source(localPosition: vec2<f32>) -> vec4<f32> {
    let direction = uLinearGradientRT.end.xy - uLinearGradientRT.start.xy;
    let lengthSquared = dot(direction, direction);
    let rawT = select(
        dot(localPosition - uLinearGradientRT.start.xy, direction) / lengthSquared,
        -1.0e30,
        lengthSquared < 1.0e-12,
    );
    let t = clamp(rawT, 0.0, 1.0);
    return uLinearGradientRT.startColor * (1.0 - t) + uLinearGradientRT.endColor * t;
}
"""

const val LinearGradientRTEntryPoint: String = "linear_gradient_rt_source"

val LinearGradientRTSourceHash: String = wgslSourceContentHash(LinearGradientRTWgsl)
internal val LinearGradientRTModuleHash: String =
    wgslModuleContentHash(LinearGradientRTWgsl, LinearGradientRTEntryPoint)
internal val LinearGradientRTReflectionHash: String = WgslReflectionReport(
    sourceId = LinearGradientRTSourceHash,
    bindings = listOf(
        WgslBindingReflection(
            group = 1,
            binding = 0,
            name = "uLinearGradientRT",
            resourceKind = "uniformBuffer",
            access = "read",
            minBindingSize = LinearGradientRTUniformBlockSizeBytes,
        ),
    ),
    layouts = listOf(
        WgslLayoutReflection(
            structName = "LinearGradientRTUniform",
            addressSpace = "uniform",
            size = LinearGradientRTUniformBlockSizeBytes,
            alignment = 16,
            members = listOf(
                WgslLayoutMemberReflection("start", "vec4<f32>", 0, 16, 16),
                WgslLayoutMemberReflection("end", "vec4<f32>", 16, 16, 16),
                WgslLayoutMemberReflection("startColor", "vec4<f32>", 32, 16, 16),
                WgslLayoutMemberReflection("endColor", "vec4<f32>", 48, 16, 16),
            ),
        ),
    ),
).reflectionFactsHash()
