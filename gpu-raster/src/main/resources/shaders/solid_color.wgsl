// G2.3a -- single shader for axis-aligned rect fill, both AA and non-AA.
//
// Vertex stage : the same full-screen Bjorke triangle as G0. Pair with
// `setScissorRect(...)` (set conservatively to floor/ceil of the rect
// bounds) so pixels outside the rect's bbox are killed before reaching
// the fragment stage.
//
// Fragment stage : analytical coverage. For each fragment, compute the
// fraction of the pixel that lies inside the rect [bounds.l, bounds.r]
// x [bounds.t, bounds.b] (device-pixel coords). Multiply the premul
// source color by that coverage so SrcOver blending sees a correctly
// alpha-weighted source -- the standard "analytic coverage" path that
// matches SkBitmapDevice's axis-aligned AA rasterizer.
//
// Non-AA path : `bounds` are the pixelEdge-rounded integers ; every
// interior pixel-center sits at least 0.5 device pixels away from any
// edge, so the coverage formula evaluates to exactly 1.0 and the
// output equals the G2.2 unconditional-premul output. Existing tests
// (ClearRedTest / RectFillCrossTest / TranslucentSrcOverTest /
// BlendModeTest) stay byte-identical.
//
// G6.2 -- the intermediate render target is now `RGBA16Float` instead
// of `RGBA8Unorm`. The shader output convention stays the same
// (premul sRGB-coded) ; the only effect of the format change is that
// intermediate values are no longer quantised to 8-bit, so blends and
// gradient outputs preserve sub-byte precision. The present pass and
// the identity pass interpret the F16 contents as sRGB-coded (same as
// G6.1) so cross-test scores stay byte-equivalent.
//
// Option (a) of the G6.2 task description (linearise at shader output,
// blend in linear) was tried first and dropped : the resulting linear
// blending diverges substantially from the cross-test reference (which
// is encoded-Rec.2020 blended via F16 raster). Option (b) -- this one
// -- keeps the encoded blending math intact while still benefiting
// from F16 precision in the intermediate.
//
// ASCII strict -- WGSL parser truncates on non-ASCII in wgpu4k 0.2.0.

// G3.1.1 -- extended uniform with optional inner-rect cutout for AA
// stroke / AA hairline rasterization. Coverage at each pixel is
//   outer_cov - inner_cov   (clamped to [0, 1])
// which matches SkBitmapDevice.strokeRectAA's annular formulation.
//
// Fill rects pass `innerBounds` as a degenerate (l > r) rect ; the
// shader's clamp(min - max, 0, 1) naturally collapses inner_cov to 0
// and the result equals the prior fill-only output.
//
// G2.x clip-shape -- the trailing two vec4 slots carry an optional
// analytical "simple shape" clip captured from the SkCanvas's clip
// stack. When `clipShapeRadiiKind.z` (clipKind) is 0, the clip is a
// plain rect and no extra modulation happens (existing behaviour).
// When clipKind is 1 (rrect, which subsumes oval / circle), each
// fragment's coverage is multiplied by the analytic rrect coverage so
// pixels outside the clip shape get 0, fully inside get 1, and the
// quarter-circle corner band gets a smooth analytical edge.
//
// Phase G-direct-colorFilter -- the 6 trailing vec4 slots carry an
// optional SkColorFilter applied to the unpremul source colour BEFORE
// the per-pixel premul + coverage modulation. Same packing as
// `layer_composite.wgsl` from #568, so the host-side packer is shared.
//
//   colorFilterKindMode.x == 0 : no-op (default ; fast path).
//   colorFilterKindMode.x == 1 : SkColorFilters.Blend(colour, mode).
//                                `colorFilterParam0` carries the premul
//                                constant src colour ; `kindMode.y` is
//                                the SkBlendMode ordinal. Supported
//                                blend modes match `blendPremul` in
//                                SkColorFilters.kt (15 simple modes ;
//                                separable / HSL fall through to src).
//   colorFilterKindMode.x == 2 : SkColorFilters.Matrix(20 floats).
//                                Rows packed as Param0..3 (vec4f each)
//                                and per-row bias in `colorFilterBias`.
struct Uniforms {
    color:               vec4f,   // offset  0 : unpremul ARGB
    outerBounds:         vec4f,   // offset 16 : (l, t, r, b) in device pixels
    innerBounds:         vec4f,   // offset 32 : (l, t, r, b) ; degenerate for fill
    clipShapeBounds:     vec4f,   // offset 48 : (l, t, r, b) device-px ; ignored when clipKind = 0
    clipShapeRadiiKind:  vec4f,   // offset 64 : (rx, ry, clipKind, _) ; clipKind in {0, 1}
    colorFilterKindMode: vec4f,   // offset 80 : (kind, blendMode, _, _) ; kind 0 = none
    colorFilterParam0:   vec4f,   // offset 96 : kind 1 -> premul colour ; kind 2 -> matrix row 0
    colorFilterParam1:   vec4f,   // offset 112: matrix row 1 (G coefs)
    colorFilterParam2:   vec4f,   // offset 128: matrix row 2 (B coefs)
    colorFilterParam3:   vec4f,   // offset 144: matrix row 3 (A coefs)
    colorFilterBias:     vec4f,   // offset 160: per-row bias (R, G, B, A)
};

@binding(0) @group(0) var<uniform> uniforms: Uniforms;

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

// Analytic coverage of an axis-aligned rounded rect at fragment center
// `p`. The rrect is the bbox `[l, t]..[r, b]` with uniform corner
// radii `(rx, ry)`. Returns 1.0 deep inside the rrect, 0.0 outside,
// and a smooth half-pixel falloff at the boundary.
//
// Implementation. Fold `p` into the first quadrant relative to the
// rect centre (`q_abs = abs(p - centre)`). Compute `q = q_abs - (half
// - (rx, ry))`, the offset from the nearest inner-corner ellipse
// centre :
//   - `q.x <= 0` AND `q.y <= 0` : inside the central + straight bands.
//     Signed distance to the rrect boundary is the standard rect SDF
//     `max(q_abs - half, 0)`-based formula -- negative everywhere in
//     this region.
//   - At least one of `q.x > 0`, `q.y > 0` : the corner band (or
//     outside the bbox).
//     - If both > 0 : in the quarter-disk corner region. The ellipse
//       SDF approximation `(length((q.x/rx, q.y/ry)) - 1) *
//       effective_r` gives signed pixel distance to the curved edge.
//     - If only one > 0 : outside on that axis. Use the per-axis
//       distance to the corresponding outer edge as SDF.
//
// We combine these branches into a single non-branching expression :
//   - For the straight-band region, both `q` components are <= 0, so
//     `max(q.x, q.y) <= 0` is the standard inner-rect SDF.
//   - For the corner region, use `max(corner_sdf, max(q.x - rx, q.y -
//     ry))` -- the second term is the distance to the outer edge along
//     the dominant axis (negative inside the bbox, positive outside).
//
// Final SDF = `max` of (inner-rect SDF) and (corner-region SDF). This
// degenerates correctly :
//   - Inside inner rect, both are negative ; we pick the smaller-in-
//     magnitude one (closest boundary).
//   - In corner band, inner-rect SDF is positive (>= 0) so it loses
//     to the corner-region SDF unless the latter is even more positive
//     (i.e. outside the curved boundary).
//   - Outside bbox, both are positive ; the larger wins.
fn rrect_cov(p: vec2f, bounds: vec4f, rx_in: f32, ry_in: f32) -> f32 {
    let centre = vec2f(0.5 * (bounds.x + bounds.z), 0.5 * (bounds.y + bounds.w));
    let half = vec2f(0.5 * (bounds.z - bounds.x), 0.5 * (bounds.w - bounds.y));
    let rx = max(rx_in, 1e-4);
    let ry = max(ry_in, 1e-4);
    let q_abs = abs(p - centre);
    // Inner-rect SDF (relative to the rect `(centre - (half - (rx,
    // ry)))..(centre + (half - (rx, ry)))`). Negative inside the inner
    // rect, positive when at least one axis is past the corresponding
    // inner-corner-ellipse origin.
    let q = q_abs - (half - vec2f(rx, ry));
    let inner_rect_sdf = max(q.x, q.y);
    // Outer-rect SDF (relative to the bbox). Negative inside bbox,
    // positive outside.
    let outer_rect_sdf = max(q_abs.x - half.x, q_abs.y - half.y);
    // Corner ellipse SDF approximation -- only meaningful in the
    // corner region (both `q.x > 0` AND `q.y > 0`). For points outside
    // the corner region we fall back to `outer_rect_sdf` via the
    // `min(inner_rect_sdf, ...)` step below.
    let qm = max(q, vec2f(0.0, 0.0));
    let n = vec2f(qm.x / rx, qm.y / ry);
    let nl = length(n);
    let nl_safe = max(nl, 1e-6);
    let dir = n / nl_safe;
    let effective_r = length(vec2f(rx * dir.x, ry * dir.y));
    let corner_sdf = (nl - 1.0) * effective_r;
    // When both `q.x > 0` AND `q.y > 0`, we're in the corner band ;
    // use `corner_sdf`. Otherwise we're either in the inner rect or
    // in a straight-band-only region -- use `outer_rect_sdf` which
    // collapses to the perpendicular distance to the outer edge.
    let in_corner_band = step(0.0, q.x) * step(0.0, q.y);
    let band_sdf = mix(outer_rect_sdf, corner_sdf, in_corner_band);
    let final_sdf = band_sdf;
    return clamp(0.5 - final_sdf, 0.0, 1.0);
}

// Phase G-direct-colorFilter -- pure-float blend on premul RGBA. Same
// dispatch table as `layer_composite.wgsl`'s `blend_premul`. Inputs are
// premul ; output is premul. Unsupported modes (separable / HSL) fall
// through to identity src ; host-side packer leaves `kind = 0` for
// unsupported variants, so this branch is defensive only.
fn blend_premul(s: vec4f, d: vec4f, mode: u32) -> vec4f {
    // Enum ordinals match SkBlendMode declaration order :
    //   0 kClear,  1 kSrc,   2 kDst,    3 kSrcOver, 4 kDstOver,
    //   5 kSrcIn,  6 kDstIn, 7 kSrcOut, 8 kDstOut,  9 kSrcATop,
    //  10 kDstATop, 11 kXor, 12 kPlus, 13 kModulate, 14 kScreen.
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

// Phase G-direct-colorFilter -- apply the optional SkColorFilter to an
// unpremul RGBA source colour. Returns the filtered unpremul RGBA.
// `kind == 0` is a no-op fast path (identical to the pre-slice output).
fn apply_color_filter(c_un: vec4f) -> vec4f {
    let kind = u32(uniforms.colorFilterKindMode.x + 0.5);
    if (kind == 1u) {
        // SkBlendModeFilter : `param0` is the premul constant src ; we
        // premul the input dst, run blend_premul, then unpremul before
        // returning so the call site can reuse the coverage * premul
        // pipeline already in place.
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
        // SkMatrixFilter : Param0..3 are the 4 rows of coefficients ;
        // bias is the additive 5th column. Input is unpremul RGBA in
        // `[0, 1]` ; output is clamped to `[0, 1]` before re-premul at
        // the call site (matches SkMatrixColorFilter's storage-edge
        // clamp).
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

@fragment
fn fs_main(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    // pos.xy is the pixel center : column `p` has center at `p + 0.5`.
    // Intersection length per axis between the pixel [p, p+1] and the
    // rect edges, clamped to [0, 1]. Outer coverage = full fill area ;
    // inner coverage = the area to subtract (for stroke / hairline).
    // For fill rects the inner bounds are degenerate so inner_cov = 0
    // and the result equals the prior fill output.
    let ob = uniforms.outerBounds;
    let outer_cov_x = clamp(min(pos.x + 0.5, ob.z) - max(pos.x - 0.5, ob.x), 0.0, 1.0);
    let outer_cov_y = clamp(min(pos.y + 0.5, ob.w) - max(pos.y - 0.5, ob.y), 0.0, 1.0);

    let ib = uniforms.innerBounds;
    let inner_cov_x = clamp(min(pos.x + 0.5, ib.z) - max(pos.x - 0.5, ib.x), 0.0, 1.0);
    let inner_cov_y = clamp(min(pos.y + 0.5, ib.w) - max(pos.y - 0.5, ib.y), 0.0, 1.0);

    var coverage = max(0.0, outer_cov_x * outer_cov_y - inner_cov_x * inner_cov_y);

    // G2.x -- clip-shape coverage. clipKind == 0 means "no shape clip"
    // (legacy rect-only) ; clipKind == 1 means "rrect-style shape" with
    // bounds + uniform radii (intersect) ; clipKind == 2 (M4) is the
    // same rrect parameterisation but the shape carves a hole -- the
    // shader uses `1 - rrect_cov` so `clipRect(_, kDifference)` and
    // `clipRRect(_, kDifference)` get pixel-correct coverage without a
    // path-mask texture. Rect-difference encodes as kind 2 with
    // rx = ry = 0 (the rect SDF collapse).
    let clip_kind = i32(uniforms.clipShapeRadiiKind.z + 0.5);
    if (clip_kind == 1) {
        let clip_cov = rrect_cov(
            pos.xy,
            uniforms.clipShapeBounds,
            uniforms.clipShapeRadiiKind.x,
            uniforms.clipShapeRadiiKind.y,
        );
        coverage = coverage * clip_cov;
    } else if (clip_kind == 2) {
        let clip_cov = rrect_cov(
            pos.xy,
            uniforms.clipShapeBounds,
            uniforms.clipShapeRadiiKind.x,
            uniforms.clipShapeRadiiKind.y,
        );
        coverage = coverage * (1.0 - clip_cov);
    }

    // Premul output for SrcOver pipeline (src=One, dst=OneMinusSrcAlpha).
    // Coverage folds into alpha : a partially-covered translucent pixel
    // contributes less than a fully-covered one, in the standard
    // premul-correct way.
    //
    // Phase G-direct-colorFilter -- the source colour is filtered first
    // (unpremul -> filter -> unpremul output), then premultiplied with
    // coverage. `apply_color_filter` is a no-op when no filter is set
    // (kind == 0), so existing tests remain bit-identical.
    let c = apply_color_filter(uniforms.color);
    let a = c.a * coverage;
    return vec4f(c.rgb * a, a);
}
