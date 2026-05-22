// G4.1 / G4.1.1 -- linear gradient for drawRect, all 4 tile modes.
//
// Vertex stage : same full-screen Bjorke triangle as `solid_color.wgsl`.
// Pair with `setScissorRect(...)` clipped to the rect's bbox so pixels
// outside the rect are killed before reaching the fragment stage.
//
// Fragment stage : compute parametric t_raw along the gradient line
// `start -> end` (both in device-pixel coords, already CTM-transformed
// at draw time). For each fragment, project onto the gradient line :
//   t_raw = dot(p - start, dir) / dot(dir, dir)
// then map t_raw to t in [0, 1] per the tile mode (one entry point
// per mode -- see fs_clamp / fs_repeat / fs_mirror / fs_decal below),
// walk the (positions, colors) table to find the two stops bracketing
// t, and lerp in premultiplied sRGB byte-equivalent space.
//
// Tile mode formulas (mirror `lookupStop` / `lookupStopF16` in
// kanvas-skia/.../SkShader.kt) :
//   kClamp  : t = clamp(t_raw, 0, 1)
//   kRepeat : t = t_raw - floor(t_raw)            (always in [0, 1))
//   kMirror : let u = t_raw * 0.5 ;
//             let w = u - floor(u) ;              (in [0, 1))
//             t = if (w < 0.5) (w * 2) else (2 - w * 2)
//   kDecal  : if (t_raw outside [0, 1]) -> coverage = 0 (transparent)
//             else t = t_raw
//
// Colors are stored as PREMULTIPLIED sRGB-encoded vec4f (alpha already
// folded into RGB). The lerp is straight `(1-u)*A + u*B` in this
// representation -- correct for premul, matches `lookupStopF16` on the
// raster side without the round-trip via integer ARGB. The fragment
// output is also premultiplied (G2.1 convention), so the SrcOver blend
// state (G2.2) composites the result correctly.
//
// MAX_STOPS = 16 covers every gradient GM in scope (the deepest one
// today, FillrectGradientGM, has 6 stops). A larger cap can be added
// when a real GM exceeds it.
//
// G6.2 -- intermediate target is `RGBA16Float` ; output convention is
// unchanged (premul sRGB-coded, lerp on premul stops). F16 gives sub-
// byte precision on the lerp result and on subsequent blends ; no
// colorspace switch.
//
// ASCII strict -- WGSL parser truncates on non-ASCII in wgpu4k 0.2.0.

// G2.x clip-shape -- the trailing two vec4 slots carry an optional
// analytical "simple shape" clip captured from the SkCanvas's clip
// stack (mirror of `solid_color.wgsl`). When clipShapeRadiiKind.z
// (clipKind) is 0, no extra modulation happens. When clipKind is 1
// (rrect-style, subsumes oval / circle), each fragment's gradient
// output is multiplied by `rrect_cov(pos, clipShapeBounds, rx, ry)`.
//
// Phase G-direct-colorFilter-gradient -- the 6 trailing vec4 slots
// carry an optional SkColorFilter (kind = 0 -> identity / no-op ;
// kind = 1 -> SkColorFilters.Blend ; kind = 2 -> SkColorFilters.Matrix).
// Same layout / packer as `solid_color.wgsl` from #569 ; copied verbatim
// across all 9 gradient pipelines (WGSL has no preprocessor).
struct Uniforms {
    // start point (xy) + end point (zw), all in device-pixel coords.
    startEnd: vec4f,    // offset  0
    // viewport (xy used) ; kept symmetric with the other pipelines.
    viewport: vec4f,    // offset 16
    // stopCount in .x as bit-reinterpreted u32, rest padding.
    countPad: vec4f,    // offset 32
    // Stop positions in [0, 1]. Only first `count` entries are valid.
    // Padded to vec4 stride (16 bytes) for std140-ish alignment ; we
    // read .x of each slot in the shader.
    positions: array<vec4f, 16>, // offset 48
    // Stop colors as premul sRGB-encoded vec4f. Only first `count`
    // entries are valid.
    colors: array<vec4f, 16>,    // offset 48 + 256 = 304
    clipShapeBounds:    vec4f,   // offset 560 : (l, t, r, b) device-px
    clipShapeRadiiKind: vec4f,   // offset 576 : (rx, ry, clipKind, _)
    colorFilterKindMode: vec4f,  // offset 592 : (kind, blendMode, _, _)
    colorFilterParam0:   vec4f,  // offset 608 : kind 1 -> premul colour ; kind 2 -> matrix row 0
    colorFilterParam1:   vec4f,  // offset 624 : matrix row 1
    colorFilterParam2:   vec4f,  // offset 640 : matrix row 2
    colorFilterParam3:   vec4f,  // offset 656 : matrix row 3
    colorFilterBias:     vec4f,  // offset 672 : per-row bias (R, G, B, A)
};

@binding(0) @group(0) var<uniform> uniforms: Uniforms;

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

// Compute t_raw at the pixel center. Returns the raw projection along
// the gradient line ; tile-mode mapping happens in each fs_* entry.
// `pos.xy` is already the pixel center (`column p` -> `p + 0.5`).
fn compute_t_raw(pos: vec4f) -> f32 {
    let start = uniforms.startEnd.xy;
    let end = uniforms.startEnd.zw;
    let dir = end - start;
    let lenSq = dot(dir, dir);
    if (lenSq < 1.0e-12) {
        // Degenerate gradient (start == end) : sentinel < 0 so callers
        // collapse to the first stop via the tile-mode formula or via
        // the explicit early-out in sample_stops_at.
        return -1.0e30;
    }
    return dot(pos.xy - start, dir) / lenSq;
}

// Walk the (positions, colors) table and lerp at parametric `t` in
// [0, 1]. Linear scan up to count stops (Skia uses binary search ; with
// count <= 16 the linear scan is negligible vs the lookup itself).
fn sample_stops_at(t: f32) -> vec4f {
    let count = bitcast<u32>(uniforms.countPad.x);
    if (count <= 1u) {
        return uniforms.colors[0];
    }
    if (t <= uniforms.positions[0].x) {
        return uniforms.colors[0];
    }
    let lastIdx = count - 1u;
    if (t >= uniforms.positions[lastIdx].x) {
        return uniforms.colors[lastIdx];
    }
    var lo: u32 = 0u;
    for (var i: u32 = 1u; i < count; i = i + 1u) {
        if (uniforms.positions[i].x >= t) {
            lo = i - 1u;
            break;
        }
    }
    let hi = lo + 1u;
    let t0 = uniforms.positions[lo].x;
    let t1 = uniforms.positions[hi].x;
    let span = t1 - t0;
    let u = select((t - t0) / span, 0.0, span <= 0.0);
    let inv = 1.0 - u;
    return inv * uniforms.colors[lo] + u * uniforms.colors[hi];
}

// G2.x -- analytical rrect coverage at fragment center `p`. Mirror of
// `solid_color.wgsl::rrect_cov`. Returns 1.0 deep inside the rrect,
// 0.0 outside, smooth half-pixel falloff at the boundary.
fn rrect_cov(p: vec2f, bounds: vec4f, rx_in: f32, ry_in: f32) -> f32 {
    let centre = vec2f(0.5 * (bounds.x + bounds.z), 0.5 * (bounds.y + bounds.w));
    let half = vec2f(0.5 * (bounds.z - bounds.x), 0.5 * (bounds.w - bounds.y));
    let rx = max(rx_in, 1e-4);
    let ry = max(ry_in, 1e-4);
    let q_abs = abs(p - centre);
    let q = q_abs - (half - vec2f(rx, ry));
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
    return clamp(0.5 - band_sdf, 0.0, 1.0);
}

// G2.x -- clip-shape modulation. Returns 1.0 when clipKind == 0 (no
// shape clip ; integer scissor handles it), else the rrect coverage.
fn clip_cov(pos: vec2f) -> f32 {
    let clip_kind = i32(uniforms.clipShapeRadiiKind.z + 0.5);
    if (clip_kind == 1) {
        return rrect_cov(
            pos,
            uniforms.clipShapeBounds,
            uniforms.clipShapeRadiiKind.x,
            uniforms.clipShapeRadiiKind.y,
        );
    } else if (clip_kind == 2) {
        // M4 -- kDifference : invert the rrect coverage so the shape
        // carves a hole instead of restricting to its inside.
        return 1.0 - rrect_cov(
            pos,
            uniforms.clipShapeBounds,
            uniforms.clipShapeRadiiKind.x,
            uniforms.clipShapeRadiiKind.y,
        );
    }
    return 1.0;
}

// from #569 -- premul blend helper. Same dispatch table as
// `layer_composite.wgsl`'s `blend_premul` and `solid_color.wgsl`.
// Inputs are premul ; output is premul. Unsupported modes (separable /
// HSL) fall through to identity src ; host-side packer leaves
// `kind = 0` for unsupported variants, so this branch is defensive only.
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

// from #569 -- apply the optional SkColorFilter to the gradient's
// premul output. Returns premul. Fast path on kind == 0 (no filter).
fn apply_color_filter_premul(c_pre: vec4f) -> vec4f {
    let kind = u32(uniforms.colorFilterKindMode.x + 0.5);
    if (kind == 0u) { return c_pre; }
    if (kind == 1u) {
        // SkBlendModeFilter : `param0` is the premul constant src,
        // `c_pre` is the premul dst.
        let mode = u32(uniforms.colorFilterKindMode.y + 0.5);
        return blend_premul(uniforms.colorFilterParam0, c_pre, mode);
    }
    if (kind == 2u) {
        // SkMatrixFilter : unpremul input, apply matrix, re-premul.
        let a = c_pre.a;
        var c_un: vec4f;
        if (a <= 0.0) {
            c_un = vec4f(0.0, 0.0, 0.0, 0.0);
        } else {
            let inv = 1.0 / a;
            c_un = vec4f(c_pre.r * inv, c_pre.g * inv, c_pre.b * inv, a);
        }
        let out_r = dot(uniforms.colorFilterParam0, c_un) + uniforms.colorFilterBias.x;
        let out_g = dot(uniforms.colorFilterParam1, c_un) + uniforms.colorFilterBias.y;
        let out_b = dot(uniforms.colorFilterParam2, c_un) + uniforms.colorFilterBias.z;
        let out_a = dot(uniforms.colorFilterParam3, c_un) + uniforms.colorFilterBias.w;
        let cr = clamp(out_r, 0.0, 1.0);
        let cg = clamp(out_g, 0.0, 1.0);
        let cb = clamp(out_b, 0.0, 1.0);
        let ca = clamp(out_a, 0.0, 1.0);
        return vec4f(cr * ca, cg * ca, cb * ca, ca);
    }
    return c_pre;
}

@fragment
fn fs_clamp(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let t_raw = compute_t_raw(pos);
    let t = clamp(t_raw, 0.0, 1.0);
    let c = apply_color_filter_premul(sample_stops_at(t));
    return c * clip_cov(pos.xy);
}

@fragment
fn fs_repeat(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let t_raw = compute_t_raw(pos);
    // t = t_raw - floor(t_raw) is always in [0, 1) for any sign.
    let t = t_raw - floor(t_raw);
    let c = apply_color_filter_premul(sample_stops_at(t));
    return c * clip_cov(pos.xy);
}

@fragment
fn fs_mirror(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let t_raw = compute_t_raw(pos);
    // Mirror formula : let u = t_raw * 0.5, w = fract(u) in [0, 1).
    // t = if (w < 0.5) (w * 2) else (2 - w * 2) -- triangle wave with
    // period 2, range [0, 1], peaks at odd integer t_raw.
    let u = t_raw * 0.5;
    let w = u - floor(u);
    let t = select(2.0 - w * 2.0, w * 2.0, w < 0.5);
    let c = apply_color_filter_premul(sample_stops_at(t));
    return c * clip_cov(pos.xy);
}

@fragment
fn fs_decal(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let t_raw = compute_t_raw(pos);
    // Outside [0, 1] -> fully transparent (premul (0, 0, 0, 0)).
    // Inside -> straight t_raw, no clamp (it's already in range).
    if (t_raw < 0.0 || t_raw > 1.0) {
        return vec4f(0.0, 0.0, 0.0, 0.0);
    }
    let c = apply_color_filter_premul(sample_stops_at(t_raw));
    return c * clip_cov(pos.xy);
}
