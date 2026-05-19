// MaskFilter -- separable Gaussian blur shader for SkBlurMaskFilter
// applied on a SHADED paint (gradient / bitmap shader). Fork of
// `blur_gaussian.wgsl` (the solid-paint MaskFilter variant) -- the
// convolution math is identical, but :
//   - The source is the SHADED rasterisation of the shape (RGBA premul,
//     including the shader / colorFilter output) instead of a white-
//     tinted alpha-only shape mask.
//   - The V pass does NOT fold a paintColor multiplier (the shader
//     output already carries the final per-pixel colour).
//   - The kSolid / kOuter / kInner style combine operates per-RGBA
//     channel rather than alpha-only :
//       kNormal : output = B
//       kSolid  : output = A + B * (1 - A.a)        (SrcOver A over B)
//       kOuter  : output = B * (1 - M)              (halo outside only)
//       kInner  : output = B * M                    (blur clipped inside)
//     where A is the unblurred shaded layer texel and M = A.a is its
//     coverage (= the sharp shape mask alpha). The combine reduces to
//     the same alpha-only formulas as `blur_gaussian.wgsl` when A is
//     a solid white-premul mask (A.rgb = M, so kSolid collapses to
//     min(M + B, 1) on alpha, kOuter to max(B - M, 0), etc.).
//
// Two entry points :
//   - fs_horizontal : sample the shaded layer along X with Gaussian
//     weights, write to a scratch RGBA target. No style combine here ;
//     the V pass owns it. Out-of-source samples follow kDecal (zero
//     border) -- the layer texture was rendered into a child device
//     sized to (path bounds + 3*sigma padding), so the kernel never
//     reaches outside meaningful pixels anyway.
//   - fs_vertical_composite : sample the H-pass scratch along Y with
//     Gaussian weights to get the blurred RGBA B(p), sample the shaded
//     layer for the original A(p) (and its alpha M = A.a), apply the
//     SkBlurStyle combine, and write the composite onto the parent's
//     intermediate. Blend mode honoured by the pipeline's blend state
//     (kClear / kSrc / kSrcOver / kDstOver subset).
//
// Both passes use `textureLoad` (no sampler) with integer dst-pixel
// coordinates -- 1:1 grid alignment with the shaded layer. The shaded
// layer is rendered by a child SkWebGpuDevice (intermediate format
// RGBA16Float), sized to (bounds + 3*sigma padding). The H pass targets
// a scratch texture of the same size and format ; the V pass targets
// the parent's intermediate, with dstOrigin shifting the sample
// position back into source-space pixel coords.
//
// Kernel weights are pre-computed CPU-side (matches `gaussianKernel1D`
// in SkBlurMaskFilter.kt) and shipped as a symmetric half-kernel :
// weight at offset 0 is the centre tap ; weight at offset k
// (1 <= k <= radius) is shared between -k and +k. The kernel sums
// to 1.0 after the 2x reflection of the off-centre half.
//
// Radius cap : 32 taps per side -- supports sigma up to ~10.6 (3*sigma
// = 32). Larger sigma clamps to this radius at the dispatch gate so
// the uniform layout stays fixed. The visual blur for larger sigma
// becomes slightly under-spread but the kernel mass renormalisation
// (CPU side) keeps the centre alpha exact.
//
// ASCII strict -- WGSL parser truncates on non-ASCII in wgpu4k 0.2.0.

struct Uniforms {
    // dstOriginSize :
    //   .xy = dstOrigin (where the (0, 0) of the shaded layer source
    //                    lands in the target's pixel grid).
    //   .zw = (srcW, srcH) of the shaded layer (and of the H-pass
    //          scratch -- they share the size).
    dstOriginSize: vec4f,    // offset   0
    // paintColor : kept for byte-layout parity with `blur_gaussian.wgsl`
    // but unused -- the shaded layer already carries the final colours.
    // The host packs zeros here.
    paintColor:    vec4f,    // offset  16
    // axisRadius :
    //   .x = 1.0 when sampling along X (H pass), 0.0 else.
    //   .y = 1.0 when sampling along Y (V pass), 0.0 else.
    //   .z = radius (number of off-centre taps per side, 0..32).
    //   .w = blurStyle (0 = kNormal, 1 = kSolid, 2 = kOuter, 3 = kInner).
    //        Only consumed by the V pass ; the H pass is style-agnostic.
    axisRadius:    vec4f,    // offset  32
    // weights[0..8] : symmetric half-kernel packed 4 floats per
    // vec4f. weight at off-centre offset k lives at flat index k :
    //   k = 0 -> weights[0].x  (centre tap)
    //   k = 1 -> weights[0].y
    //   ...
    //   k = 32 -> weights[8].x
    weights:       array<vec4f, 9>,  // offset 48
};

@group(0) @binding(0) var<uniform> uniforms: Uniforms;
@group(0) @binding(1) var source_texture: texture_2d<f32>;
// The unblurred shaded layer (the original A(p) carrying full shader
// output). The V pass reads it for two purposes :
//   - M = A.a -- the sharp coverage mask used by kOuter / kInner.
//   - A.rgb / A.a -- the SrcOver "src" term for kSolid (A on top of B).
// The H pass binding is the same texture in this slot -- the layout is
// shared with `blur_gaussian.wgsl` so we can keep the bind-group
// layout single across the two MaskFilter variants.
@group(0) @binding(2) var shaded_layer: texture_2d<f32>;

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

// Horizontal pass : sample along X. The fragment's dst pixel maps
// 1:1 to the source pixel grid (the H scratch is the same size as
// the source shaded layer). textureLoad with integer coords ; out-of-
// bounds reads clamp to (0,0,0,0) (transparent border).
@fragment
fn fs_horizontal(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let dst_px = vec2i(i32(floor(pos.x)), i32(floor(pos.y)));
    let src_w = i32(uniforms.dstOriginSize.z);
    let src_h = i32(uniforms.dstOriginSize.w);
    let radius = i32(uniforms.axisRadius.z + 0.5);

    if (dst_px.x < 0 || dst_px.x >= src_w ||
        dst_px.y < 0 || dst_px.y >= src_h) {
        return vec4f(0.0);
    }

    var acc = vec4f(0.0);
    // Centre tap (k = 0).
    acc = acc + textureLoad(source_texture, dst_px, 0) * weight_at(0);
    // Symmetric off-centre taps.
    for (var k: i32 = 1; k <= radius; k = k + 1) {
        let w = weight_at(k);
        let xn = dst_px.x - k;
        let xp = dst_px.x + k;
        if (xn >= 0 && xn < src_w) {
            acc = acc + textureLoad(source_texture, vec2i(xn, dst_px.y), 0) * w;
        }
        if (xp >= 0 && xp < src_w) {
            acc = acc + textureLoad(source_texture, vec2i(xp, dst_px.y), 0) * w;
        }
    }
    return acc;
}

// Vertical pass + final composite : sample the H-pass scratch along
// Y, combine with the original shaded layer per SkBlurStyle, write
// onto the parent device's intermediate. The fragment's dst pixel is
// in PARENT coordinates ; subtract dstOrigin to get the H-pass-scratch
// pixel position. Out-of-bounds reads (shaded layer doesn't cover this
// dst pixel) clamp to zero (transparent), preserving the parent's
// existing pixels.
@fragment
fn fs_vertical_composite(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let dst_px = vec2i(i32(floor(pos.x)), i32(floor(pos.y)));
    let origin_px = vec2i(i32(uniforms.dstOriginSize.x), i32(uniforms.dstOriginSize.y));
    let src_w = i32(uniforms.dstOriginSize.z);
    let src_h = i32(uniforms.dstOriginSize.w);
    let radius = i32(uniforms.axisRadius.z + 0.5);

    // Position in the H-scratch / shaded-layer grid.
    let scratch_px = dst_px - origin_px;
    if (scratch_px.x < 0 || scratch_px.x >= src_w ||
        scratch_px.y < 0 || scratch_px.y >= src_h) {
        return vec4f(0.0);
    }

    // V-axis Gaussian convolution on the H-pass scratch -> B (blurred
    // RGBA premul).
    var B = vec4f(0.0);
    B = B + textureLoad(source_texture, scratch_px, 0) * weight_at(0);
    for (var k: i32 = 1; k <= radius; k = k + 1) {
        let w = weight_at(k);
        let yn = scratch_px.y - k;
        let yp = scratch_px.y + k;
        if (yn >= 0 && yn < src_h) {
            B = B + textureLoad(source_texture, vec2i(scratch_px.x, yn), 0) * w;
        }
        if (yp >= 0 && yp < src_h) {
            B = B + textureLoad(source_texture, vec2i(scratch_px.x, yp), 0) * w;
        }
    }

    // A = the original unblurred shaded layer texel at the same scratch
    // coord. M = A.a is the sharp coverage mask.
    let A = textureLoad(shaded_layer, scratch_px, 0);
    let M = A.a;

    let style = i32(uniforms.axisRadius.w + 0.5);
    var out: vec4f = B;
    if (style == 1) {
        // kSolid : A on top of B (SrcOver, A is premul). Output =
        // A + B * (1 - A.a). For A.a = 1 (interior pixels of the sharp
        // shape) the output reduces to A -- the sharp shape stays.
        // For A.a = 0 (outside the shape) the output reduces to B --
        // the halo only.
        out = A + B * (1.0 - A.a);
    } else if (style == 2) {
        // kOuter : halo only. Multiply B by (1 - M) so the interior
        // of the sharp shape goes to zero. For solid white-premul A
        // (A.rgb = M) this matches the upstream max(B - M, 0) up to
        // the clamping side : the unsigned (1 - M) * B premul scale
        // is always non-negative, no clamp needed.
        out = B * (1.0 - M);
    } else if (style == 3) {
        // kInner : blur clipped to inside. Multiply B by M so the
        // exterior of the sharp shape goes to zero.
        out = B * M;
    }
    return out;
}
