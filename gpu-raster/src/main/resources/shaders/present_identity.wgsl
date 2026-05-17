// G6.1 / G6.2 present pass (identity variant) -- straight pixel copy
// from intermediate to final, no colorspace transform. Used by callers
// that want the raw device output for unit testing (cross-tests via
// WebGpuSink pick `present_pass.wgsl` instead, which applies the
// sRGB -> Rec.2020 transform).
//
// **G6.2.** The intermediate is now `RGBA16Float` instead of
// `RGBA8Unorm`. Draw shaders still emit premul **sRGB-coded** values
// (the F16 storage just buys sub-byte precision), so a straight
// pixel-value copy still produces sRGB-coded bytes on the
// `RGBA8Unorm` readback target -- exactly the bytes
// `SkBitmapDevice` raster also produces.
//
// ASCII strict -- WGSL parser truncates on non-ASCII in wgpu4k 0.2.0.

@group(0) @binding(0) var intermediate_texture: texture_2d<f32>;

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

@fragment
fn fs_main(@builtin(position) frag: vec4f) -> @location(0) vec4f {
    let pix = vec2i(i32(frag.x), i32(frag.y));
    return textureLoad(intermediate_texture, pix, 0);
}
