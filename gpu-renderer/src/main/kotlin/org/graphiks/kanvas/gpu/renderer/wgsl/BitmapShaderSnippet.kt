package org.graphiks.kanvas.gpu.renderer.wgsl

const val BitmapShaderWgsl: String = """
@group(1) @binding(1) var texture_sampled: texture_2d<f32>;
@group(1) @binding(2) var texture_sampler: sampler;

fn bitmap_uv_clamp(uv: vec2<f32>) -> vec2<f32> {
    return clamp(uv, vec2f(0.0, 0.0), vec2f(1.0, 1.0));
}

fn bitmap_uv_repeat(uv: vec2<f32>) -> vec2<f32> {
    return fract(uv);
}

fn bitmap_uv_mirror(uv: vec2<f32>) -> vec2<f32> {
    let half = uv * 0.5;
    let t = half - floor(half);
    return 1.0 - 2.0 * abs(t - 0.5);
}

fn bitmap_uv_decal(uv: vec2<f32>) -> vec2<f32> {
    return uv;
}

fn bitmap_tile_clamp(v: f32) -> f32 {
    return clamp(v, 0.0, 1.0);
}

fn bitmap_tile_repeat(v: f32) -> f32 {
    return fract(v);
}

fn bitmap_tile_mirror(v: f32) -> f32 {
    let half = v * 0.5;
    let t = half - floor(half);
    return 1.0 - 2.0 * abs(t - 0.5);
}

fn bitmap_tile_decal(v: f32) -> f32 {
    return v;
}

fn bitmap_decal_in_bounds(v: f32) -> bool {
    return v >= 0.0 && v <= 1.0;
}

fn bitmap_transparent() -> vec4<f32> {
    return vec4f(0.0, 0.0, 0.0, 0.0);
}

fn bitmap_shader_clamp(uv: vec2<f32>) -> vec4<f32> {
    let clamped = bitmap_uv_clamp(uv);
    return textureSample(texture_sampled, texture_sampler, clamped);
}

fn bitmap_shader_repeat(uv: vec2<f32>) -> vec4<f32> {
    let repeated = bitmap_uv_repeat(uv);
    return textureSample(texture_sampled, texture_sampler, repeated);
}

fn bitmap_shader_mirror(uv: vec2<f32>) -> vec4<f32> {
    let mirrored = bitmap_uv_mirror(uv);
    return textureSample(texture_sampled, texture_sampler, mirrored);
}

fn bitmap_shader_decal(uv: vec2<f32>) -> vec4<f32> {
    let inside = all(uv >= vec2f(0.0, 0.0)) && all(uv <= vec2f(1.0, 1.0));
    if (inside) {
        return textureSample(texture_sampled, texture_sampler, uv);
    }
    return bitmap_transparent();
}

fn bitmap_shader_clamp_repeat(uv: vec2<f32>) -> vec4<f32> {
    return textureSample(texture_sampled, texture_sampler, vec2f(bitmap_tile_clamp(uv.x), bitmap_tile_repeat(uv.y)));
}

fn bitmap_shader_clamp_mirror(uv: vec2<f32>) -> vec4<f32> {
    return textureSample(texture_sampled, texture_sampler, vec2f(bitmap_tile_clamp(uv.x), bitmap_tile_mirror(uv.y)));
}

fn bitmap_shader_clamp_decal(uv: vec2<f32>) -> vec4<f32> {
    if (!bitmap_decal_in_bounds(uv.y)) {
        return bitmap_transparent();
    }
    return textureSample(texture_sampled, texture_sampler, vec2f(bitmap_tile_clamp(uv.x), bitmap_tile_decal(uv.y)));
}

fn bitmap_shader_repeat_clamp(uv: vec2<f32>) -> vec4<f32> {
    return textureSample(texture_sampled, texture_sampler, vec2f(bitmap_tile_repeat(uv.x), bitmap_tile_clamp(uv.y)));
}

fn bitmap_shader_repeat_mirror(uv: vec2<f32>) -> vec4<f32> {
    return textureSample(texture_sampled, texture_sampler, vec2f(bitmap_tile_repeat(uv.x), bitmap_tile_mirror(uv.y)));
}

fn bitmap_shader_repeat_decal(uv: vec2<f32>) -> vec4<f32> {
    if (!bitmap_decal_in_bounds(uv.y)) {
        return bitmap_transparent();
    }
    return textureSample(texture_sampled, texture_sampler, vec2f(bitmap_tile_repeat(uv.x), bitmap_tile_decal(uv.y)));
}

fn bitmap_shader_mirror_clamp(uv: vec2<f32>) -> vec4<f32> {
    return textureSample(texture_sampled, texture_sampler, vec2f(bitmap_tile_mirror(uv.x), bitmap_tile_clamp(uv.y)));
}

fn bitmap_shader_mirror_repeat(uv: vec2<f32>) -> vec4<f32> {
    return textureSample(texture_sampled, texture_sampler, vec2f(bitmap_tile_mirror(uv.x), bitmap_tile_repeat(uv.y)));
}

fn bitmap_shader_mirror_decal(uv: vec2<f32>) -> vec4<f32> {
    if (!bitmap_decal_in_bounds(uv.y)) {
        return bitmap_transparent();
    }
    return textureSample(texture_sampled, texture_sampler, vec2f(bitmap_tile_mirror(uv.x), bitmap_tile_decal(uv.y)));
}

fn bitmap_shader_decal_clamp(uv: vec2<f32>) -> vec4<f32> {
    if (!bitmap_decal_in_bounds(uv.x)) {
        return bitmap_transparent();
    }
    return textureSample(texture_sampled, texture_sampler, vec2f(bitmap_tile_decal(uv.x), bitmap_tile_clamp(uv.y)));
}

fn bitmap_shader_decal_repeat(uv: vec2<f32>) -> vec4<f32> {
    if (!bitmap_decal_in_bounds(uv.x)) {
        return bitmap_transparent();
    }
    return textureSample(texture_sampled, texture_sampler, vec2f(bitmap_tile_decal(uv.x), bitmap_tile_repeat(uv.y)));
}

fn bitmap_shader_decal_mirror(uv: vec2<f32>) -> vec4<f32> {
    if (!bitmap_decal_in_bounds(uv.x)) {
        return bitmap_transparent();
    }
    return textureSample(texture_sampled, texture_sampler, vec2f(bitmap_tile_decal(uv.x), bitmap_tile_mirror(uv.y)));
}

fn bitmap_shader_source(uv: vec2<f32>) -> vec4<f32> {
    return bitmap_shader_clamp(uv);
}
"""

const val BitmapShaderSnippetSourceHash: String = "fragment:bitmap_shader:v2"
const val BitmapShaderSourceEntryPoint: String = "bitmap_shader_source"
const val BitmapShaderClampEntryPoint: String = "bitmap_shader_clamp"
const val BitmapShaderRepeatEntryPoint: String = "bitmap_shader_repeat"
const val BitmapShaderMirrorEntryPoint: String = "bitmap_shader_mirror"
const val BitmapShaderDecalEntryPoint: String = "bitmap_shader_decal"
const val BitmapShaderClampRepeatEntryPoint: String = "bitmap_shader_clamp_repeat"
const val BitmapShaderClampMirrorEntryPoint: String = "bitmap_shader_clamp_mirror"
const val BitmapShaderClampDecalEntryPoint: String = "bitmap_shader_clamp_decal"
const val BitmapShaderRepeatClampEntryPoint: String = "bitmap_shader_repeat_clamp"
const val BitmapShaderRepeatMirrorEntryPoint: String = "bitmap_shader_repeat_mirror"
const val BitmapShaderRepeatDecalEntryPoint: String = "bitmap_shader_repeat_decal"
const val BitmapShaderMirrorClampEntryPoint: String = "bitmap_shader_mirror_clamp"
const val BitmapShaderMirrorRepeatEntryPoint: String = "bitmap_shader_mirror_repeat"
const val BitmapShaderMirrorDecalEntryPoint: String = "bitmap_shader_mirror_decal"
const val BitmapShaderDecalClampEntryPoint: String = "bitmap_shader_decal_clamp"
const val BitmapShaderDecalRepeatEntryPoint: String = "bitmap_shader_decal_repeat"
const val BitmapShaderDecalMirrorEntryPoint: String = "bitmap_shader_decal_mirror"

fun bitmapShaderWgslForEntryPoint(entryPoint: String): String {
    require(BitmapShaderWgsl.contains("fn $entryPoint(")) {
        "Unknown bitmap shader WGSL entry point: $entryPoint"
    }
    return BitmapShaderWgsl.replace(
        oldValue = "    return $BitmapShaderClampEntryPoint(uv);",
        newValue = "    return $entryPoint(uv);",
    )
}
