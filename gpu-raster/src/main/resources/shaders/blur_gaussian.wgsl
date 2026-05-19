// MaskFilter -- separable Gaussian blur shader for SkBlurMaskFilter(kNormal).
//
// Two entry points :
//   - fs_horizontal : sample the source texture along X with Gaussian
//     weights, write to a scratch RGBA target. No paint colour
//     modulation here ; the colour fold happens in the V pass to
//     keep the H pass colour-agnostic.
//   - fs_vertical_composite : sample the H-pass scratch along Y with
//     Gaussian weights, multiply the resulting alpha by paintColor
//     (premul), and write the final composite onto the parent's
//     intermediate. Blend mode is honoured by the pipeline's blend
//     state (kClear / kSrc / kSrcOver / kDstOver natively-blendable
//     subset, same gate as layer_composite.wgsl).
//
// Both passes use `textureLoad` (no sampler) with integer dst-pixel
// coordinates -- 1:1 grid alignment with the shape mask. The shape
// mask is rendered by a child SkWebGpuDevice (intermediate format
// RGBA16Float), sized to (bounds + 3*sigma padding). The H pass
// targets a scratch texture of the same size and format ; the V pass
// targets the parent's intermediate, with dstOrigin shifting the
// sample position back into source-space pixel coords.
//
// Kernel weights are pre-computed CPU-side (matches the CPU raster's
// gaussianKernel1D in SkBlurMaskFilter.kt) and shipped as a symmetric
// half-kernel : weight at offset 0 is the centre tap ; weight at offset
// k (1 <= k <= radius) is shared between -k and +k. The kernel sums
// to 1.0 after the 2x reflection of the off-centre half.
//
// Radius cap : 32 taps per side -- supports sigma up to ~10.6 (3*sigma
// = 32). Larger sigma clamps to this radius at the dispatch gate so
// the uniform layout stays fixed. The visual blur for larger sigma
// becomes slightly under-spread but the kernel mass renormalisation
// (CPU side) keeps the centre alpha exact.
//
// The shape mask source is RGBA16Float, premul, with shape pixels in
// the .rgb = (1,1,1) (white) and .a = coverage. We sample channel
// .r (or .a, equivalent for white-on-clear shape masks) -- the V pass
// scales the paintColor by the blurred coverage.
//
// ASCII strict -- WGSL parser truncates on non-ASCII in wgpu4k 0.2.0.

struct Uniforms {
    // dstOriginSize :
    //   .xy = dstOrigin (where the (0, 0) of the shape-mask source
    //                    lands in the target's pixel grid).
    //   .zw = (srcW, srcH) of the shape mask (and of the H-pass
    //          scratch -- they share the size).
    dstOriginSize: vec4f,    // offset   0
    // paintColor : per-draw paint scale folded into the V pass output.
    // Premul. Shape coverage * paintColor = final premul colour.
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
    // Slots past 4 * 9 = 36 floats (indices 33..35) are unused but
    // must be zeroed by the host so the uniform bytes are deterministic.
    weights:       array<vec4f, 9>,  // offset 48
};

@group(0) @binding(0) var<uniform> uniforms: Uniforms;
@group(0) @binding(1) var source_texture: texture_2d<f32>;
// MaskFilter blur styles (kSolid / kOuter / kInner) : V pass needs the
// original (unblurred) shape-mask alpha M(p) alongside the blurred
// coverage B(p). The H pass ignores this binding -- it samples
// source_texture only -- but the layout entry is required by the
// shared bind-group layout. The host binds the shape-mask view to
// this slot in both H and V bind groups.
@group(0) @binding(2) var shape_mask: texture_2d<f32>;

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

fn weight_at(k: i32) -> f32 {
    // Flat index k -> (vec4f index k / 4, component k % 4).
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
// the source shape mask). textureLoad with integer coords ; out-of-
// bounds reads clamp to (0,0,0,0) (transparent border, matching CPU
// raster's zero-padded buffer).
@fragment
fn fs_horizontal(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let dst_px = vec2i(i32(floor(pos.x)), i32(floor(pos.y)));
    let src_w = i32(uniforms.dstOriginSize.z);
    let src_h = i32(uniforms.dstOriginSize.w);
    let radius = i32(uniforms.axisRadius.z + 0.5);

    // Guard rail -- if the scissor lets through fragments outside
    // the H scratch extent, return zero. The host sets a scissor
    // matching srcW x srcH, so this branch should be dead.
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
// Y, multiply by paintColor (premul), write onto the parent device's
// intermediate. The fragment's dst pixel is in PARENT coordinates ;
// subtract dstOrigin to get the H-pass-scratch pixel position. Out-
// of-bounds reads (shape mask doesn't cover this dst pixel) clamp to
// zero (transparent), preserving the parent's existing pixels.
@fragment
fn fs_vertical_composite(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let dst_px = vec2i(i32(floor(pos.x)), i32(floor(pos.y)));
    let origin_px = vec2i(i32(uniforms.dstOriginSize.x), i32(uniforms.dstOriginSize.y));
    let src_w = i32(uniforms.dstOriginSize.z);
    let src_h = i32(uniforms.dstOriginSize.w);
    let radius = i32(uniforms.axisRadius.z + 0.5);

    // Position in the H-scratch grid.
    let scratch_px = dst_px - origin_px;
    if (scratch_px.x < 0 || scratch_px.x >= src_w) {
        return vec4f(0.0);
    }

    var acc = vec4f(0.0);
    if (scratch_px.y >= 0 && scratch_px.y < src_h) {
        acc = acc + textureLoad(source_texture, scratch_px, 0) * weight_at(0);
    }
    for (var k: i32 = 1; k <= radius; k = k + 1) {
        let w = weight_at(k);
        let yn = scratch_px.y - k;
        let yp = scratch_px.y + k;
        if (yn >= 0 && yn < src_h) {
            acc = acc + textureLoad(source_texture, vec2i(scratch_px.x, yn), 0) * w;
        }
        if (yp >= 0 && yp < src_h) {
            acc = acc + textureLoad(source_texture, vec2i(scratch_px.x, yp), 0) * w;
        }
    }

    // The blurred shape mask alpha is `acc.a` (and acc.r/g/b mirror
    // it since the shape was rendered white-premul).
    let blurAlpha = acc.a;

    // SkBlurStyle composition. Let M = original sharp mask alpha (the
    // shape-mask texel under this fragment) and B = blurred coverage.
    //   kNormal (0) : output = B            -- pure soft blur.
    //   kSolid  (1) : output = min(M+B, 1)  -- sharp shape + halo.
    //   kOuter  (2) : output = max(B-M, 0)  -- halo outside only.
    //   kInner  (3) : output = B * M        -- blur clipped inside.
    //
    // The shape_mask texture is a white-premul render of the shape :
    // .a holds the path coverage. For pixels outside the shape mask's
    // extent (scratch_px out of range), M defaults to 0 so kSolid /
    // kInner / kOuter behave as if M = 0 there.
    var maskAlpha: f32 = 0.0;
    if (scratch_px.x >= 0 && scratch_px.x < src_w &&
        scratch_px.y >= 0 && scratch_px.y < src_h) {
        maskAlpha = textureLoad(shape_mask, scratch_px, 0).a;
    }

    let style = i32(uniforms.axisRadius.w + 0.5);
    var coverage: f32 = blurAlpha;
    if (style == 1) {
        coverage = min(maskAlpha + blurAlpha, 1.0);
    } else if (style == 2) {
        coverage = max(blurAlpha - maskAlpha, 0.0);
    } else if (style == 3) {
        coverage = blurAlpha * maskAlpha;
    }

    return vec4f(
        uniforms.paintColor.r * coverage,
        uniforms.paintColor.g * coverage,
        uniforms.paintColor.b * coverage,
        uniforms.paintColor.a * coverage,
    );
}
