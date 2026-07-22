package org.graphiks.kanvas.gpu.renderer.wgsl

/**
 * Fixed layer budget iterated by the single-pass COLRv0 composite shader.
 * Matches `GPUColorGlyphRoutePlanner.MAX_COLOR_LAYERS`; plans exceeding it are
 * refused before reaching the shader.
 */
const val COLOR_GLYPH_COMPOSITE_MAX_LAYERS: Int = 16

/**
 * Generates the single-pass COLRv0 composite WGSL. For each layer (bottom -> top)
 * it samples the A8 coverage atlas at the layer's atlas rect and blends the
 * layer's resolved solid color over the accumulator (src-over). Per-layer atlas
 * atlas rects, device-space rectangles, and colors are supplied via the uniform
 * block; the GPU runtime packs them before the draw. Coverage is zero outside
 * each layer's device rectangle. Layers beyond [maxLayers] are not iterated
 * (the route planner refuses such plans).
 *
 * GPU packing contract: `layerColors[i]` must be premultiplied RGBA (the
 * fragment blends premultiplied src-over: `accum = src + accum*(1-src.a)`);
 * `layerAtlasRects[i]` is the layer glyph's coverage region as normalized
 * `(originU, originV, sizeU, sizeV)`. `layerDeviceRects[i]` is the device-space
 * `(left, top, width, height)` used to derive the local atlas UV for that layer.
 */
fun colorGlyphCompositeWgsl(maxLayers: Int = COLOR_GLYPH_COMPOSITE_MAX_LAYERS): String = """
struct Uniforms {
    targetWidth: f32,
    targetHeight: f32,
    layerCount: u32,
    reserved: u32,
    layerColors: array<vec4f, $maxLayers>,
    layerAtlasRects: array<vec4f, $maxLayers>,
    layerDeviceRects: array<vec4f, $maxLayers>,
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

@group(0) @binding(1) var coverage_atlas: texture_2d<f32>;
@group(0) @binding(2) var coverage_sampler: sampler;

@fragment
fn fs_main(in: VertexOutput) -> @location(0) vec4<f32> {
    var accum: vec4f = vec4f(0.0, 0.0, 0.0, 0.0);
    var colors = uniforms.layerColors;
    var atlas_rects = uniforms.layerAtlasRects;
    var device_rects = uniforms.layerDeviceRects;
    let device_xy = in.position.xy;
    for (var i: u32 = 0u; i < ${maxLayers}u; i = i + 1u) {
        if (i >= uniforms.layerCount) {
            break;
        }
        let atlas_rect = atlas_rects[i];
        let device_rect = device_rects[i];
        let inside = device_xy.x >= device_rect.x &&
            device_xy.x < device_rect.x + device_rect.z &&
            device_xy.y >= device_rect.y &&
            device_xy.y < device_rect.y + device_rect.w;
        if (inside) {
            let local_uv = (device_xy - device_rect.xy) / device_rect.zw;
            let atlas_uv = atlas_rect.xy + local_uv * atlas_rect.zw;
            let coverage = textureSample(coverage_atlas, coverage_sampler, atlas_uv).r;
            let src = colors[i] * coverage;
            accum = src + accum * (1.0 - src.a);
        }
    }
    return accum;
}
""".trimIndent()
