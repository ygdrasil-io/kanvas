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
//   3 = SMPTE ST 2084 PQ EOTF + primaries matrix + sRGB OETF (G5.3.y ;
//       Rec.2020 PQ HDR sources). PQ-coded `N in [0, 1]` maps to nits
//       via the 5-coefficient + power-law spec ; we then divide by a
//       peak-luminance constant (passed in `csTfParams0.x`, typically
//       1000 nits) to land in the SDR `[0, 1]` working range, clip,
//       multiply by the Rec.2020->sRGB primaries matrix, and re-encode
//       through the sRGB OETF. This is a deliberate tone-mapping
//       simplification : preserving HDR through the pipeline would
//       require an F16 working space ; for now we tonemap-by-divide
//       at the texture-sample boundary.
//   4 = BT.2100 HLG inverse OETF + primaries matrix + sRGB OETF
//       (G5.3.y ; Rec.2020 HLG HDR sources). Same convention as PQ :
//       `csTfParams0.x` is peak luminance (typically 1000 nits) ;
//       the HLG inverse runs the BT.2100 split linear-log curve
//       (`a, b, c = 0.17883277, 0.28466892, 0.55991073`) producing
//       linear scene light in `[0, 12]`, divided by 12 to land in
//       `[0, 1]` SDR-coded range, then matrix + sRGB OETF.
// The matrix is column-major (`mat3x3<f32>`) ; for sRGB images the host
// uploads the identity so the multiply is a no-op even when the flag
// would route through a transform branch -- the flag is the gate. For
// PQ / HLG sources the host uploads the canonical Rec.2020-linear ->
// sRGB-linear primaries matrix (`SkColorSpaceXformSteps.srcToDstMatrix`
// with `(SkColorSpace.makeRGB(kLinear, kRec2020), sRGB)` -- the same
// Bradford-adapted Rec.2020 toXYZD50 cells the rest of the pipeline
// uses).
//
// Limitations enforced at the dispatch gate (see SkWebGpuDevice
// drawImageRect) :
//  - No paint.shader-as-bitmap (drawImageRect only ; G5.2 onwards).
//  - Color management :
//      sRGB (no-op, mode=0),
//      sRGB-TF + non-sRGB gamut (Display P3 ; mode=1),
//      sRGBish parametric TF + any gamut (Rec.2020 linear, Adobe RGB ;
//        mode=2),
//      Rec.2020 PQ (mode=3) and Rec.2020 HLG (mode=4) HDR sources --
//        tone-mapped by dividing the linear nits/scene-light by a
//        peak-luminance constant (`csTfParams0.x`, default 1000) so
//        downstream blends stay in the existing SDR-coded [0,1] working
//        range. HDR-through-the-pipeline preservation needs an F16
//        intermediate target and is out of scope.
//    ProPhoto with non-sRGBish TF still bypasses the transform branch.
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
// G5.2.2 -- rotated / skewed bitmap shader. The host
// always builds a 2x3 device-to-image affine `M^-1 = (ctm *
// localMatrix)^-1` and ships it via `devToImageRow0` / `devToImageRow1`
// (offsets 192 / 208). The fragment stage computes
//   sx = m0.x * pos.x + m0.y * pos.y + m0.z
//   sy = m1.x * pos.x + m1.y * pos.y + m1.z
// directly, then divides by imageSize for the UV. This unifies the
// axis-aligned and rotated/skewed paths : the legacy `srcRect/dstRect`
// pair is preserved in the uniform layout (it still drives the host-
// side scissor rounding) but is no longer read by the fragment stage.
// Total uniform size grows from 192 to 224 bytes (+32 for the affine).
//
// Phase H2 paint-colorFilter (this slice) -- the 6 trailing vec4f
// slots carry an optional `SkColorFilter` (same packing as
// `solid_color.wgsl` / `layer_composite.wgsl`). The filter is applied
// after the G5.3 colorspace transform (so colours are already in the
// sRGB-coded working space) and BEFORE the rrect clipShape coverage
// multiply, matching Skia's `shader -> colorFilter -> maskFilter ->
// clip -> blend` ordering. Filter slots sit AFTER the G5.2.2 affine
// to keep the existing block contiguous ; total uniform size grows
// from 224 to 320 bytes (+96 = 6 * 16 for the colorFilter payload).
//
//   colorFilterKindMode.x == 0 : no-op (default ; fast path).
//   colorFilterKindMode.x == 1 : SkColorFilters.Blend(colour, mode).
//   colorFilterKindMode.x == 2 : SkColorFilters.Matrix(20 floats).
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
    //   3 = PQ EOTF + tone-map (divide by peak) + matrix + sRGB OETF
    //       (G5.3.y ; Rec.2020 PQ HDR sources).
    //   4 = HLG inverse OETF + tone-map (divide by peak) + matrix
    //       + sRGB OETF (G5.3.y ; Rec.2020 HLG HDR sources).
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
    //
    // G5.3.y -- when `csFlags.x == 3` (PQ) or `csFlags.x == 4` (HLG)
    // the slot is repurposed : `csTfParams0.x` carries the peak
    // luminance constant in nits (typically 1000) used to tone-map the
    // recovered linear scene light down to the SDR `[0, 1]` working
    // range. The remaining `csTfParams0/1` slots are unused in modes
    // 3 / 4 (uploaded as zero ; the hardcoded PQ / HLG math is
    // self-contained, no parametric coefs to look up).
    csTfParams0: vec4f,  // offset 128
    csTfParams1: vec4f,  // offset 144
    // G2.x -- analytical clip-shape payload, mirrors `solid_color.wgsl`.
    // `clipShapeRadiiKind.z` is the kind enum (0 = no shape clip ; 1 =
    // rrect / oval / circle ; same encoding as the rect pipeline).
    // `clipShapeRadiiKind.w` is the strict-src-constraint mode
    // (0 = fast, 1 = strict filter taps, 2 = strict nearest texels).
    // The two slots come AFTER the G5.3 colorspace block so the
    // colorspace layout stays contiguous (G2.x sits at offsets 160/176).
    clipShapeBounds:    vec4f, // offset 160 : (l, t, r, b) device-px
    clipShapeRadiiKind: vec4f, // offset 176 : (rx, ry, clipKind, _)
    // G5.2.2 / sampler widening -- device-to-image inverse. Row 2 is
    // (0, 0, 1) for affine callers and carries perspective otherwise.
    devToImageRow0:     vec4f, // offset 192 : (sx, kx, tx, _)
    devToImageRow1:     vec4f, // offset 208 : (ky, sy, ty, _)
    devToImageRow2:     vec4f, // offset 224 : (p0, p1, p2, _)
    // (sampleMode, cubicB, cubicC, _). 0 = sampler nearest/linear,
    // 1 = 16-tap bicubic using B/C coefficients.
    sampleOptions:      vec4f, // offset 240 : (mode, B, C, _)
    // Phase H2 paint-colorFilter -- 6 trailing vec4f carrying an
    // optional `SkColorFilter` (same packing as `solid_color.wgsl` /
    // `layer_composite.wgsl`). The host-side packer
    // ([SkWebGpuDevice.packLayerCompositeColorFilter]) is shared.
    //   colorFilterKindMode.x == 0 -> no-op (default fast path).
    //   colorFilterKindMode.x == 1 -> SkBlendModeFilter (param0 = premul
    //                                  src colour, kindMode.y = mode).
    //   colorFilterKindMode.x == 2 -> SkMatrixFilter (param0..3 = rows,
    //                                  bias = additive 5th column).
    colorFilterKindMode: vec4f, // offset 256 : (kind, blendMode, _, _)
    colorFilterParam0:   vec4f, // offset 272
    colorFilterParam1:   vec4f, // offset 288
    colorFilterParam2:   vec4f, // offset 304
    colorFilterParam3:   vec4f, // offset 320
    colorFilterBias:     vec4f, // offset 336
};

// Tile-mode constants -- mirror `SkTileMode` ordinals.
const TILE_DECAL: u32 = 3u;
const SAMPLE_MODE_CUBIC: u32 = 1u;
const SAMPLE_MODE_IMPLICIT_SAMPLER: u32 = 2u;

// G5.3 / G5.3.x / G5.3.y -- color-space transform mode sentinel.
//   0 = no-op (sRGB source or any source whose pipeline reduces to
//       identity ; the host gate covers Skia's
//       `SkColorSpaceXformSteps::Flags::isIdentity`).
//   1 = sRGB EOTF + matrix + sRGB OETF.
//   2 = parametric sRGBish EOTF + matrix + sRGB OETF (G5.3.x).
//   3 = PQ EOTF + tone-map (divide by peak nits) + matrix + sRGB OETF
//       (G5.3.y).
//   4 = HLG inverse OETF + tone-map (divide by peak nits) + matrix
//       + sRGB OETF (G5.3.y).
// Higher values reserved for future TF families.
const CS_MODE_IDENTITY: u32 = 0u;
const CS_MODE_SRGB_TF_MATRIX: u32 = 1u;
const CS_MODE_PARAMETRIC_TF_MATRIX: u32 = 2u;
const CS_MODE_PQ: u32 = 3u;
const CS_MODE_HLG: u32 = 4u;

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

// G5.3.y -- SMPTE ST 2084 PQ EOTF. Maps a PQ-coded value `N in [0, 1]`
// to linear nits via the 5-coefficient + power-law formula
// (`include/private/SkPQ.h`, BT.2100-2 Table 4). Constants below match
// the integer-rationals upstream Skia ships in `skcms.cc` byte-for-byte.
// Returns nits (cd/m^2) ; caller divides by peak luminance to land in
// the SDR `[0, 1]` working range.
//
// `pow(0, fractional)` is implementation-defined in WGSL ; clamp the
// input to avoid the `N = 0` edge case (the spec maps `N = 0 -> 0` nits
// but the formula goes through `0^(1/m2)` which can NaN). We clamp the
// numerator to `>= 0` so the divide stays well-defined.
fn pq_eotf(N_in: f32) -> f32 {
    let m1 = 0.1593017578125;          // 2610 / 16384
    let m2 = 78.84375;                 // 2523 / 4096 * 128
    let c1 = 0.8359375;                // 3424 / 4096
    let c2 = 18.8515625;               // 2413 / 4096 * 32
    let c3 = 18.6875;                  // 2392 / 4096 * 32
    let N = clamp(N_in, 0.0, 1.0);
    let Np = pow(N, 1.0 / m2);
    let num = max(Np - c1, 0.0);
    let den = max(c2 - c3 * Np, 1.0e-6);
    return pow(num / den, 1.0 / m1) * 10000.0;
}

// G5.3.y -- BT.2100 HLG inverse OETF. Maps an HLG-coded value
// `E' in [0, 1]` to linear scene light in `[0, 1]` via the split
// linear-log curve (ITU-R BT.2100-2 Table 5, `a, b, c` constants in
// upstream `skcms.cc:259-264`).
//   HLG_inverse(E') = E'^2 / 3                        if E' <= 0.5
//                     (exp((E' - c) / a) + b) / 12    if E' >  0.5
// The EOTF tone-maps to display light by `HLG_inverse(E') * Lw`. This
// slice's convention is `Lw = peakLuminance` so the tone-mapped output
// reduces to `HLG_inverse(E')` directly (no extra divide). The caller
// applies the Rec.2020 -> sRGB primaries matrix on the linear result.
fn hlg_inverse_oetf(Ep_in: f32) -> f32 {
    let a = 0.17883277;
    let b = 0.28466892;
    let c = 0.55991073;
    let Ep = clamp(Ep_in, 0.0, 1.0);
    if (Ep <= 0.5) {
        return Ep * Ep / 3.0;
    }
    return (exp((Ep - c) / a) + b) / 12.0;
}

// Phase H2 paint-colorFilter -- pure-float blend on premul RGBA. Same
// dispatch table as `solid_color.wgsl`'s `blend_premul` / shared with
// `layer_composite.wgsl`. Inputs are premul ; output is premul.
// Unsupported modes (separable / HSL) fall through to identity src ;
// the host-side packer leaves `kind = 0` for unsupported variants so
// the branch is defensive only.
fn blend_premul(s: vec4f, d: vec4f, mode: u32) -> vec4f {
    let sa = s.a; let da = d.a;
    if (mode == 0u) { return vec4f(0.0); }
    if (mode == 1u) { return s; }
    if (mode == 2u) { return d; }
    if (mode == 3u) {
        let k = 1.0 - sa;
        return vec4f(s.r + d.r * k, s.g + d.g * k, s.b + d.b * k, sa + da * k);
    }
    if (mode == 4u) {
        let k = 1.0 - da;
        return vec4f(d.r + s.r * k, d.g + s.g * k, d.b + s.b * k, da + sa * k);
    }
    if (mode == 5u) { return s * da; }
    if (mode == 6u) { return d * sa; }
    if (mode == 7u) { return s * (1.0 - da); }
    if (mode == 8u) { return d * (1.0 - sa); }
    if (mode == 9u) {
        let k = 1.0 - sa;
        return vec4f(s.r * da + d.r * k, s.g * da + d.g * k,
                     s.b * da + d.b * k, sa * da + da * k);
    }
    if (mode == 10u) {
        let k = 1.0 - da;
        return vec4f(d.r * sa + s.r * k, d.g * sa + s.g * k,
                     d.b * sa + s.b * k, da * sa + sa * k);
    }
    if (mode == 11u) {
        let ks = 1.0 - sa; let kd = 1.0 - da;
        return vec4f(s.r * kd + d.r * ks, s.g * kd + d.g * ks,
                     s.b * kd + d.b * ks, sa * kd + da * ks);
    }
    if (mode == 12u) {
        return min(vec4f(1.0), s + d);
    }
    if (mode == 13u) { return s * d; }
    if (mode == 14u) { return s + d - s * d; }
    return s;
}

// Phase H2 paint-colorFilter -- apply the optional `SkColorFilter` to
// an unpremul RGBA source colour (already in the sRGB-coded working
// space ; the G5.3 colorspace transform has run upstream). Returns
// filtered unpremul RGBA ; `kind == 0` is the no-op fast path
// (identical to the pre-slice output).
fn apply_color_filter(c_un: vec4f) -> vec4f {
    let kind = u32(uniforms.colorFilterKindMode.x + 0.5);
    if (kind == 1u) {
        // SkBlendModeFilter : `param0` is the premul constant src ;
        // premul the dst, run blend_premul, unpremul before returning
        // so the call site can reuse the existing premul pipeline.
        let mode = u32(uniforms.colorFilterKindMode.y + 0.5);
        let a = c_un.a;
        let dst_premul = vec4f(c_un.r * a, c_un.g * a, c_un.b * a, a);
        let out_premul = blend_premul(uniforms.colorFilterParam0, dst_premul, mode);
        let oa = out_premul.a;
        if (oa <= 0.0) { return vec4f(0.0); }
        let inv = 1.0 / oa;
        return vec4f(out_premul.r * inv, out_premul.g * inv, out_premul.b * inv, oa);
    }
    if (kind == 2u) {
        // SkMatrixFilter : Param0..3 are rows of coefficients ; bias is
        // the additive 5th column. Output is clamped to `[0, 1]` before
        // re-premul at the call site (matches SkMatrixColorFilter's
        // storage-edge clamp).
        let out_r = dot(uniforms.colorFilterParam0, c_un) + uniforms.colorFilterBias.x;
        let out_g = dot(uniforms.colorFilterParam1, c_un) + uniforms.colorFilterBias.y;
        let out_b = dot(uniforms.colorFilterParam2, c_un) + uniforms.colorFilterBias.z;
        let out_a = dot(uniforms.colorFilterParam3, c_un) + uniforms.colorFilterBias.w;
        return vec4f(
            clamp(out_r, 0.0, 1.0),
            clamp(out_g, 0.0, 1.0),
            clamp(out_b, 0.0, 1.0),
            clamp(out_a, 0.0, 1.0),
        );
    }
    return c_un;
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

fn cubic_weight(t: f32, B: f32, C: f32) -> f32 {
    let x = abs(t);
    if (x >= 2.0) {
        return 0.0;
    }
    let x2 = x * x;
    let x3 = x2 * x;
    if (x < 1.0) {
        return ((12.0 - 9.0 * B - 6.0 * C) * x3 +
                (-18.0 + 12.0 * B + 6.0 * C) * x2 +
                (6.0 - 2.0 * B)) / 6.0;
    }
    return ((-B - 6.0 * C) * x3 +
            (6.0 * B + 30.0 * C) * x2 +
            (-12.0 * B - 48.0 * C) * x +
            (8.0 * B + 24.0 * C)) / 6.0;
}

fn wrap_index(i: i32, size: i32, tile: u32) -> i32 {
    if (tile == 1u) {
        let m = ((i % size) + size) % size;
        return m;
    }
    if (tile == 2u) {
        let period = max(size * 2, 1);
        let m = ((i % period) + period) % period;
        if (m < size) {
            return m;
        }
        return period - 1 - m;
    }
    return clamp(i, 0, size - 1);
}

fn cubic_tap(ix: i32, iy: i32, x_min: i32, x_max: i32, y_min: i32, y_max: i32,
             strict_constraint: bool, tile_x: u32, tile_y: u32) -> vec4f {
    let width = i32(uniforms.imageSize.x);
    let height = i32(uniforms.imageSize.y);
    var tx = ix;
    var ty = iy;
    if (strict_constraint) {
        tx = clamp(tx, x_min, x_max);
        ty = clamp(ty, y_min, y_max);
    } else {
        if (tile_x == TILE_DECAL && (tx < 0 || tx >= width)) {
            return vec4f(0.0);
        }
        if (tile_y == TILE_DECAL && (ty < 0 || ty >= height)) {
            return vec4f(0.0);
        }
        tx = wrap_index(tx, width, tile_x);
        ty = wrap_index(ty, height, tile_y);
    }
    return textureLoad(image_texture, vec2i(tx, ty), 0);
}

fn cubic_sample(sx: f32, sy: f32, strict_constraint: bool, tile_x: u32, tile_y: u32) -> vec4f {
    let B = uniforms.sampleOptions.y;
    let C = uniforms.sampleOptions.z;
    let xf = sx - 0.5;
    let yf = sy - 0.5;
    let ix_base = i32(floor(xf));
    let iy_base = i32(floor(yf));
    let fx = xf - f32(ix_base);
    let fy = yf - f32(iy_base);
    let max_x = i32(uniforms.imageSize.x) - 1;
    let max_y = i32(uniforms.imageSize.y) - 1;
    let min_x_f = clamp(ceil(uniforms.srcRect.x - 0.5), 0.0, f32(max_x));
    let min_y_f = clamp(ceil(uniforms.srcRect.y - 0.5), 0.0, f32(max_y));
    let max_x_f = clamp(floor(uniforms.srcRect.z - 0.5), min_x_f, f32(max_x));
    let max_y_f = clamp(floor(uniforms.srcRect.w - 0.5), min_y_f, f32(max_y));
    let x_min = i32(min_x_f);
    let y_min = i32(min_y_f);
    let x_max = i32(max_x_f);
    let y_max = i32(max_y_f);
    var sum = vec4f(0.0);
    var sum_w = 0.0;
    for (var j: i32 = 0; j < 4; j = j + 1) {
        let dy = select(2.0 - fy, select(1.0 - fy, select(fy, 1.0 + fy, j == 0), j == 1), j == 2);
        let wy = cubic_weight(dy, B, C);
        for (var i: i32 = 0; i < 4; i = i + 1) {
            let dx = select(2.0 - fx, select(1.0 - fx, select(fx, 1.0 + fx, i == 0), i == 1), i == 2);
            let wx = cubic_weight(dx, B, C);
            let wt = wx * wy;
            let tap = cubic_tap(
                ix_base + i - 1,
                iy_base + j - 1,
                x_min,
                x_max,
                y_min,
                y_max,
                strict_constraint,
                tile_x,
                tile_y,
            );
            sum = sum + tap * wt;
            sum_w = sum_w + wt;
        }
    }
    if (abs(sum_w) <= 1.0e-6) {
        return vec4f(0.0);
    }
    return clamp(sum / sum_w, vec4f(0.0), vec4f(1.0));
}

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

@fragment
fn fs_main(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let hx = uniforms.devToImageRow0.x * pos.x
           + uniforms.devToImageRow0.y * pos.y
           + uniforms.devToImageRow0.z;
    let hy = uniforms.devToImageRow1.x * pos.x
           + uniforms.devToImageRow1.y * pos.y
           + uniforms.devToImageRow1.z;
    let hw = uniforms.devToImageRow2.x * pos.x
           + uniforms.devToImageRow2.y * pos.y
           + uniforms.devToImageRow2.z;
    if (abs(hw) <= 1.0e-6) {
        return vec4f(0.0);
    }
    let sx_raw = hx / hw;
    let sy_raw = hy / hw;

    // SrcRectConstraint parity: strict linear keeps filter taps inside
    // the requested src subset by clamping to texel-center bounds. Strict
    // nearest must instead clamp the integer texel selected by floor(),
    // matching SkBitmapDevice's strictSampleMin/Max rules.
    let strict_mode = uniforms.clipShapeRadiiKind.w;
    let strict_constraint = strict_mode > 0.5;
    let strict_nearest = strict_mode > 1.5;
    var sx = sx_raw;
    var sy = sy_raw;
    if (strict_constraint && !strict_nearest) {
        sx = clamp(sx_raw, uniforms.srcRect.x + 0.5, uniforms.srcRect.z - 0.5);
        sy = clamp(sy_raw, uniforms.srcRect.y + 0.5, uniforms.srcRect.w - 0.5);
    }

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

    // Sample. Linear and fast-nearest use the sampler. Strict-nearest uses
    // textureLoad so half-pixel source subsets keep their border texels
    // instead of snapping to the center-only bilinear clamp.
    var sampled: vec4f;
    let sample_mode = u32(uniforms.sampleOptions.x + 0.5);
    if (sample_mode == SAMPLE_MODE_CUBIC) {
        sampled = cubic_sample(sx_raw, sy_raw, strict_constraint, tile_x, tile_y);
    } else if (strict_nearest) {
        let max_x = uniforms.imageSize.x - 1.0;
        let max_y = uniforms.imageSize.y - 1.0;
        let min_x = clamp(ceil(uniforms.srcRect.x - 0.5), 0.0, max_x);
        let min_y = clamp(ceil(uniforms.srcRect.y - 0.5), 0.0, max_y);
        let strict_max_x = clamp(floor(uniforms.srcRect.z - 0.5), min_x, max_x);
        let strict_max_y = clamp(floor(uniforms.srcRect.w - 0.5), min_y, max_y);
        let ix = i32(clamp(floor(sx_raw), min_x, strict_max_x));
        let iy = i32(clamp(floor(sy_raw), min_y, strict_max_y));
        sampled = textureLoad(image_texture, vec2i(ix, iy), 0);
    } else if (sample_mode == SAMPLE_MODE_IMPLICIT_SAMPLER) {
        sampled = textureSample(image_texture, image_sampler, vec2f(u, v));
    } else {
        sampled = textureSampleLevel(image_texture, image_sampler, vec2f(u, v), 0.0);
    }

    // G5.3 -- texture color management. When the host marks the source
    // image as non-sRGB (csFlags.x != CS_MODE_IDENTITY), apply the
    // source-TF -> primaries-matrix -> sRGB-OETF chain on the sampled
    // *unpremul* RGB before the premul step below. Alpha is untouched
    // by the colorspace transform (matches Skia's xform pipeline).
    //
    // The source-TF stage is selected by `csFlags.x` :
    //   1 = sRGB EOTF (hardcoded constants ; matches `srgb_to_linear`).
    //   2 = parametric sRGBish TF (G5.3.x ; coefs from `csTfParams0/1`).
    //   3 = PQ EOTF + tone-map (G5.3.y ; peak nits in `csTfParams0.x`).
    //   4 = HLG inverse OETF + tone-map (G5.3.y ; peak nits in
    //       `csTfParams0.x`).
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
    } else if (cs_mode == CS_MODE_PQ) {
        // G5.3.y -- Rec.2020 PQ HDR. PQ EOTF lifts the encoded sample
        // to nits ; divide by peak luminance (csTfParams0.x, default
        // 1000) to land in `[0, 1]` SDR-coded, clip, apply the
        // Rec.2020 -> sRGB primaries matrix (host-uploaded in
        // `csMatrix`), and re-encode through the sRGB OETF. Tone-
        // mapping reduces to divide-by-peak ; preserving HDR through
        // the pipeline needs F16 working space.
        let peak_nits = max(uniforms.csTfParams0.x, 1.0);
        let lin = vec3f(
            clamp(pq_eotf(src_rgb.r) / peak_nits, 0.0, 1.0),
            clamp(pq_eotf(src_rgb.g) / peak_nits, 0.0, 1.0),
            clamp(pq_eotf(src_rgb.b) / peak_nits, 0.0, 1.0),
        );
        let lin_srgb = uniforms.csMatrix * lin;
        src_rgb = vec3f(
            linear_to_srgb(lin_srgb.r),
            linear_to_srgb(lin_srgb.g),
            linear_to_srgb(lin_srgb.b),
        );
    } else if (cs_mode == CS_MODE_HLG) {
        // G5.3.y -- Rec.2020 HLG HDR. HLG inverse OETF maps the encoded
        // sample to linear scene light in `[0, 1]` ; with the
        // `Lw = peakLuminance` convention the EOTF tone-mapped output
        // is the HLG-inverse value directly (no extra divide). Apply
        // the Rec.2020 -> sRGB primaries matrix, then sRGB OETF.
        let lin = vec3f(
            hlg_inverse_oetf(src_rgb.r),
            hlg_inverse_oetf(src_rgb.g),
            hlg_inverse_oetf(src_rgb.b),
        );
        let lin_srgb = uniforms.csMatrix * lin;
        src_rgb = vec3f(
            linear_to_srgb(clamp(lin_srgb.r, 0.0, 1.0)),
            linear_to_srgb(clamp(lin_srgb.g, 0.0, 1.0)),
            linear_to_srgb(clamp(lin_srgb.b, 0.0, 1.0)),
        );
    }

    // Phase H2 paint-colorFilter -- run the optional `SkColorFilter`
    // on the unpremul (sRGB-coded) RGBA before the premul + paint
    // modulation. Skia's stage order is `shader -> colorFilter ->
    // maskFilter -> clip -> blend` ; the bitmap shader is the "shader"
    // stage (G5.3 colorspace transform already ran), `paintColor`
    // contributes the paint.alpha modulation, and the rrect clip mod
    // below covers the clip step. The fast path (kind == 0) returns
    // the input unchanged so the bytes stay bit-iso when no filter is
    // attached.
    let filtered_un = apply_color_filter(vec4f(src_rgb.r, src_rgb.g, src_rgb.b, sampled.a));
    // The uploaded texture carries **unpremul** source-encoded bytes
    // (SkImage convention -- post-G5.3 the source colorspace can be
    // non-sRGB ; the transform branch above lifts those texels into
    // sRGB-encoded values before premul). Multiply RGB by alpha to
    // match the premul intermediate convention. paintColor multiplies
    // the result so the paint.alpha modulation folds in at no extra
    // pipeline cost ; default is (1, 1, 1, 1).
    var pa = filtered_un.a * uniforms.paintColor.a;
    var pr = filtered_un.r * filtered_un.a * uniforms.paintColor.r;
    var pg = filtered_un.g * filtered_un.a * uniforms.paintColor.g;
    var pb = filtered_un.b * filtered_un.a * uniforms.paintColor.b;

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
    } else if (clip_kind == 2) {
        // M4 -- kDifference : invert coverage so the shape carves a hole.
        let clip_cov = 1.0 - rrect_cov(
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
