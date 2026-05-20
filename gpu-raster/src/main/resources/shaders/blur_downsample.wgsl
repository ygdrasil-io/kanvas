// MaskFilter -- 2x bilinear box-filter downsample. Used as part of
// the multi-stage downsample-blur-upsample cascade that lifts the
// sigma cap on SkBlurMaskFilter beyond the kernel-clamp limit
// (sigma ~ 10.6, radius 32) of the single-stage path.
//
// Algorithm : each dst pixel maps to 4 source pixels in a 2x2 block.
// Bilinear sampling at the centre of that block (offset (+0.5, +0.5)
// in dst-pixel coords -> source pixel coord (2x + 1, 2y + 1) - 0.5
// = (2x + 0.5, 2y + 0.5)) reads the average of the 4 source taps,
// which is the box-filter downsample matching Skia's
// SkGpuBlurUtils::convert_2D_blur_kernel and Vello's
// `vello_shaders::blur_downsample`.
//
// The shader uses textureSampleLevel (LOD 0) with a Linear sampler
// to leverage the hardware-bilinear filter -- one tap, one ALU op.
// The sampler is ClampToEdge so taps near the source border read
// the edge texel (preserves the kernel mass at boundaries ; matches
// SkScan's "clamp" extension on the shape-mask buffer).
//
// Uniform layout :
//   - dstSize.xy = destination texture (srcW / 2, srcH / 2)
//   - dstSize.zw = source texture size (srcW, srcH)
// The dst pixel's UV is `(pos.xy / dstSize.xy)`, which the bilinear
// sampler maps to the source at half the dst coord plus 0.5 / srcSize.
// We sample at `(dst_px + 0.5) / dstSize` instead, scaled to source
// UV via the bilinear sampler -- this lands at source pixel coord
// (2*dst_px + 1, 2*dst_px + 1) in pixel-centre terms, picking the
// average of the 4 adjacent source texels.
//
// ASCII strict -- WGSL parser truncates on non-ASCII in wgpu4k 0.2.0.

struct Uniforms {
    // dstSize.xy = destination width / height (pixels).
    // dstSize.zw = source width / height (pixels).
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
    let src_w = uniforms.dstSize.z;
    let src_h = uniforms.dstSize.w;

    // Guard rail -- if a fragment slips past the scissor, drop it.
    if (pos.x < 0.0 || pos.x >= dst_w ||
        pos.y < 0.0 || pos.y >= dst_h) {
        return vec4f(0.0);
    }

    // Bilinear sample at the centre of the 2x2 source block. The
    // dst pixel at floor(pos.xy) maps to source pixels (2x, 2x+1) x
    // (2y, 2y+1) ; sampling at pixel-centre (2x+1, 2y+1) - 0.5 =
    // (2x+0.5, 2y+0.5) in source pixels yields the 4-tap average.
    let src_uv = vec2f(
        (floor(pos.x) * 2.0 + 1.0) / src_w,
        (floor(pos.y) * 2.0 + 1.0) / src_h,
    );
    return textureSampleLevel(source_texture, source_sampler, src_uv, 0.0);
}
