// G5.1 -- bitmap-shader pipeline used by drawImageRect.
//
// Vertex stage : full-screen Bjorke triangle, same pattern as
// `solid_color.wgsl` / `linear_gradient.wgsl`. Pair with
// `setScissorRect(...)` clipped to the (clip-intersected) integer
// pixelEdge-rounded devDst rect so fragments outside the dst rect are
// killed before the fragment stage.
//
// Fragment stage : for each fragment center `(px + 0.5, py + 0.5)` in
// device-pixel coords, map back to source-pixel coords via the affine
//   sx = src.l + (devX - dst.l) * (src.w / dst.w)
//   sy = src.t + (devY - dst.t) * (src.h / dst.h)
// then divide by image size to derive normalised UV. `textureSampleLevel`
// with `lod = 0` does the (bilinear or nearest) filter on the sampler
// bound at slot 1. The sampler picks the filter mode (Linear/Nearest)
// AND the address mode (ClampToEdge/Repeat/MirrorRepeat) -- the shader
// is filter-agnostic and only branches on tile mode for kDecal (G5.1.1).
//
// The texture is uploaded as RGBA8Unorm with **unpremul sRGB-encoded
// bytes** (the SkImage convention -- `pixels[i*4+0..3] = R G B A` non-
// premultiplied, sRGB-tagged). The intermediate target convention is
// **premul sRGB-coded** (matches solid_color.wgsl and the gradients), so
// the fragment output multiplies RGB by alpha before returning. The
// `paint.alpha` and uniform tint (color filter / blend modulation) are
// folded into the sampled alpha by a uniform `paintColor` scale that
// defaults to (1, 1, 1, 1) when the paint is null.
//
// G5.1.1 -- tile mode plumbed through `imageSize.z` (bit-reinterpreted
// u32 ; matches the `Float.fromBits` packing used by the gradient
// shaders). Values mirror `SkTileMode` ordinals :
//   0 = kClamp    -> sampler ClampToEdge ; shader pass-through.
//   1 = kRepeat   -> sampler Repeat       ; shader pass-through.
//   2 = kMirror   -> sampler MirrorRepeat ; shader pass-through.
//   3 = kDecal    -> sampler ClampToEdge (WebGPU has no `BorderColor`
//                    for non-depth textures) ; shader returns transparent
//                    when the requested UV falls outside [0, 1].
// The dispatch gate (see SkWebGpuDevice.drawImageRect) currently feeds
// kClamp ; the other modes are reachable via the test-only enqueue path.
//
// G5.2 -- per-axis tile modes : `imageSize.z` carries `tileX`,
// `imageSize.w` carries `tileY` (both bit-reinterpreted u32). Each axis
// runs the kDecal check independently against the corresponding UV
// coord. `SkBitmapShader` is constructed with `(tileX, tileY)` as a
// pair, so the sampler's `addressModeU` / `addressModeV` and the
// in-shader decal check must both honour the split.
//
// G6.2 -- intermediate target is `RGBA16Float` ; the output convention
// is unchanged (premul sRGB-coded). F16 buys sub-byte precision on
// downstream blends and bilinear lerps ; no colorspace switch.
//
// G5.3 -- texture color management. The intermediate target convention
// is premul sRGB-coded (sRGB primaries, sRGB OETF). If the source
// `SkImage` carries a non-sRGB color space, its texels are in that
// source space's encoded primaries. To draw correctly we :
//   (1) sample the texel (unpremul, source-encoded),
//   (2) linearize through the source TF,
//   (3) multiply by a 3x3 primaries matrix (source -> sRGB),
//   (4) re-encode through the sRGB OETF,
//   (5) then proceed with the existing premul-by-alpha + paintColor.
// `csFlags.x` is a bit-reinterpreted u32 sentinel selecting the source-TF
// linearise stage :
//   0 = no transform (existing fast path -- sRGB or untagged source).
//   1 = sRGB EOTF + primaries matrix (G5.3 ; Display P3 falls here, it
//       shares the sRGB curve, only the primaries matrix differs).
//   2 = parametric sRGBish TF + primaries matrix (G5.3.x ; covers
//       Rec.2020-linear, Adobe RGB / k2Dot2, Rec.709 / kRec2020-bit-depths
//       -- any source whose TF classifies as sRGBish but is not the sRGB
//       curve itself). `csTfParams0/1` carry the 7 parametric coefficients
//       (g, a, b, c, d, e, f) ; the OETF on the output side stays the
//       sRGB OETF (the intermediate target's encoding does not change).
// The matrix is column-major (`mat3x3<f32>`) ; for sRGB images the host
// uploads the identity so the multiply is a no-op even when the flag
// would route through a transform branch -- the flag is the gate.
//
// Limitations enforced at the dispatch gate (see SkWebGpuDevice
// drawImageRect) :
//  - No paint.shader-as-bitmap (drawImageRect only ; G5.2 onwards).
//  - Color management :
//      sRGB (no-op, mode=0),
//      sRGB-TF + non-sRGB gamut (Display P3 ; mode=1),
//      sRGBish parametric TF + any gamut (Rec.2020 linear, Adobe RGB ;
//        mode=2).
//    HDR families (Rec.2020 PQ, HLG, ProPhoto with non-sRGBish TF) still
//    bypass the transform branch -- they need luminance scaling / OOTF
//    handling out of scope for G5.3.x.
//
// G2.x clip-shape (this slice) -- the trailing two vec4 slots carry
// the analytical "simple shape" clip captured from the SkCanvas's
// clip stack. Same shape as `solid_color.wgsl` : `clipShapeBounds` is
// `(l, t, r, b)` in device-pixel coords ; `clipShapeRadiiKind` is
// `(rx, ry, clipKind, _)` with `clipKind in {0, 1}`. When clipKind ==
// 0, the slots are ignored (rect-clip fast path -- already enforced
// by the scissor) ; when clipKind == 1, the `rrect_cov` helper below
// multiplies the fragment output by the analytic rrect coverage so
// pixels outside the curved clip get 0, inside get 1, with a smooth
// half-pixel band on the boundary. The two slots come AFTER the G5.3
// colorspace block (`csMatrix + csTfParams0 + csTfParams1`) to keep
// the colorspace layout contiguous ; total uniform size grows from
// 128 to 192 bytes (32 for G5.3 csTfParams + 32 for G2.x clip slots).
//
// ASCII strict -- WGSL parser truncates on non-ASCII in wgpu4k 0.2.0.

struct Uniforms {
    // Source rect in source-image-pixel coords (l, t, r, b).
    srcRect:   vec4f,    // offset  0
    // Destination rect in device-pixel coords (l, t, r, b).
    dstRect:   vec4f,    // offset 16
    // Image size in source pixels (w, h, tileX, tileY).
    // `.z` carries the X-axis tile mode and `.w` carries the Y-axis
    // tile mode -- both bit-reinterpreted u32 (G5.2 split, see header
    // comment). G5.1 / G5.1.1 fed `tileX = tileY` so the single-tile
    // semantics survive as a degenerate case.
    imageSize: vec4f,    // offset 32
    // Per-draw paint scale folded into the sampled color (premul vec4f).
    // Defaults to (1, 1, 1, 1) -- paint.alpha and color filter overrides
    // can scale rgba multiplicatively here (G5.x follow-ups).
    paintColor: vec4f,   // offset 48
    // G5.3 -- color-space transform gate. `.x` is a bit-reinterpreted
    // u32 sentinel :
    //   0 = no transform (identity / sRGB fast path).
    //   1 = sRGB EOTF + matrix + sRGB OETF.
    //   2 = parametric sRGBish EOTF + matrix + sRGB OETF (G5.3.x ;
    //       Rec.2020 linear / Adobe RGB / Rec.709 / ...).
    // `.y/.z/.w` are reserved (zero-padded).
    csFlags: vec4f,      // offset 64
    // G5.3 -- column-major 3x3 primaries matrix (source-linear ->
    // sRGB-linear). std140 stores each column padded to 16 bytes, so
    // the struct consumes 48 bytes here (offsets 80 / 96 / 112). For
    // sRGB sources the host uploads the identity ; for Display P3 it
    // uploads the P3 -> sRGB primaries transform built on the CPU via
    // `SkColorSpaceXformSteps`.
    csMatrix:  mat3x3<f32>, // offset 80
    // G5.3.x -- parametric source-TF coefficients, used only when
    // `csFlags.x == 2`. The 7-float Skia parametric form
    //   y = (a*x + b)^g + e   if x >= d
    //   y = c*x + f           if x <  d
    // is packed `(g, a, b, c)` in `csTfParams0` and `(d, e, f, _)` in
    // `csTfParams1`. For Rec.2020 linear the host uploads `(1, 1, 0, 0,
    // 0, 0, 0)` (identity power law) ; for Adobe RGB / k2Dot2 it
    // uploads `(2.2, 1, 0, 0, 0, 0, 0)` (pure 2.2 power law). The slot
    // is unused (uploaded as zero) when `csFlags.x != 2` so the std140
    // alignment is stable across modes.
    csTfParams0: vec4f,  // offset 128
    csTfParams1: vec4f,  // offset 144
    // G2.x -- analytical clip-shape payload, mirrors `solid_color.wgsl`.
    // `clipShapeRadiiKind.z` is the kind enum (0 = no shape clip ; 1 =
    // rrect / oval / circle ; same encoding as the rect pipeline). The
    // two slots come AFTER the G5.3 colorspace block so the colorspace
    // layout stays contiguous (G2.x sits at offsets 160/176).
    clipShapeBounds:    vec4f, // offset 160 : (l, t, r, b) device-px
    clipShapeRadiiKind: vec4f, // offset 176 : (rx, ry, clipKind, _)
};

// Tile-mode constants -- mirror `SkTileMode` ordinals.
const TILE_DECAL: u32 = 3u;

// G5.3 -- color-space transform mode sentinel.
//   0 = no-op (sRGB source or any source whose pipeline reduces to
//       identity ; the host gate covers Skia's
//       `SkColorSpaceXformSteps::Flags::isIdentity`).
//   1 = sRGB EOTF + matrix + sRGB OETF.
//   2 = parametric sRGBish EOTF + matrix + sRGB OETF (G5.3.x).
// Higher values are reserved for future TF families (PQ / HLG / ...).
const CS_MODE_IDENTITY: u32 = 0u;
const CS_MODE_SRGB_TF_MATRIX: u32 = 1u;
const CS_MODE_PARAMETRIC_TF_MATRIX: u32 = 2u;

@group(0) @binding(0) var<uniform> uniforms: Uniforms;
@group(0) @binding(1) var image_texture: texture_2d<f32>;
@group(0) @binding(2) var image_sampler: sampler;

// G5.3 -- sRGB EOTF (encoded -> linear). Matches `present_pass.wgsl`'s
// `srgb_to_linear` byte-for-byte ; we keep a local copy here so the
// bitmap-shader module is self-contained.
fn srgb_to_linear(v: f32) -> f32 {
    if (v <= 0.04045) {
        return v / 12.92;
    }
    return pow((v + 0.055) / 1.055, 2.4);
}

// G5.3 -- sRGB OETF (linear -> encoded).
fn linear_to_srgb(v: f32) -> f32 {
    let c = max(v, 0.0);
    if (c <= 0.0031308) {
        return 12.92 * c;
    }
    return 1.055 * pow(c, 1.0 / 2.4) - 0.055;
}

// G5.3.x -- parametric sRGBish TF eval. Mirror of
// `skcmsTransferFunctionEval(tf, x)` in `Skcms.kt` for the `sRGBish`
// classification : strips out the `sign` extraction (texel bytes are
// in [0, 1], no negatives reach this path) and evaluates the two
// branches by the source TF's `d` split. `csTfParams0 = (g, a, b, c)`,
// `csTfParams1 = (d, e, f, _)`.
//
// For Rec.2020-linear the host uploads `(1, 1, 0, 0, 0, 0, 0)` ; the
// power branch returns `(1*x + 0)^1 + 0 = x`. For Adobe RGB / k2Dot2
// the host uploads `(2.2, 1, 0, 0, 0, 0, 0)` ; the power branch
// returns `x^2.2`. The linear branch is unreachable (`d == 0`) so the
// `c` and `f` slots are zero -- the formula is correct regardless.
fn parametric_tf(v: f32) -> f32 {
    let g = uniforms.csTfParams0.x;
    let a = uniforms.csTfParams0.y;
    let b = uniforms.csTfParams0.z;
    let c = uniforms.csTfParams0.w;
    let d = uniforms.csTfParams1.x;
    let e = uniforms.csTfParams1.y;
    let f = uniforms.csTfParams1.z;
    let clamped = max(v, 0.0);
    if (clamped < d) {
        return c * clamped + f;
    }
    return pow(a * clamped + b, g) + e;
}

// G2.x -- analytic coverage of an axis-aligned rounded rect at fragment
// center `p`. Copy of `solid_color.wgsl`'s `rrect_cov` (kept local so
// the bitmap-shader module stays self-contained ; WGSL has no shared
// include). The formula and constants are byte-identical to the rect
// pipeline -- see `solid_color.wgsl` for the full derivation comment.
fn rrect_cov(p: vec2f, bounds: vec4f, rx_in: f32, ry_in: f32) -> f32 {
    let centre = vec2f(0.5 * (bounds.x + bounds.z), 0.5 * (bounds.y + bounds.w));
    let half = vec2f(0.5 * (bounds.z - bounds.x), 0.5 * (bounds.w - bounds.y));
    let rx = max(rx_in, 1e-4);
    let ry = max(ry_in, 1e-4);
    let q_abs = abs(p - centre);
    let q = q_abs - (half - vec2f(rx, ry));
    let inner_rect_sdf = max(q.x, q.y);
    let outer_rect_sdf = max(q_abs.x - half.x, q_abs.y - half.y);
    let qm = max(q, vec2f(0.0, 0.0));
    let n = vec2f(qm.x / rx, qm.y / ry);
    let nl = length(n);
    let nl_safe = max(nl, 1e-6);
    let dir = n / nl_safe;
    let effective_r = length(vec2f(rx * dir.x, ry * dir.y));
    let corner_sdf = (nl - 1.0) * effective_r;
    let in_corner_band = step(0.0, q.x) * step(0.0, q.y);
    let band_sdf = mix(outer_rect_sdf, corner_sdf, in_corner_band);
    let final_sdf = band_sdf;
    return clamp(0.5 - final_sdf, 0.0, 1.0);
}

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

@fragment
fn fs_main(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    // pos.xy is the pixel center (column p -> p + 0.5). Map back to
    // source-image-pixel coords through the dst -> src affine.
    let dst_w = uniforms.dstRect.z - uniforms.dstRect.x;
    let dst_h = uniforms.dstRect.w - uniforms.dstRect.y;
    let src_w = uniforms.srcRect.z - uniforms.srcRect.x;
    let src_h = uniforms.srcRect.w - uniforms.srcRect.y;

    let sx = uniforms.srcRect.x + (pos.x - uniforms.dstRect.x) * (src_w / dst_w);
    let sy = uniforms.srcRect.y + (pos.y - uniforms.dstRect.y) * (src_h / dst_h);

    // Normalise to [0, 1] UV. Tile-mode handling is split between the
    // sampler (Clamp/Repeat/Mirror via addressModeU/V) and the shader
    // (kDecal -- transparent outside [0, 1]).
    let u = sx / uniforms.imageSize.x;
    let v = sy / uniforms.imageSize.y;

    // kDecal : WebGPU has no `BorderColor` mode for sampled (non-depth)
    // textures, so we emulate it -- the sampler stays ClampToEdge and
    // the shader kills out-of-rect fragments here. Matches the
    // `fs_decal` idiom in linear_gradient.wgsl. G5.2 -- per-axis :
    // kill if `tileX == kDecal && u outside [0, 1]` OR
    // `tileY == kDecal && v outside [0, 1]`. The two axes do not
    // interact (e.g. `(kRepeat, kDecal)` repeats horizontally and
    // decals vertically).
    let tile_x = bitcast<u32>(uniforms.imageSize.z);
    let tile_y = bitcast<u32>(uniforms.imageSize.w);
    if (tile_x == TILE_DECAL && (u < 0.0 || u > 1.0)) {
        return vec4f(0.0, 0.0, 0.0, 0.0);
    }
    if (tile_y == TILE_DECAL && (v < 0.0 || v > 1.0)) {
        return vec4f(0.0, 0.0, 0.0, 0.0);
    }

    // Sample (filter mode = Nearest or Linear, picked by the sampler).
    let sampled = textureSampleLevel(image_texture, image_sampler, vec2f(u, v), 0.0);

    // G5.3 -- texture color management. When the host marks the source
    // image as non-sRGB (csFlags.x != CS_MODE_IDENTITY), apply the
    // source-TF -> primaries-matrix -> sRGB-OETF chain on the sampled
    // *unpremul* RGB before the premul step below. Alpha is untouched
    // by the colorspace transform (matches Skia's xform pipeline).
    //
    // The source-TF stage is selected by `csFlags.x` :
    //   1 = sRGB EOTF (hardcoded constants ; matches `srgb_to_linear`).
    //   2 = parametric sRGBish TF (G5.3.x ; coefs from `csTfParams0/1`).
    // The OETF on the output side is always the sRGB OETF because the
    // intermediate target's encoding does not change.
    //
    // Note: in WGSL, `mat3x3 * vec3` is column-vector matrix multiply,
    // which matches the column-major layout we upload from the host.
    var src_rgb = vec3f(sampled.r, sampled.g, sampled.b);
    let cs_mode = bitcast<u32>(uniforms.csFlags.x);
    if (cs_mode == CS_MODE_SRGB_TF_MATRIX) {
        let lin = vec3f(
            srgb_to_linear(src_rgb.r),
            srgb_to_linear(src_rgb.g),
            srgb_to_linear(src_rgb.b),
        );
        let lin_srgb = uniforms.csMatrix * lin;
        src_rgb = vec3f(
            linear_to_srgb(lin_srgb.r),
            linear_to_srgb(lin_srgb.g),
            linear_to_srgb(lin_srgb.b),
        );
    } else if (cs_mode == CS_MODE_PARAMETRIC_TF_MATRIX) {
        let lin = vec3f(
            parametric_tf(src_rgb.r),
            parametric_tf(src_rgb.g),
            parametric_tf(src_rgb.b),
        );
        let lin_srgb = uniforms.csMatrix * lin;
        src_rgb = vec3f(
            linear_to_srgb(lin_srgb.r),
            linear_to_srgb(lin_srgb.g),
            linear_to_srgb(lin_srgb.b),
        );
    }

    // The uploaded texture carries **unpremul** source-encoded bytes
    // (SkImage convention -- post-G5.3 the source colorspace can be
    // non-sRGB ; the transform branch above lifts those texels into
    // sRGB-encoded values before premul). Multiply RGB by alpha to
    // match the premul intermediate convention. paintColor multiplies
    // the result so the paint.alpha / color filter (future G5.x) can
    // fold in at no extra pipeline cost ; default is (1, 1, 1, 1).
    var pa = sampled.a * uniforms.paintColor.a;
    var pr = src_rgb.r * sampled.a * uniforms.paintColor.r;
    var pg = src_rgb.g * sampled.a * uniforms.paintColor.g;
    var pb = src_rgb.b * sampled.a * uniforms.paintColor.b;

    // G2.x -- analytical clip-shape coverage. clipKind == 0 means "no
    // shape clip" (legacy rect-only ; the integer scissor is the only
    // clip), so the multiply is skipped. clipKind == 1 evaluates the
    // rrect coverage formula and folds it into the premul output so
    // pixels outside the clip shape get 0, fully inside get 1, with a
    // smooth half-pixel band on the boundary. Matches `solid_color.wgsl`
    // byte-for-byte.
    let clip_kind = i32(uniforms.clipShapeRadiiKind.z + 0.5);
    if (clip_kind == 1) {
        let clip_cov = rrect_cov(
            pos.xy,
            uniforms.clipShapeBounds,
            uniforms.clipShapeRadiiKind.x,
            uniforms.clipShapeRadiiKind.y,
        );
        pr = pr * clip_cov;
        pg = pg * clip_cov;
        pb = pb * clip_cov;
        pa = pa * clip_cov;
    }
    return vec4f(pr, pg, pb, pa);
}
