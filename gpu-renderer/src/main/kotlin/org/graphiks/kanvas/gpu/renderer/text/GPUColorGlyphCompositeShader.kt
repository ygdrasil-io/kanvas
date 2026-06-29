package org.graphiks.kanvas.gpu.renderer.text

import org.graphiks.kanvas.gpu.renderer.color.GPUColorWgslReflection
import org.graphiks.kanvas.gpu.renderer.color.GPUColorWgslValidation
import org.graphiks.kanvas.gpu.renderer.color.validateColorWgsl

/**
 * Fixed layer budget iterated by the single-pass COLRv0 composite shader.
 * Matches `GPUColorGlyphRoutePlanner.MAX_COLOR_LAYERS`; plans exceeding it are
 * refused before reaching the shader.
 */
const val COLOR_GLYPH_COMPOSITE_MAX_LAYERS: Int = 16

/**
 * A wgsl4k-validated COLRv0 composite shader plan: the generated WGSL source and
 * its reflection. Renderer-neutral — no GPU handle, no pixels.
 */
data class GPUColorGlyphCompositePlan(
    val wgslSource: String,
    val wgslReflection: GPUColorWgslReflection?,
)

/** Outcome of building the COLRv0 composite shader plan. */
sealed interface GPUColorGlyphCompositeShaderResult {
    /** Shader generated and validated through wgsl4k. */
    data class Ready(val plan: GPUColorGlyphCompositePlan) : GPUColorGlyphCompositeShaderResult

    /** wgsl4k rejected the generated shader. */
    data class Rejected(val reason: String, val message: String) : GPUColorGlyphCompositeShaderResult
}

/**
 * Generates the single-pass COLRv0 composite WGSL. For each layer (bottom -> top)
 * it samples the A8 coverage atlas at the layer's atlas rect and blends the
 * layer's resolved solid color over the accumulator (src-over). Per-layer atlas
 * rects and colors are supplied via the uniform block; the backend (Plan 3c)
 * packs them before the draw. Layers beyond [maxLayers] are not iterated (the
 * route planner refuses such plans).
 *
 * Backend packing contract (Plan 3c): `layerColors[i]` must be premultiplied
 * RGBA (the fragment blends premultiplied src-over: `accum = src + accum*(1-src.a)`);
 * `layerAtlasRects[i]` is the layer glyph's coverage region as normalized
 * `(originU, originV, sizeU, sizeV)` and is sampled as `origin + quad_uv * size`.
 *
 * Uses the WGSL `loop {}` construct (not `for`) because the current wgsl4k
 * parser rejects `for` loops; `loop {}` is standard WGSL and validates.
 */
fun colorGlyphCompositeWgsl(maxLayers: Int = COLOR_GLYPH_COMPOSITE_MAX_LAYERS): String = """
struct Uniforms {
    targetWidth: f32,
    targetHeight: f32,
    layerCount: u32,
    reserved: u32,
    layerColors: array<vec4f, $maxLayers>,
    layerAtlasRects: array<vec4f, $maxLayers>,
};

@group(0) @binding(0) var<uniform> uniforms: Uniforms;

struct VertexInput {
    @location(0) position: vec2<f32>,
    @location(1) quad_uv: vec2<f32>,
};

struct VertexOutput {
    @builtin(position) position: vec4<f32>,
    @location(0) quad_uv: vec2<f32>,
};

@vertex
fn vs_main(in: VertexInput) -> VertexOutput {
    var out: VertexOutput;
    out.position = vec4<f32>(
        in.position.x / uniforms.targetWidth * 2.0 - 1.0,
        1.0 - in.position.y / uniforms.targetHeight * 2.0,
        0.0,
        1.0
    );
    out.quad_uv = in.quad_uv;
    return out;
}

@group(1) @binding(1) var coverage_atlas: texture_2d<f32>;
@group(1) @binding(2) var coverage_sampler: sampler;

@fragment
fn fs_main(in: VertexOutput) -> @location(0) vec4<f32> {
    var accum: vec4f = vec4f(0.0, 0.0, 0.0, 0.0);
    var colors = uniforms.layerColors;
    var rects = uniforms.layerAtlasRects;
    var i: u32 = 0u;
    loop {
        if (i >= uniforms.layerCount) {
            break;
        }
        if (i >= ${maxLayers}u) {
            break;
        }
        let rect = rects[i];
        let atlas_uv = vec2f(rect.x + in.quad_uv.x * rect.z, rect.y + in.quad_uv.y * rect.w);
        let coverage = textureSample(coverage_atlas, coverage_sampler, atlas_uv).r;
        let src = colors[i] * coverage;
        accum = src + accum * (1.0 - src.a);
        i = i + 1u;
    }
    return accum;
}
""".trimIndent()

/**
 * Generates and validates the COLRv0 composite shader through wgsl4k, mirroring
 * the color-management WGSL validation path in `GPUColorWgsl`.
 */
fun buildColorGlyphCompositeShader(
    maxLayers: Int = COLOR_GLYPH_COMPOSITE_MAX_LAYERS,
): GPUColorGlyphCompositeShaderResult {
    val wgsl = colorGlyphCompositeWgsl(maxLayers)
    return when (val validation = validateColorWgsl(sourceId = "text.colrv0.composite", wgslSource = wgsl)) {
        is GPUColorWgslValidation.Validated ->
            GPUColorGlyphCompositeShaderResult.Ready(
                GPUColorGlyphCompositePlan(wgslSource = wgsl, wgslReflection = validation.reflection),
            )
        is GPUColorWgslValidation.Rejected ->
            GPUColorGlyphCompositeShaderResult.Rejected(reason = validation.reason, message = validation.message)
    }
}
