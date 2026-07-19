package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.color.GPUColorWgslReflection
import org.graphiks.kanvas.gpu.renderer.color.GPUColorWgslValidation
import org.graphiks.kanvas.gpu.renderer.color.validateColorWgsl
import org.graphiks.kanvas.gpu.renderer.passes.CORE_PRIMITIVE_ANALYTIC_SHAPE_UNIFORM_BYTES

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

internal const val CORE_PRIMITIVE_ANALYTIC_SHAPE_REFLECTION_INVALID_REASON =
    "wgsl_analytic_shape_reflection_invalid"

internal fun buildCorePrimitiveAnalyticShapeNativeShader(
    validator: (sourceId: String, wgslSource: String) -> GPUColorWgslValidation = ::validateColorWgsl,
): GPUCorePrimitiveNativeShaderResult =
    when (
        val validation = validator(
            CORE_PRIMITIVE_ANALYTIC_SHAPE_NATIVE_SHADER_IDENTITY,
            CORE_PRIMITIVE_ANALYTIC_SHAPE_NATIVE_WGSL,
        )
    ) {
        is GPUColorWgslValidation.Validated -> {
            val invalidMessage = analyticShapeReflectionInvalidMessage(validation.reflection)
            if (invalidMessage == null) {
                GPUCorePrimitiveNativeShaderResult.Ready(
                    GPUCorePrimitiveNativeShaderPlan(
                        CORE_PRIMITIVE_ANALYTIC_SHAPE_NATIVE_WGSL,
                        requireNotNull(validation.reflection),
                    ),
                )
            } else {
                GPUCorePrimitiveNativeShaderResult.Rejected(
                    CORE_PRIMITIVE_ANALYTIC_SHAPE_REFLECTION_INVALID_REASON,
                    invalidMessage,
                )
            }
        }
        is GPUColorWgslValidation.Rejected -> GPUCorePrimitiveNativeShaderResult.Rejected(
            validation.reason,
            validation.message,
        )
    }

private fun analyticShapeReflectionInvalidMessage(reflection: GPUColorWgslReflection?): String? {
    if (reflection == null) return "Analytic-shape executable WGSL requires parser reflection."
    if (!reflection.validated) return "Analytic-shape executable WGSL reflection is not parser validated."
    val report = reflection.report
    if (!report.validation.success) {
        return "Analytic-shape executable WGSL parser validation did not succeed."
    }
    val expectedEntryPoints = setOf(
        CORE_PRIMITIVE_ANALYTIC_SHAPE_NATIVE_VERTEX_ENTRY_POINT to "vertex",
        CORE_PRIMITIVE_ANALYTIC_SHAPE_NATIVE_FRAGMENT_ENTRY_POINT to "fragment",
    )
    val reflectedEntryPoints = report.entryPoints.map { it.name to it.stage }
    if (reflectedEntryPoints.size != expectedEntryPoints.size ||
        reflectedEntryPoints.toSet() != expectedEntryPoints
    ) {
        return "Analytic-shape executable WGSL must expose exactly its vertex and fragment entry points."
    }
    val binding = report.bindings.singleOrNull()
    if (binding == null || binding.group != 0 || binding.binding != 0 ||
        binding.resourceKind != "uniformBuffer" ||
        binding.minBindingSize != CORE_PRIMITIVE_ANALYTIC_SHAPE_UNIFORM_BYTES
    ) {
        return "Analytic-shape executable WGSL must expose exactly one uniform binding at group 0 binding 0."
    }
    val layout = report.layouts.singleOrNull { reflected ->
        reflected.structName == "CorePrimitiveAnalyticShapeBlock" && reflected.addressSpace == "uniform"
    } ?: return "Analytic-shape executable WGSL must reflect its uniform block."
    if (layout.size != CORE_PRIMITIVE_ANALYTIC_SHAPE_UNIFORM_BYTES) {
        return "Analytic-shape executable WGSL uniform block must be exactly 80 bytes."
    }
    val expectedMembers = listOf(
        "target_size" to (0 to 8),
        "anti_alias" to (8 to 4),
        "padding0" to (12 to 4),
        "premul_rgba" to (16 to 16),
        "device_bounds" to (32 to 16),
        "radii0" to (48 to 16),
        "radii1" to (64 to 16),
    )
    if (layout.members.map { it.name to (it.offset to it.size) } != expectedMembers) {
        return "Analytic-shape executable WGSL uniform block members do not match the sealed ABI80 offsets."
    }
    return null
}

internal fun buildCorePrimitiveClipStencilProducerNativeShader(): GPUCorePrimitiveNativeShaderResult =
    when (
        val validation = validateColorWgsl(
            sourceId = CORE_PRIMITIVE_CLIP_STENCIL_PRODUCER_NATIVE_SHADER_IDENTITY,
            wgslSource = CORE_PRIMITIVE_CLIP_STENCIL_PRODUCER_NATIVE_WGSL,
        )
    ) {
        is GPUColorWgslValidation.Validated -> GPUCorePrimitiveNativeShaderResult.Ready(
            GPUCorePrimitiveNativeShaderPlan(
                CORE_PRIMITIVE_CLIP_STENCIL_PRODUCER_NATIVE_WGSL,
                validation.reflection,
            ),
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

internal fun buildCorePrimitiveCoverageMaskProducerNativeShader(): GPUCorePrimitiveNativeShaderResult =
    when (
        val validation = validateColorWgsl(
            sourceId = CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_NATIVE_SHADER_IDENTITY,
            wgslSource = CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_NATIVE_WGSL,
        )
    ) {
        is GPUColorWgslValidation.Validated -> GPUCorePrimitiveNativeShaderResult.Ready(
            GPUCorePrimitiveNativeShaderPlan(
                CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_NATIVE_WGSL,
                validation.reflection,
            ),
        )
        is GPUColorWgslValidation.Rejected -> GPUCorePrimitiveNativeShaderResult.Rejected(
            validation.reason,
            validation.message,
        )
    }

internal fun buildCorePrimitiveCoverageMaskConsumerNativeShader(): GPUCorePrimitiveNativeShaderResult =
    when (
        val validation = validateColorWgsl(
            sourceId = CORE_PRIMITIVE_COVERAGE_MASK_CONSUMER_NATIVE_SHADER_IDENTITY,
            wgslSource = CORE_PRIMITIVE_COVERAGE_MASK_CONSUMER_NATIVE_WGSL,
        )
    ) {
        is GPUColorWgslValidation.Validated -> GPUCorePrimitiveNativeShaderResult.Ready(
            GPUCorePrimitiveNativeShaderPlan(
                CORE_PRIMITIVE_COVERAGE_MASK_CONSUMER_NATIVE_WGSL,
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
internal const val CORE_PRIMITIVE_ANALYTIC_SHAPE_NATIVE_SHADER_IDENTITY =
    "core-primitive-analytic-shape-device-geometry-wgsl-v1"
internal const val CORE_PRIMITIVE_ANALYTIC_SHAPE_NATIVE_BINDING_LAYOUT_IDENTITY =
    "dynamic-uniform80-analytic-shape-v1"
internal const val CORE_PRIMITIVE_ANALYTIC_SHAPE_NATIVE_VERTEX_ENTRY_POINT = "vs_main"
internal const val CORE_PRIMITIVE_ANALYTIC_SHAPE_NATIVE_FRAGMENT_ENTRY_POINT = "fs_main"
internal const val CORE_PRIMITIVE_CLIP_STENCIL_PRODUCER_NATIVE_SHADER_IDENTITY =
    "core-primitive-clip-stencil-producer-ndc-wgsl-v1"
internal const val CORE_PRIMITIVE_CLIP_STENCIL_PRODUCER_NATIVE_BINDING_LAYOUT_IDENTITY =
    "no-bindings-v1"
internal const val CORE_PRIMITIVE_CLIP_STENCIL_PRODUCER_NATIVE_VERTEX_ENTRY_POINT = "vs_main"
internal const val CORE_PRIMITIVE_CLIP_STENCIL_PRODUCER_NATIVE_FRAGMENT_ENTRY_POINT = "fs_stencil"
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
internal const val CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_NATIVE_SHADER_IDENTITY =
    "core-primitive-coverage-mask-producer-wgsl-v1"
internal const val CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_NATIVE_BINDING_LAYOUT_IDENTITY =
    "vertex-fragment-dynamic-uniform64-coverage-mask-producer-v1"
internal const val CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_NATIVE_VERTEX_LAYOUT_IDENTITY =
    "builtin-vertex-index-fullscreen-triangle-v1"
internal const val CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_NATIVE_VERTEX_ENTRY_POINT = "vs_main"
internal const val CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_NATIVE_RECT_FRAGMENT_ENTRY_POINT = "fs_rect"
internal const val CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_NATIVE_RRECT_FRAGMENT_ENTRY_POINT = "fs_rrect"
internal const val CORE_PRIMITIVE_COVERAGE_MASK_CONSUMER_NATIVE_SHADER_IDENTITY =
    "core-primitive-coverage-mask-consumer-nearest-wgsl-v1"
internal const val CORE_PRIMITIVE_COVERAGE_MASK_CONSUMER_NATIVE_BINDING_LAYOUT_IDENTITY =
    "vertex-fragment-dynamic-uniform64-texture2d-coverage-mask-consumer-v1"
internal const val CORE_PRIMITIVE_COVERAGE_MASK_CONSUMER_NATIVE_VERTEX_ENTRY_POINT = "vs_main"
internal const val CORE_PRIMITIVE_COVERAGE_MASK_CONSUMER_NATIVE_FRAGMENT_ENTRY_POINT = "fs_main"

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

/** One parser-validated program handles zero-radius Rects and four-corner normalized RRects. */
internal val CORE_PRIMITIVE_ANALYTIC_SHAPE_NATIVE_WGSL = """
    struct CorePrimitiveAnalyticShapeBlock {
        target_size: vec2<f32>,
        anti_alias: u32,
        padding0: u32,
        premul_rgba: vec4<f32>,
        device_bounds: vec4<f32>,
        radii0: vec4<f32>,
        radii1: vec4<f32>,
    }

    @group(0) @binding(0) var<uniform> analytic: CorePrimitiveAnalyticShapeBlock;

    @vertex
    fn vs_main(@location(0) device_position: vec2<f32>) -> @builtin(position) vec4<f32> {
        let ndc_x = device_position.x / analytic.target_size.x * 2.0 - 1.0;
        let ndc_y = 1.0 - device_position.y / analytic.target_size.y * 2.0;
        return vec4<f32>(ndc_x, ndc_y, 0.0, 1.0);
    }

    fn corner_distance(
        current_distance: f32,
        corner_edge_distance: vec2<f32>,
        radii: vec2<f32>,
    ) -> f32 {
        var distance = current_distance;
        let uv = radii - corner_edge_distance;
        if (uv.x > 0.0 && uv.y > 0.0 && radii.x > 0.0 && radii.y > 0.0) {
            let normalized_uv = uv / (radii * radii);
            let normalized_length = length(normalized_uv);
            if (normalized_length > 0.0) {
                let ellipse_inside = 0.5 * (1.0 - dot(uv, normalized_uv)) / normalized_length;
                distance = min(distance, ellipse_inside);
            }
        }
        return distance;
    }

    fn analytic_shape_distance(position: vec2<f32>) -> f32 {
        let edge_distances = vec4<f32>(
            position.x - analytic.device_bounds.x,
            position.y - analytic.device_bounds.y,
            analytic.device_bounds.z - position.x,
            analytic.device_bounds.w - position.y,
        );
        var distance = min(min(edge_distances.x, edge_distances.y), min(edge_distances.z, edge_distances.w));
        distance = corner_distance(distance, edge_distances.xy, analytic.radii0.xy);
        distance = corner_distance(distance, edge_distances.zy, analytic.radii0.zw);
        distance = corner_distance(distance, edge_distances.zw, analytic.radii1.xy);
        distance = corner_distance(distance, edge_distances.xw, analytic.radii1.zw);
        return distance;
    }

    fn analytic_shape_coverage(position: vec2<f32>) -> f32 {
        let distance = analytic_shape_distance(position);
        let hard = select(0.0, 1.0, distance >= 0.0);
        let shape_size = max(analytic.device_bounds.zw - analytic.device_bounds.xy, vec2<f32>(0.0));
        let scale = clamp(min(shape_size.x, shape_size.y), 0.0, 1.0);
        let bias = 1.0 - 0.5 * scale;
        let aa = clamp(scale * (distance + bias), 0.0, 1.0);
        return select(hard, aa, analytic.anti_alias != 0u);
    }

    @fragment
    fn fs_main(@builtin(position) fragment_position: vec4<f32>) -> @location(0) vec4<f32> {
        let coverage = analytic_shape_coverage(fragment_position.xy);
        return analytic.premul_rgba * coverage;
    }
""".trimIndent()

/** The pure route seals device coordinates to NDC before this shader ever sees a vertex. */
internal val CORE_PRIMITIVE_CLIP_STENCIL_PRODUCER_NATIVE_WGSL = """
    @vertex
    fn vs_main(@location(0) ndc_position: vec2<f32>) -> @builtin(position) vec4<f32> {
        return vec4<f32>(ndc_position, 0.0, 1.0);
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

/**
 * One fullscreen producer program updates the bounded frame-local mask. The render-pipeline blend
 * state selects DstIn for intersection or DstOut for difference; combine behavior is not shader
 * control flow. The producer mask is single-sample and intentionally hard coverage only.
 */
internal val CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_NATIVE_WGSL = """
    struct CorePrimitiveCoverageMaskProducerBlock {
        mask_origin: vec2<f32>,
        mask_size: vec2<f32>,
        shape_bounds: vec4<f32>,
        shape_radii0: vec4<f32>,
        shape_radii1: vec4<f32>,
    }

    @group(0) @binding(0) var<uniform> producer: CorePrimitiveCoverageMaskProducerBlock;

    @vertex
    fn vs_main(@builtin(vertex_index) vertex_index: u32) -> @builtin(position) vec4<f32> {
        var positions = array<vec2<f32>, 3>(
            vec2<f32>(-1.0, -1.0),
            vec2<f32>(3.0, -1.0),
            vec2<f32>(-1.0, 3.0),
        );
        return vec4<f32>(positions[vertex_index], 0.0, 1.0);
    }

    fn rect_signed_distance(position: vec2<f32>) -> f32 {
        let center = (producer.shape_bounds.xy + producer.shape_bounds.zw) * 0.5;
        let half_extent = (producer.shape_bounds.zw - producer.shape_bounds.xy) * 0.5;
        let q = abs(position - center) - half_extent;
        return length(max(q, vec2<f32>(0.0))) + min(max(q.x, q.y), 0.0);
    }

    fn rrect_radius(local_position: vec2<f32>) -> vec2<f32> {
        let top_radius = select(producer.shape_radii0.xy, producer.shape_radii0.zw, local_position.x > 0.0);
        let bottom_radius = select(producer.shape_radii1.zw, producer.shape_radii1.xy, local_position.x > 0.0);
        return max(select(top_radius, bottom_radius, local_position.y > 0.0), vec2<f32>(0.0001));
    }

    fn rrect_signed_distance(position: vec2<f32>) -> f32 {
        let center = (producer.shape_bounds.xy + producer.shape_bounds.zw) * 0.5;
        let half_extent = (producer.shape_bounds.zw - producer.shape_bounds.xy) * 0.5;
        let local_position = position - center;
        let radius = rrect_radius(local_position);
        let outer_q = abs(local_position) - half_extent;
        let corner_q = outer_q + radius;
        let normalized_length = length(corner_q / radius);
        let safe_normalized_length = max(normalized_length, 0.000001);
        let implicit_value = normalized_length - 1.0;
        let implicit_gradient = corner_q / (radius * radius * safe_normalized_length);
        let safe_gradient_length = max(length(implicit_gradient), 0.000001);
        let corner_distance = implicit_value / safe_gradient_length;
        let strip_distance = max(outer_q.x, outer_q.y);
        return select(strip_distance, corner_distance, corner_q.x > 0.0 && corner_q.y > 0.0);
    }

    @fragment
    fn fs_rect(@builtin(position) fragment_position: vec4<f32>) -> @location(0) vec4<f32> {
        let device_position = fragment_position.xy + producer.mask_origin;
        let coverage = select(0.0, 1.0, rect_signed_distance(device_position) <= 0.0);
        return vec4<f32>(coverage, coverage, coverage, coverage);
    }

    @fragment
    fn fs_rrect(@builtin(position) fragment_position: vec4<f32>) -> @location(0) vec4<f32> {
        let device_position = fragment_position.xy + producer.mask_origin;
        let coverage = select(0.0, 1.0, rrect_signed_distance(device_position) <= 0.0);
        return vec4<f32>(coverage, coverage, coverage, coverage);
    }
""".trimIndent()

/**
 * A bounded mask has one logical device-space origin and size. Its texture is sampled with integer
 * coordinates, so nearest coverage is exact and no sampler object or normalized backing extent can
 * affect the result. Inversion is payload-only and deliberately absent from the pipeline identity.
 * The indexed `stored_sample` access also records a wgsl4k lowering gap: member access on a
 * `textureLoad` result currently raises `LoweringError`, although both forms are valid WGSL.
 */
internal val CORE_PRIMITIVE_COVERAGE_MASK_CONSUMER_NATIVE_WGSL = """
    struct CorePrimitiveCoverageMaskConsumerBlock {
        target_size: vec2<f32>,
        mask_origin: vec2<i32>,
        mask_size: vec2<u32>,
        padding0: vec2<u32>,
        premul_rgba: vec4<f32>,
        invert: u32,
        padding1: u32,
        padding2: u32,
        padding3: u32,
    }

    @group(0) @binding(0) var<uniform> consumer: CorePrimitiveCoverageMaskConsumerBlock;
    @group(0) @binding(1) var coverage_mask: texture_2d<f32>;

    @vertex
    fn vs_main(@location(0) device_position: vec2<f32>) -> @builtin(position) vec4<f32> {
        let ndc_x = device_position.x / consumer.target_size.x * 2.0 - 1.0;
        let ndc_y = 1.0 - device_position.y / consumer.target_size.y * 2.0;
        return vec4<f32>(ndc_x, ndc_y, 0.0, 1.0);
    }

    @fragment
    fn fs_main(@builtin(position) fragment_position: vec4<f32>) -> @location(0) vec4<f32> {
        let device_pixel = vec2<i32>(fragment_position.xy);
        let mask_extent = vec2<i32>(consumer.mask_size);
        let mask_end = consumer.mask_origin + mask_extent;
        let inside_bounds = device_pixel.x >= consumer.mask_origin.x &&
            device_pixel.y >= consumer.mask_origin.y &&
            device_pixel.x < mask_end.x && device_pixel.y < mask_end.y;
        var stored_coverage = 0.0;
        if (inside_bounds) {
            let mask_pixel = device_pixel - consumer.mask_origin;
            let backing_extent = vec2<i32>(textureDimensions(coverage_mask));
            if (mask_pixel.x >= 0 && mask_pixel.y >= 0 &&
                mask_pixel.x < backing_extent.x && mask_pixel.y < backing_extent.y) {
                let stored_sample = textureLoad(coverage_mask, mask_pixel, 0);
                stored_coverage = stored_sample[0];
            }
        }
        let coverage = select(stored_coverage, 1.0 - stored_coverage, consumer.invert != 0u);
        return consumer.premul_rgba * coverage;
    }
""".trimIndent()
