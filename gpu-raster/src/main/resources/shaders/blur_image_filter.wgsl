// ImageFilter -- separable Gaussian blur shader for SkImageFilters.Blur
// applied on a saveLayer composite. Fork of `blur_gaussian.wgsl` (the
// MaskFilter variant) -- same separable Gaussian convolution math, but
// the V pass writes a pure blurred premul RGBA into a scratch texture
// instead of folding a paint colour. The final composite onto the
// parent uses the existing `layer_composite.wgsl` pipeline so paint
// alpha + colour filter follow the same code path as the no-filter
// saveLayer case.
//
// Two entry points :
//   - fs_horizontal : sample the source texture along X with Gaussian
//     weights, write to a scratch RGBA target. Out-of-source samples
//     follow the per-draw [tileMode] (SkTileMode ordinal) :
//       0 = kClamp  : clamp the coord to the source extent, reading the
//                     edge pixel.
//       1 = kRepeat : wrap the coord modulo the source extent (positive
//                     mod, so negative indices wrap to the high side).
//       2 = kMirror : reflect the coord every `srcExtent` taps so the
//                     sequence reads 0..N-1, N-1..0, 0..N-1, ...
//       3 = kDecal  : return zero, transparent border.
//   - fs_vertical : sample the H-pass scratch along Y with Gaussian
//     weights, write the blurred RGBA into a V-pass scratch. Same
//     tile-mode handling. The output is consumed by a subsequent
//     `layer_composite.wgsl` pass which applies paintColor +
//     colorFilter + the per-draw blend mode.
//
// Both passes use `textureLoad` (no sampler) with integer source-pixel
// coordinates -- the H scratch and V scratch share the same dimensions
// as the source layer texture (1:1 alignment, no padding). The
// composite step that follows reads the V scratch via `textureLoad`
// too, so the whole chain stays sampler-free and format-agnostic.
//
// Kernel weights are pre-computed CPU-side (matches the CPU raster's
// `gaussianKernel1D` -- shared with the MaskFilter blur) and shipped
// as a symmetric half-kernel : weight at offset 0 is the centre tap ;
// weight at offset k (1 <= k <= radius) is shared between -k and +k.
// The kernel sums to 1.0 after the 2x reflection of the off-centre
// half (or after renormalisation when the radius is clamped to the
// uniform's MAX_BLUR_RADIUS = 32).
//
// ASCII strict -- WGSL parser truncates on non-ASCII in wgpu4k 0.2.0.

struct Uniforms {
    // dstOriginSize :
    //   .xy = unused (kept zero by the host for layout parity with the
    //          MaskFilter blur uniform).
    //   .zw = (srcW, srcH) of the source texture (and of the scratch ;
    //          they share the size).
    dstOriginSize: vec4f,    // offset   0
    // axisTileRadius :
    //   .x = 1.0 when sampling along X (H pass), 0.0 else.
    //   .y = 1.0 when sampling along Y (V pass), 0.0 else.
    //   .z = SkTileMode ordinal :
    //          0 = kClamp  -> clamp coord to source extent (edge texel).
    //          1 = kRepeat -> positive-mod wrap (every sample in-bound).
    //          2 = kMirror -> reflect every srcExtent taps (every sample
    //                         in-bound).
    //          3 = kDecal  -> zero border. Also the fallback for any
    //                         unrecognised ordinal.
    //   .w = radius (number of off-centre taps per side, 0..32).
    axisTileRadius: vec4f,   // offset  16
    // weights[0..8] : symmetric half-kernel packed 4 floats per
    // vec4f. weight at off-centre offset k lives at flat index k :
    //   k = 0 -> weights[0].x  (centre tap)
    //   k = 1 -> weights[0].y
    //   ...
    //   k = 32 -> weights[8].x
    // Slots past 4 * 9 = 36 floats (indices 33..35) are unused but
    // must be zeroed by the host so the uniform bytes are deterministic.
    weights:        array<vec4f, 9>,  // offset 32
};

@group(0) @binding(0) var<uniform> uniforms: Uniforms;
@group(0) @binding(1) var source_texture: texture_2d<f32>;

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

fn weight_at(k: i32) -> f32 {
    let vec_idx = k / 4;
    let comp_idx = k % 4;
    let v = uniforms.weights[vec_idx];
    if (comp_idx == 0) { return v.x; }
    if (comp_idx == 1) { return v.y; }
    if (comp_idx == 2) { return v.z; }
    return v.w;
}

// Positive modulo : WGSL's `%` mirrors C99 (sign of dividend) for signed
// ints, so `(-1) % w` returns -1. The CPU raster's
// `SkBlurImageFilter.positiveModInternal` (kanvas-skia
// SkImageFilters.kt) wraps the result back into [0, m). Mirror it here
// so kRepeat lands on the same pixel grid as the CPU reference.
fn positive_mod(n: i32, m: i32) -> i32 {
    let r = n % m;
    if (r < 0) { return r + m; }
    return r;
}

// Mirror modulo : reflect every `m` taps so the sequence reads
// 0, 1, ..., m-1, m-1, m-2, ..., 0, 0, 1, ... Mirrors the CPU
// raster's `mirrorModInternal` in SkBlurImageFilter so kMirror lands on
// the same pixel grid as the CPU reference.
fn mirror_mod(n: i32, m: i32) -> i32 {
    if (m <= 0) { return 0; }
    let two_m = 2 * m;
    var r = n % two_m;
    if (r < 0) { r = r + two_m; }
    if (r < m) { return r; }
    return two_m - 1 - r;
}

// Sample the source texture at (px, py), honouring the per-draw
// tile mode :
//   - kClamp  (ordinal 0) : clamp (px, py) to the source extent and
//                           read the edge texel (no sampler).
//   - kRepeat (ordinal 1) : positive-mod wrap on each axis ; every
//                           sample lands in-bound, so the kernel mass
//                           stays unitary by construction.
//   - kMirror (ordinal 2) : reflect every `srcExtent` taps on each
//                           axis ; same unitary-mass property.
//   - kDecal  (else)      : return vec4f(0) for any out-of-source coord.
fn tile_load(px: i32, py: i32, src_w: i32, src_h: i32, tile_mode: i32) -> vec4f {
    if (tile_mode == 0) {
        let cx = clamp(px, 0, src_w - 1);
        let cy = clamp(py, 0, src_h - 1);
        return textureLoad(source_texture, vec2i(cx, cy), 0);
    }
    if (tile_mode == 1) {
        let cx = positive_mod(px, src_w);
        let cy = positive_mod(py, src_h);
        return textureLoad(source_texture, vec2i(cx, cy), 0);
    }
    if (tile_mode == 2) {
        let cx = mirror_mod(px, src_w);
        let cy = mirror_mod(py, src_h);
        return textureLoad(source_texture, vec2i(cx, cy), 0);
    }
    if (px < 0 || px >= src_w || py < 0 || py >= src_h) {
        return vec4f(0.0);
    }
    return textureLoad(source_texture, vec2i(px, py), 0);
}

// Horizontal pass : sample along X. The fragment's dst pixel maps
// 1:1 to the source pixel grid (scratch H is the same size as the
// source layer texture).
@fragment
fn fs_horizontal(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let dst_px = vec2i(i32(floor(pos.x)), i32(floor(pos.y)));
    let src_w = i32(uniforms.dstOriginSize.z);
    let src_h = i32(uniforms.dstOriginSize.w);
    let radius = i32(uniforms.axisTileRadius.w + 0.5);
    let tile_mode = i32(uniforms.axisTileRadius.z + 0.5);

    // Guard rail -- if the scissor lets through fragments outside the
    // scratch extent, return zero. The host sets a scissor matching
    // srcW x srcH, so this branch should be dead.
    if (dst_px.x < 0 || dst_px.x >= src_w ||
        dst_px.y < 0 || dst_px.y >= src_h) {
        return vec4f(0.0);
    }

    var acc = vec4f(0.0);
    // Centre tap (k = 0).
    acc = acc + tile_load(dst_px.x, dst_px.y, src_w, src_h, tile_mode) * weight_at(0);
    // Symmetric off-centre taps.
    for (var k: i32 = 1; k <= radius; k = k + 1) {
        let w = weight_at(k);
        acc = acc + tile_load(dst_px.x - k, dst_px.y, src_w, src_h, tile_mode) * w;
        acc = acc + tile_load(dst_px.x + k, dst_px.y, src_w, src_h, tile_mode) * w;
    }
    return acc;
}

// Vertical pass : sample the H-pass scratch along Y. Output is pure
// blurred RGBA (no paint-colour fold). The final composite onto the
// parent intermediate runs through `layer_composite.wgsl` in a
// subsequent render pass.
@fragment
fn fs_vertical(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let dst_px = vec2i(i32(floor(pos.x)), i32(floor(pos.y)));
    let src_w = i32(uniforms.dstOriginSize.z);
    let src_h = i32(uniforms.dstOriginSize.w);
    let radius = i32(uniforms.axisTileRadius.w + 0.5);
    let tile_mode = i32(uniforms.axisTileRadius.z + 0.5);

    if (dst_px.x < 0 || dst_px.x >= src_w ||
        dst_px.y < 0 || dst_px.y >= src_h) {
        return vec4f(0.0);
    }

    var acc = vec4f(0.0);
    acc = acc + tile_load(dst_px.x, dst_px.y, src_w, src_h, tile_mode) * weight_at(0);
    for (var k: i32 = 1; k <= radius; k = k + 1) {
        let w = weight_at(k);
        acc = acc + tile_load(dst_px.x, dst_px.y - k, src_w, src_h, tile_mode) * w;
        acc = acc + tile_load(dst_px.x, dst_px.y + k, src_w, src_h, tile_mode) * w;
    }
    return acc;
}
