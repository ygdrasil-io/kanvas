// G4.4 -- conical (two-point) gradient for drawRect, kClamp only.
//
// This shader covers the kRadial sub-case of `SkConicalGradient` :
// concentric circles `c0 == c1`. The gradient parameter is
//   t = (length(p - c1) - r0) / (r1 - r0)
// in device-pixel coords. Mirrors the CPU SkConicalGradient.kt::computeT
// kRadial branch (whose post-gradient-matrix form is
//   sqrt(x*x + y*y) * scale + bias
// with `scale = max(r0, r1) / (r1 - r0)` and `bias = -r0 / (r1 - r0)`
// after the gradientMatrix pre-scales the input by `1 / max(r0, r1)`).
// Folded back to device space we get the simple subtract-and-divide form
// above. The shallow-gradient cross-test
// (`shallow_gradient_conical_nodither`) hits this branch.
//
// Other conical sub-cases (kStrip, kFocal in any of its focal-on-circle /
// focal-outside / focal-inside variants) are filtered out at the host
// dispatch gate -- they fall through to the existing solid-color
// machinery until a G4.4.x follow-up adds them. The intended next slice
// is focal-inside (well-behaved), which is the most common kFocal case
// and what the brief's outline targets ; it needs the host to expose the
// `gradientMatrix` so the focal-frame coords can be computed.
//
// Vertex stage : same full-screen Bjorke triangle as the linear / radial
// / sweep gradient shaders. Pair with `setScissorRect(...)` clipped to
// the rect's bbox so pixels outside the rect are killed before reaching
// the fragment stage.
//
// Tile mode formulas (mirror `lookupStop` / `lookupStopF16` in
// kanvas-skia/.../SkShader.kt -- identical to the linear / radial /
// sweep shaders) :
//   kClamp  : t = clamp(t_raw, 0, 1)
//   kRepeat : t = t_raw - floor(t_raw)            (always in [0, 1))
//   kMirror : let u = t_raw * 0.5 ;
//             let w = u - floor(u) ;              (in [0, 1))
//             t = if (w < 0.5) (w * 2) else (2 - w * 2)
//   kDecal  : if (t_raw outside [0, 1]) -> coverage = 0 (transparent)
//             else t = t_raw
// G4.4.2 widened the host dispatch gate so all 4 entry points are
// reachable for the kRadial sub-case. (For kRadial the tile-mode
// formulas are well-defined ; the CPU SkConicalGradient.kRadial branch
// already routes through the same lookupStop entry.)
//
// `sample_stops_at` is copy-pasted from `radial_gradient.wgsl` rather
// than factored across shaders ; cross-shader helper extraction is
// deferred to a separate slice.
//
// MAX_STOPS = 16 matches the linear / radial / sweep shaders so the
// host-side packing code can stay symmetric.
//
// ASCII strict -- WGSL parser truncates on non-ASCII in wgpu4k 0.2.0.

// G2.x clip-shape -- trailing two vec4 slots carry the optional
// analytical clip (mirror of `solid_color.wgsl` / `linear_gradient.wgsl`).
struct Uniforms {
    // viewport (xy used) ; kept symmetric with the other pipelines.
    viewport: vec4f,    // offset  0
    // centerRadii : (c1.x, c1.y, r0, r1) in device-pixel coords.
    // c1 is the shared centre (kRadial sub-case has c0 == c1) ;
    // r0 = start radius, r1 = end radius (both scaled by ctm.getMaxScale()
    // at dispatch time, which collapses to max(|sx|, |sy|) under the
    // axis-aligned-CTM gate).
    centerRadii: vec4f, // offset 16
    // stopCount in .x as bit-reinterpreted u32, rest padding.
    countPad: vec4f,    // offset 32
    // Stop positions in [0, 1]. Only first `count` entries are valid.
    positions: array<vec4f, 16>, // offset 48
    // Stop colors as premul sRGB-encoded vec4f. Only first `count`
    // entries are valid.
    colors: array<vec4f, 16>,    // offset 48 + 256 = 304
    clipShapeBounds:    vec4f,   // offset 560 : (l, t, r, b) device-px
    clipShapeRadiiKind: vec4f,   // offset 576 : (rx, ry, clipKind, _)
    // Phase G-direct-colorFilter-gradient -- same layout as #569.
    colorFilterKindMode: vec4f,  // offset 592 : (kind, blendMode, _, _)
    colorFilterParam0:   vec4f,  // offset 608
    colorFilterParam1:   vec4f,  // offset 624
    colorFilterParam2:   vec4f,  // offset 640
    colorFilterParam3:   vec4f,  // offset 656
    colorFilterBias:     vec4f,  // offset 672
};

@binding(0) @group(0) var<uniform> uniforms: Uniforms;

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

// Compute t_raw at the pixel center. `pos.xy` is the pixel center.
// kRadial : t_raw = (length(p - c1) - r0) / (r1 - r0). Tile-mode mapping
// happens in each fs_* entry.
fn compute_t_raw(pos: vec4f) -> f32 {
    let c1 = uniforms.centerRadii.xy;
    let r0 = uniforms.centerRadii.z;
    let r1 = uniforms.centerRadii.w;
    let dR = r1 - r0;
    if (dR == 0.0) {
        // Degenerate (equal radii) ; host should have filtered this out
        // (SkConicalGradient.Make returns null for nearlyEqual radii
        // under equal centres).
        return -1.0e30;
    }
    let d = length(pos.xy - c1);
    return (d - r0) / dR;
}

// Walk the (positions, colors) table and lerp at parametric `t` in
// [0, 1]. Same shape as `radial_gradient.wgsl::sample_stops_at`.
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

// G2.x -- analytical rrect coverage (mirror of `solid_color.wgsl`).
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

// from #569 -- premul blend helper. Copied verbatim across all 9
// gradient shaders (WGSL has no preprocessor).
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

fn apply_color_filter_premul(c_pre: vec4f) -> vec4f {
    let kind = u32(uniforms.colorFilterKindMode.x + 0.5);
    if (kind == 0u) { return c_pre; }
    if (kind == 1u) {
        let mode = u32(uniforms.colorFilterKindMode.y + 0.5);
        return blend_premul(uniforms.colorFilterParam0, c_pre, mode);
    }
    if (kind == 2u) {
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
    let t = t_raw - floor(t_raw);
    let c = apply_color_filter_premul(sample_stops_at(t));
    return c * clip_cov(pos.xy);
}

@fragment
fn fs_mirror(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let t_raw = compute_t_raw(pos);
    let u = t_raw * 0.5;
    let w = u - floor(u);
    let t = select(2.0 - w * 2.0, w * 2.0, w < 0.5);
    let c = apply_color_filter_premul(sample_stops_at(t));
    return c * clip_cov(pos.xy);
}

@fragment
fn fs_decal(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let t_raw = compute_t_raw(pos);
    if (t_raw < 0.0 || t_raw > 1.0) {
        return vec4f(0.0, 0.0, 0.0, 0.0);
    }
    let c = apply_color_filter_premul(sample_stops_at(t_raw));
    return c * clip_cov(pos.xy);
}
