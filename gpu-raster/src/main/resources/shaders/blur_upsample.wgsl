// MaskFilter -- 2x bilinear upsample. Inverse of `blur_downsample.wgsl`,
// used as the second half of the multi-stage cascade : after the
// inner blur runs at low resolution, this pass upsamples each level
// back toward the original mask size.
//
// Algorithm : sample the source at the corresponding mid-pixel UV.
// For each dst pixel, the matching source coordinate is
// (dst_px + 0.5) / dstSize (the dst-pixel-centre in normalised UV).
// With a Linear sampler + ClampToEdge, this reads the bilinear-
// interpolated source value at half-resolution -- exactly the
// reconstruction filter SkGpuBlurUtils uses on its mip-chain
// upsample. ClampToEdge preserves the kernel's edge mass.
//
// One sample, one tap. The dst texture is allocated at 2x the
// source dimensions (the chain steps `down{n}` -> `down{n-1}` ->
// ... -> `up_full`).
//
// Uniform layout :
//   - dstSize.xy = destination texture width / height (pixels).
//   - dstSize.zw = source texture width / height (pixels).
// The dst pixel's source UV is `(pos.xy + 0.5) / dstSize.xy` then
// the sampler picks 4 source texels at `(pos / 2)` rounded to the
// nearest half-texel.
//
// ASCII strict -- WGSL parser truncates on non-ASCII in wgpu4k 0.2.0.

struct Uniforms {
    // dstSize.xy = destination width / height (pixels).
    // dstSize.zw = source width / height (pixels) -- unused by the
    //              upsample shader (the bilinear sampler resolves
    //              the source coord from the UV) but kept for the
    //              layout parity with `blur_downsample.wgsl`.
    dstSize: vec4f,
};

@group(0) @binding(0) var<uniform> uniforms: Uniforms;
@group(0) @binding(1) var source_texture: texture_2d<f32>;
@group(0) @binding(2) var source_sampler: sampler;

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

@fragment
fn fs_main(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let dst_w = uniforms.dstSize.x;
    let dst_h = uniforms.dstSize.y;

    // Guard rail.
    if (pos.x < 0.0 || pos.x >= dst_w ||
        pos.y < 0.0 || pos.y >= dst_h) {
        return vec4f(0.0);
    }

    // dst pixel-centre in normalised UV. The Linear sampler maps
    // this to the bilinear-interpolated source value at the half-
    // resolution grid.
    let dst_uv = vec2f(
        (floor(pos.x) + 0.5) / dst_w,
        (floor(pos.y) + 0.5) / dst_h,
    );
    return textureSampleLevel(source_texture, source_sampler, dst_uv, 0.0);
}
