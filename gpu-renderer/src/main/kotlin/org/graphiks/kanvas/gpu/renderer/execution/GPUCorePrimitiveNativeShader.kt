package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.color.GPUColorWgslReflection
import org.graphiks.kanvas.gpu.renderer.color.GPUColorWgslValidation
import org.graphiks.kanvas.gpu.renderer.color.validateColorWgsl

internal data class GPUCorePrimitiveNativeShaderPlan(
    val wgslSource: String,
    val wgslReflection: GPUColorWgslReflection?,
)

internal sealed interface GPUCorePrimitiveNativeShaderResult {
    data class Ready(val plan: GPUCorePrimitiveNativeShaderPlan) : GPUCorePrimitiveNativeShaderResult
    data class Rejected(val reason: String, val message: String) : GPUCorePrimitiveNativeShaderResult
}

internal fun buildCorePrimitiveNativeShader(): GPUCorePrimitiveNativeShaderResult =
    when (
        val validation = validateColorWgsl(
            sourceId = CORE_PRIMITIVE_NATIVE_SHADER_IDENTITY,
            wgslSource = CORE_PRIMITIVE_NATIVE_WGSL,
        )
    ) {
        is GPUColorWgslValidation.Validated -> GPUCorePrimitiveNativeShaderResult.Ready(
            GPUCorePrimitiveNativeShaderPlan(CORE_PRIMITIVE_NATIVE_WGSL, validation.reflection),
        )
        is GPUColorWgslValidation.Rejected -> GPUCorePrimitiveNativeShaderResult.Rejected(
            validation.reason,
            validation.message,
        )
    }

internal fun buildCorePrimitiveAnalyticClipNativeShader(): GPUCorePrimitiveNativeShaderResult =
    when (
        val validation = validateColorWgsl(
            sourceId = CORE_PRIMITIVE_ANALYTIC_CLIP_NATIVE_SHADER_IDENTITY,
            wgslSource = CORE_PRIMITIVE_ANALYTIC_CLIP_NATIVE_WGSL,
        )
    ) {
        is GPUColorWgslValidation.Validated -> GPUCorePrimitiveNativeShaderResult.Ready(
            GPUCorePrimitiveNativeShaderPlan(
                CORE_PRIMITIVE_ANALYTIC_CLIP_NATIVE_WGSL,
                validation.reflection,
            ),
        )
        is GPUColorWgslValidation.Rejected -> GPUCorePrimitiveNativeShaderResult.Rejected(
            validation.reason,
            validation.message,
        )
    }

internal fun buildCorePrimitiveAnalyticIntersection4NativeShader(): GPUCorePrimitiveNativeShaderResult =
    when (
        val validation = validateColorWgsl(
            sourceId = CORE_PRIMITIVE_ANALYTIC_INTERSECTION4_NATIVE_SHADER_IDENTITY,
            wgslSource = CORE_PRIMITIVE_ANALYTIC_INTERSECTION4_NATIVE_WGSL,
        )
    ) {
        is GPUColorWgslValidation.Validated -> GPUCorePrimitiveNativeShaderResult.Ready(
            GPUCorePrimitiveNativeShaderPlan(
                CORE_PRIMITIVE_ANALYTIC_INTERSECTION4_NATIVE_WGSL,
                validation.reflection,
            ),
        )
        is GPUColorWgslValidation.Rejected -> GPUCorePrimitiveNativeShaderResult.Rejected(
            validation.reason,
            validation.message,
        )
    }

internal const val CORE_PRIMITIVE_NATIVE_SHADER_IDENTITY = "core-primitive-device-geometry-wgsl-v2"
internal const val CORE_PRIMITIVE_NATIVE_BINDING_LAYOUT_IDENTITY =
    "vertex-fragment-dynamic-uniform32-v2"
internal const val CORE_PRIMITIVE_NATIVE_VERTEX_LAYOUT_IDENTITY = "float32x2-uint32-triangle-list-v1"
internal const val CORE_PRIMITIVE_NATIVE_VERTEX_ENTRY_POINT = "vs_main"
internal const val CORE_PRIMITIVE_NATIVE_COLOR_FRAGMENT_ENTRY_POINT = "fs_main"
internal const val CORE_PRIMITIVE_NATIVE_STENCIL_FRAGMENT_ENTRY_POINT = "fs_stencil"
internal const val CORE_PRIMITIVE_ANALYTIC_CLIP_NATIVE_SHADER_IDENTITY =
    "core-primitive-analytic-clip-device-geometry-wgsl-v1"
internal const val CORE_PRIMITIVE_ANALYTIC_CLIP_NATIVE_BINDING_LAYOUT_IDENTITY =
    "vertex-fragment-dynamic-uniform64-analytic-clip-v1"
internal const val CORE_PRIMITIVE_ANALYTIC_CLIP_NATIVE_VERTEX_ENTRY_POINT = "vs_main"
internal const val CORE_PRIMITIVE_ANALYTIC_CLIP_NATIVE_FRAGMENT_ENTRY_POINT = "fs_main"
internal const val CORE_PRIMITIVE_ANALYTIC_INTERSECTION4_NATIVE_SHADER_IDENTITY =
    "core-primitive-analytic-clip-intersection4-device-geometry-wgsl-v1"
internal const val CORE_PRIMITIVE_ANALYTIC_INTERSECTION4_NATIVE_BINDING_LAYOUT_IDENTITY =
    "vertex-fragment-dynamic-uniform160-analytic-clip-intersection4-v1"
internal const val CORE_PRIMITIVE_ANALYTIC_INTERSECTION4_NATIVE_VERTEX_ENTRY_POINT = "vs_main"
internal const val CORE_PRIMITIVE_ANALYTIC_INTERSECTION4_NATIVE_FRAGMENT_ENTRY_POINT = "fs_main"

internal val CORE_PRIMITIVE_NATIVE_WGSL = """
    struct CorePrimitiveBlock {
        target_size: vec2<f32>,
        padding: vec2<f32>,
        premul_rgba: vec4<f32>,
    }

    @group(0) @binding(0) var<uniform> core: CorePrimitiveBlock;

    @vertex
    fn vs_main(@location(0) device_position: vec2<f32>) -> @builtin(position) vec4<f32> {
        let ndc_x = device_position.x / core.target_size.x * 2.0 - 1.0;
        let ndc_y = 1.0 - device_position.y / core.target_size.y * 2.0;
        return vec4<f32>(ndc_x, ndc_y, 0.0, 1.0);
    }

    @fragment
    fn fs_main() -> @location(0) vec4<f32> {
        return core.premul_rgba;
    }

    @fragment
    fn fs_stencil() -> @location(0) vec4<f32> {
        return vec4<f32>(0.0, 0.0, 0.0, 0.0);
    }
""".trimIndent()

internal val CORE_PRIMITIVE_ANALYTIC_CLIP_NATIVE_WGSL = """
    struct CorePrimitiveAnalyticClipBlock {
        target_size: vec2<f32>,
        clip_type: u32,
        anti_alias: u32,
        premul_rgba: vec4<f32>,
        clip_bounds: vec4<f32>,
        clip_radii: vec4<f32>,
    }

    @group(0) @binding(0) var<uniform> analytic: CorePrimitiveAnalyticClipBlock;

    @vertex
    fn vs_main(@location(0) device_position: vec2<f32>) -> @builtin(position) vec4<f32> {
        let ndc_x = device_position.x / analytic.target_size.x * 2.0 - 1.0;
        let ndc_y = 1.0 - device_position.y / analytic.target_size.y * 2.0;
        return vec4<f32>(ndc_x, ndc_y, 0.0, 1.0);
    }

    fn sd_coverage(distance: f32) -> f32 {
        let hard = select(0.0, 1.0, distance <= 0.0);
        let aa = clamp(0.5 - distance, 0.0, 1.0);
        return select(hard, aa, analytic.anti_alias != 0u);
    }

    fn rect_signed_distance(position: vec2<f32>) -> f32 {
        let center = (analytic.clip_bounds.xy + analytic.clip_bounds.zw) * 0.5;
        let half_extent = (analytic.clip_bounds.zw - analytic.clip_bounds.xy) * 0.5;
        let q = abs(position - center) - half_extent;
        return length(max(q, vec2<f32>(0.0))) + min(max(q.x, q.y), 0.0);
    }

    fn rrect_signed_distance(position: vec2<f32>) -> f32 {
        let center = (analytic.clip_bounds.xy + analytic.clip_bounds.zw) * 0.5;
        let half_extent = (analytic.clip_bounds.zw - analytic.clip_bounds.xy) * 0.5;
        let radius = max(analytic.clip_radii.xy, vec2<f32>(0.0001));
        let outer_q = abs(position - center) - half_extent;
        let corner_q = outer_q + radius;
        let normalized_length = length(corner_q / radius);
        let safe_normalized_length = max(normalized_length, 0.000001);
        let implicit_value = normalized_length - 1.0;
        let implicit_gradient = corner_q / (radius * radius * safe_normalized_length);
        let safe_gradient_length = max(length(implicit_gradient), 0.000001);
        let corner_distance = implicit_value / safe_gradient_length;
        let strip_distance = max(outer_q.x, outer_q.y);
        let is_corner = corner_q.x > 0.0 && corner_q.y > 0.0;
        return select(strip_distance, corner_distance, is_corner);
    }

    fn rect_coverage(position: vec2<f32>) -> f32 {
        return sd_coverage(rect_signed_distance(position));
    }

    fn rrect_coverage(position: vec2<f32>) -> f32 {
        return sd_coverage(rrect_signed_distance(position));
    }

    @fragment
    fn fs_main(@builtin(position) fragment_position: vec4<f32>) -> @location(0) vec4<f32> {
        let position = fragment_position.xy;
        let coverage = select(rect_coverage(position), rrect_coverage(position), analytic.clip_type == 1u);
        return analytic.premul_rgba * coverage;
    }
""".trimIndent()

/**
 * Four explicit 32-byte slots keep every ABI member in the bound root struct reflected by Kanvas.
 * wgsl4k parses and lays out this source directly; no parser fallback or declared reflection is used.
 */
internal val CORE_PRIMITIVE_ANALYTIC_INTERSECTION4_NATIVE_WGSL = """
    struct CorePrimitiveAnalyticIntersection4Block {
        target_size: vec2<f32>,
        clip_count: u32,
        padding: u32,
        premul_rgba: vec4<f32>,
        clip0_bounds: vec4<f32>,
        clip0_radii: vec2<f32>,
        clip0_kind: u32,
        clip0_anti_alias: u32,
        clip1_bounds: vec4<f32>,
        clip1_radii: vec2<f32>,
        clip1_kind: u32,
        clip1_anti_alias: u32,
        clip2_bounds: vec4<f32>,
        clip2_radii: vec2<f32>,
        clip2_kind: u32,
        clip2_anti_alias: u32,
        clip3_bounds: vec4<f32>,
        clip3_radii: vec2<f32>,
        clip3_kind: u32,
        clip3_anti_alias: u32,
    }

    @group(0) @binding(0) var<uniform> analytic: CorePrimitiveAnalyticIntersection4Block;

    @vertex
    fn vs_main(@location(0) device_position: vec2<f32>) -> @builtin(position) vec4<f32> {
        let ndc_x = device_position.x / analytic.target_size.x * 2.0 - 1.0;
        let ndc_y = 1.0 - device_position.y / analytic.target_size.y * 2.0;
        return vec4<f32>(ndc_x, ndc_y, 0.0, 1.0);
    }

    fn sd_coverage(distance: f32, anti_alias: u32) -> f32 {
        let hard = select(0.0, 1.0, distance <= 0.0);
        let aa = clamp(0.5 - distance, 0.0, 1.0);
        return select(hard, aa, anti_alias != 0u);
    }

    fn rect_signed_distance(position: vec2<f32>, bounds: vec4<f32>) -> f32 {
        let center = (bounds.xy + bounds.zw) * 0.5;
        let half_extent = (bounds.zw - bounds.xy) * 0.5;
        let q = abs(position - center) - half_extent;
        return length(max(q, vec2<f32>(0.0))) + min(max(q.x, q.y), 0.0);
    }

    fn rrect_signed_distance(
        position: vec2<f32>,
        bounds: vec4<f32>,
        radii: vec2<f32>,
    ) -> f32 {
        let center = (bounds.xy + bounds.zw) * 0.5;
        let half_extent = (bounds.zw - bounds.xy) * 0.5;
        let radius = max(radii, vec2<f32>(0.0001));
        let outer_q = abs(position - center) - half_extent;
        let corner_q = outer_q + radius;
        let normalized_length = length(corner_q / radius);
        let safe_normalized_length = max(normalized_length, 0.000001);
        let implicit_value = normalized_length - 1.0;
        let implicit_gradient = corner_q / (radius * radius * safe_normalized_length);
        let safe_gradient_length = max(length(implicit_gradient), 0.000001);
        let corner_distance = implicit_value / safe_gradient_length;
        let strip_distance = max(outer_q.x, outer_q.y);
        let is_corner = corner_q.x > 0.0 && corner_q.y > 0.0;
        return select(strip_distance, corner_distance, is_corner);
    }

    fn clip_coverage(
        position: vec2<f32>,
        bounds: vec4<f32>,
        radii: vec2<f32>,
        kind: u32,
        anti_alias: u32,
    ) -> f32 {
        let rect_distance = rect_signed_distance(position, bounds);
        let rrect_distance = rrect_signed_distance(position, bounds, radii);
        let distance = select(rect_distance, rrect_distance, kind == 1u);
        return sd_coverage(distance, anti_alias);
    }

    @fragment
    fn fs_main(@builtin(position) fragment_position: vec4<f32>) -> @location(0) vec4<f32> {
        let position = fragment_position.xy;
        let coverage0 = select(1.0, clip_coverage(position, analytic.clip0_bounds, analytic.clip0_radii, analytic.clip0_kind, analytic.clip0_anti_alias), analytic.clip_count > 0u);
        let coverage1 = select(1.0, clip_coverage(position, analytic.clip1_bounds, analytic.clip1_radii, analytic.clip1_kind, analytic.clip1_anti_alias), analytic.clip_count > 1u);
        let coverage2 = select(1.0, clip_coverage(position, analytic.clip2_bounds, analytic.clip2_radii, analytic.clip2_kind, analytic.clip2_anti_alias), analytic.clip_count > 2u);
        let coverage3 = select(1.0, clip_coverage(position, analytic.clip3_bounds, analytic.clip3_radii, analytic.clip3_kind, analytic.clip3_anti_alias), analytic.clip_count > 3u);
        let coverage = coverage0 * coverage1 * coverage2 * coverage3;
        return analytic.premul_rgba * coverage;
    }
""".trimIndent()
