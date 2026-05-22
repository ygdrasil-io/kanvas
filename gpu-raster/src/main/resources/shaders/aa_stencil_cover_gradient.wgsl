// G4.1.2 -- AA stencil-and-cover cover-pass for non-rect paths filled
// with a linear gradient. Same machinery as `aa_stencil_cover.wgsl`
// (per-fragment minimum-distance to nearest edge segment, two-pass
// inside / outside cover that integrates to the full AA profile at
// the path boundary), but the fragment output is sampled from the
// (start, end, stops) gradient table instead of a uniform color.
//
// One entry point per (CoverageSide x SkTileMode) pair :
//   fs_inside_clamp   / fs_outside_clamp
//   fs_inside_repeat  / fs_outside_repeat
//   fs_inside_mirror  / fs_outside_mirror
//   fs_inside_decal   / fs_outside_decal
//
// Tile-mode formulas mirror `linear_gradient.wgsl` byte-for-byte. The
// stop sampling helper is also copied verbatim. The only diff vs the
// rect-only `linear_gradient.wgsl` is the AA coverage modulation : the
// premul colour the stops produce is scaled by `coverage` before being
// written, with `coverage` defined as in `aa_stencil_cover.wgsl` :
//   inside  : coverage = clamp(minDist + 0.5, 0, 1)   in [0.5, 1.0]
//   outside : coverage = clamp(0.5 - minDist, 0, 1)   in [0.0, 0.5]
// Across the half-pixel boundary band each fragment is gated to exactly
// one of the two pipelines (stencil compare-op flips between them), so
// the two sub-draws sum to the full AA falloff without double-painting.
//
// Uniform-layout note : the first two vec4 slots (`color` + `viewport`)
// match `solid_polygon.wgsl` / `aa_stencil_cover.wgsl` byte-for-byte so
// the stencil-write pass (which uses `solid_polygon.wgsl` and reads
// `viewport` for the NDC remap) can share this draw's bind group.
// `color` itself is unused by the gradient fragment entries but kept
// in the slot to preserve the layout.
//
// G6.2 -- intermediate target is `RGBA16Float` ; output convention is
// unchanged (premul sRGB-coded). F16 gives sub-byte precision in the
// intermediate ; no colorspace switch.
//
// ASCII strict -- WGSL parser truncates on non-ASCII in wgpu4k 0.2.0.

struct Uniforms {
    color:        vec4f,                       // offset    0 : unused, kept for layout-compat
    viewport:     vec4f,                       // offset   16 : (w, h, 0, 0)
    startEnd:     vec4f,                       // offset   32 : (sx, sy, ex, ey)
    countPad:     vec4f,                       // offset   48 : .x = stopCount as bit-reinterp f32
    edgeCountPad: vec4f,                       // offset   64 : .x = edgeCount as bit-reinterp f32
    edges:        array<vec4f, 256>,           // offset   80 : (Ax, Ay, Bx, By) per edge
    positions:    array<vec4f, 16>,            // offset 4176 : (pos, _, _, _) per stop
    colors:       array<vec4f, 16>,            // offset 4432 : premul (r, g, b, a) per stop
    // G2.x clip-shape -- trailing two vec4 slots carry the optional
    // analytical clip (mirror of `solid_color.wgsl`).
    clipShapeBounds:    vec4f,                 // offset 4688 : (l, t, r, b) device-px
    clipShapeRadiiKind: vec4f,                 // offset 4704 : (rx, ry, clipKind, _)
    // Phase G-direct-colorFilter-gradient -- same layout as #569.
    colorFilterKindMode: vec4f,                // offset 4720 : (kind, blendMode, _, _)
    colorFilterParam0:   vec4f,                // offset 4736
    colorFilterParam1:   vec4f,                // offset 4752
    colorFilterParam2:   vec4f,                // offset 4768
    colorFilterParam3:   vec4f,                // offset 4784
    colorFilterBias:     vec4f,                // offset 4800
};

@binding(0) @group(0) var<uniform> uniforms: Uniforms;

@vertex
fn vs_main(@location(0) pos: vec2f) -> @builtin(position) vec4f {
    let ndc_x =  pos.x / uniforms.viewport.x * 2.0 - 1.0;
    let ndc_y = -(pos.y / uniforms.viewport.y * 2.0 - 1.0);
    return vec4f(ndc_x, ndc_y, 0.0, 1.0);
}

fn minSegmentDistance(p: vec2f) -> f32 {
    let edgeCount = bitcast<u32>(uniforms.edgeCountPad.x);
    var minDist: f32 = 1.0e9;
    for (var i: u32 = 0u; i < edgeCount; i = i + 1u) {
        let e = uniforms.edges[i];
        let ea = e.xy;
        let eb = e.zw;
        let ab = eb - ea;
        let ap = p - ea;
        let len2 = max(dot(ab, ab), 1.0e-9);
        let t = clamp(dot(ap, ab) / len2, 0.0, 1.0);
        let closest = ea + t * ab;
        let d = length(p - closest);
        minDist = min(minDist, d);
    }
    return minDist;
}

fn compute_t_raw(pos: vec2f) -> f32 {
    let start = uniforms.startEnd.xy;
    let end = uniforms.startEnd.zw;
    let dir = end - start;
    let lenSq = dot(dir, dir);
    if (lenSq < 1.0e-12) {
        return -1.0e30;
    }
    return dot(pos - start, dir) / lenSq;
}

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

// Tile-mode mapping helpers : same formulas as `linear_gradient.wgsl`.

fn map_clamp(t_raw: f32) -> f32 {
    return clamp(t_raw, 0.0, 1.0);
}

fn map_repeat(t_raw: f32) -> f32 {
    return t_raw - floor(t_raw);
}

fn map_mirror(t_raw: f32) -> f32 {
    let u = t_raw * 0.5;
    let w = u - floor(u);
    return select(2.0 - w * 2.0, w * 2.0, w < 0.5);
}

// Decal sentinel : t_raw outside [0, 1] -> output transparent regardless
// of stops. Returning a negative t and post-checking in the caller would
// also work, but a sentinel keeps the branch explicit at the call site.
fn decal_out_of_range(t_raw: f32) -> bool {
    return t_raw < 0.0 || t_raw > 1.0;
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

fn clip_cov(p: vec2f) -> f32 {
    let clip_kind = i32(uniforms.clipShapeRadiiKind.z + 0.5);
    if (clip_kind == 1) {
        return rrect_cov(
            p,
            uniforms.clipShapeBounds,
            uniforms.clipShapeRadiiKind.x,
            uniforms.clipShapeRadiiKind.y,
        );
    } else if (clip_kind == 2) {
        // M4 -- kDifference : invert the rrect coverage so the shape
        // carves a hole instead of restricting to its inside.
        return 1.0 - rrect_cov(
            p,
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

// ----- inside cover (coverage in [0.5, 1.0]) -----

@fragment
fn fs_inside_clamp(@builtin(position) frag: vec4f) -> @location(0) vec4f {
    let minDist = minSegmentDistance(frag.xy);
    let coverage = clamp(minDist + 0.5, 0.0, 1.0) * clip_cov(frag.xy);
    let t_raw = compute_t_raw(frag.xy);
    let c = apply_color_filter_premul(sample_stops_at(map_clamp(t_raw)));
    return vec4f(c.rgb * coverage, c.a * coverage);
}

@fragment
fn fs_inside_repeat(@builtin(position) frag: vec4f) -> @location(0) vec4f {
    let minDist = minSegmentDistance(frag.xy);
    let coverage = clamp(minDist + 0.5, 0.0, 1.0) * clip_cov(frag.xy);
    let t_raw = compute_t_raw(frag.xy);
    let c = apply_color_filter_premul(sample_stops_at(map_repeat(t_raw)));
    return vec4f(c.rgb * coverage, c.a * coverage);
}

@fragment
fn fs_inside_mirror(@builtin(position) frag: vec4f) -> @location(0) vec4f {
    let minDist = minSegmentDistance(frag.xy);
    let coverage = clamp(minDist + 0.5, 0.0, 1.0) * clip_cov(frag.xy);
    let t_raw = compute_t_raw(frag.xy);
    let c = apply_color_filter_premul(sample_stops_at(map_mirror(t_raw)));
    return vec4f(c.rgb * coverage, c.a * coverage);
}

@fragment
fn fs_inside_decal(@builtin(position) frag: vec4f) -> @location(0) vec4f {
    let minDist = minSegmentDistance(frag.xy);
    let coverage = clamp(minDist + 0.5, 0.0, 1.0) * clip_cov(frag.xy);
    let t_raw = compute_t_raw(frag.xy);
    if (decal_out_of_range(t_raw)) {
        return vec4f(0.0, 0.0, 0.0, 0.0);
    }
    let c = apply_color_filter_premul(sample_stops_at(t_raw));
    return vec4f(c.rgb * coverage, c.a * coverage);
}

// ----- outside cover (coverage in [0.0, 0.5]) -----

@fragment
fn fs_outside_clamp(@builtin(position) frag: vec4f) -> @location(0) vec4f {
    let minDist = minSegmentDistance(frag.xy);
    let coverage = clamp(0.5 - minDist, 0.0, 1.0) * clip_cov(frag.xy);
    let t_raw = compute_t_raw(frag.xy);
    let c = apply_color_filter_premul(sample_stops_at(map_clamp(t_raw)));
    return vec4f(c.rgb * coverage, c.a * coverage);
}

@fragment
fn fs_outside_repeat(@builtin(position) frag: vec4f) -> @location(0) vec4f {
    let minDist = minSegmentDistance(frag.xy);
    let coverage = clamp(0.5 - minDist, 0.0, 1.0) * clip_cov(frag.xy);
    let t_raw = compute_t_raw(frag.xy);
    let c = apply_color_filter_premul(sample_stops_at(map_repeat(t_raw)));
    return vec4f(c.rgb * coverage, c.a * coverage);
}

@fragment
fn fs_outside_mirror(@builtin(position) frag: vec4f) -> @location(0) vec4f {
    let minDist = minSegmentDistance(frag.xy);
    let coverage = clamp(0.5 - minDist, 0.0, 1.0) * clip_cov(frag.xy);
    let t_raw = compute_t_raw(frag.xy);
    let c = apply_color_filter_premul(sample_stops_at(map_mirror(t_raw)));
    return vec4f(c.rgb * coverage, c.a * coverage);
}

@fragment
fn fs_outside_decal(@builtin(position) frag: vec4f) -> @location(0) vec4f {
    let minDist = minSegmentDistance(frag.xy);
    let coverage = clamp(0.5 - minDist, 0.0, 1.0) * clip_cov(frag.xy);
    let t_raw = compute_t_raw(frag.xy);
    if (decal_out_of_range(t_raw)) {
        return vec4f(0.0, 0.0, 0.0, 0.0);
    }
    let c = apply_color_filter_premul(sample_stops_at(t_raw));
    return vec4f(c.rgb * coverage, c.a * coverage);
}
