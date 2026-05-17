// G4.2.2 -- AA stencil-and-cover cover-pass for non-rect paths filled
// with a radial gradient. Mirror of `aa_stencil_cover_gradient.wgsl`
// (linear-on-non-rect) : same per-fragment minimum-distance edge AA
// machinery, same two-pass inside / outside cover that integrates to
// the full AA profile at the path boundary, but the gradient is
// parameterised by `(center, radius)` instead of `(start, end)`.
//
// One entry point per (CoverageSide x SkTileMode) pair :
//   fs_inside_clamp   / fs_outside_clamp
//   fs_inside_repeat  / fs_outside_repeat
//   fs_inside_mirror  / fs_outside_mirror
//   fs_inside_decal   / fs_outside_decal
//
// `t_raw = length(p - center) / radius` (mirrors `radial_gradient.wgsl`
// from G4.2 / G4.2.1). Tile-mode mapping helpers are identical to the
// linear variant. `sample_stops_at` is copied from the linear shader
// rather than factored across shaders -- the G4.1.2 plan deferred any
// cross-shader helper extraction until more shader types arrive, and
// G4.2.2 follows the same convention.
//
// Coverage modulation matches `aa_stencil_cover_gradient.wgsl` :
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
    centerRadius: vec4f,                       // offset   32 : (cx, cy, radius, _)
    countPad:     vec4f,                       // offset   48 : .x = stopCount as bit-reinterp f32
    edgeCountPad: vec4f,                       // offset   64 : .x = edgeCount as bit-reinterp f32
    edges:        array<vec4f, 256>,           // offset   80 : (Ax, Ay, Bx, By) per edge
    positions:    array<vec4f, 16>,            // offset 4176 : (pos, _, _, _) per stop
    colors:       array<vec4f, 16>,            // offset 4432 : premul (r, g, b, a) per stop
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

fn compute_t_raw(p: vec2f) -> f32 {
    let center = uniforms.centerRadius.xy;
    let radius = uniforms.centerRadius.z;
    if (radius <= 0.0) {
        // Degenerate gradient : caller should never reach here (the
        // dispatch gate rejects radius <= 0) but stay safe.
        return -1.0e30;
    }
    let d = p - center;
    return length(d) / radius;
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

// Tile-mode mapping helpers : same formulas as `radial_gradient.wgsl`.

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
// of stops. For a radial gradient t_raw >= 0 (it's length / radius), so
// the lower bound is dead code in practice ; kept for symmetry with the
// linear variant.
fn decal_out_of_range(t_raw: f32) -> bool {
    return t_raw < 0.0 || t_raw > 1.0;
}

// ----- inside cover (coverage in [0.5, 1.0]) -----

@fragment
fn fs_inside_clamp(@builtin(position) frag: vec4f) -> @location(0) vec4f {
    let minDist = minSegmentDistance(frag.xy);
    let coverage = clamp(minDist + 0.5, 0.0, 1.0);
    let t_raw = compute_t_raw(frag.xy);
    let c = sample_stops_at(map_clamp(t_raw));
    return vec4f(c.rgb * coverage, c.a * coverage);
}

@fragment
fn fs_inside_repeat(@builtin(position) frag: vec4f) -> @location(0) vec4f {
    let minDist = minSegmentDistance(frag.xy);
    let coverage = clamp(minDist + 0.5, 0.0, 1.0);
    let t_raw = compute_t_raw(frag.xy);
    let c = sample_stops_at(map_repeat(t_raw));
    return vec4f(c.rgb * coverage, c.a * coverage);
}

@fragment
fn fs_inside_mirror(@builtin(position) frag: vec4f) -> @location(0) vec4f {
    let minDist = minSegmentDistance(frag.xy);
    let coverage = clamp(minDist + 0.5, 0.0, 1.0);
    let t_raw = compute_t_raw(frag.xy);
    let c = sample_stops_at(map_mirror(t_raw));
    return vec4f(c.rgb * coverage, c.a * coverage);
}

@fragment
fn fs_inside_decal(@builtin(position) frag: vec4f) -> @location(0) vec4f {
    let minDist = minSegmentDistance(frag.xy);
    let coverage = clamp(minDist + 0.5, 0.0, 1.0);
    let t_raw = compute_t_raw(frag.xy);
    if (decal_out_of_range(t_raw)) {
        return vec4f(0.0, 0.0, 0.0, 0.0);
    }
    let c = sample_stops_at(t_raw);
    return vec4f(c.rgb * coverage, c.a * coverage);
}

// ----- outside cover (coverage in [0.0, 0.5]) -----

@fragment
fn fs_outside_clamp(@builtin(position) frag: vec4f) -> @location(0) vec4f {
    let minDist = minSegmentDistance(frag.xy);
    let coverage = clamp(0.5 - minDist, 0.0, 1.0);
    let t_raw = compute_t_raw(frag.xy);
    let c = sample_stops_at(map_clamp(t_raw));
    return vec4f(c.rgb * coverage, c.a * coverage);
}

@fragment
fn fs_outside_repeat(@builtin(position) frag: vec4f) -> @location(0) vec4f {
    let minDist = minSegmentDistance(frag.xy);
    let coverage = clamp(0.5 - minDist, 0.0, 1.0);
    let t_raw = compute_t_raw(frag.xy);
    let c = sample_stops_at(map_repeat(t_raw));
    return vec4f(c.rgb * coverage, c.a * coverage);
}

@fragment
fn fs_outside_mirror(@builtin(position) frag: vec4f) -> @location(0) vec4f {
    let minDist = minSegmentDistance(frag.xy);
    let coverage = clamp(0.5 - minDist, 0.0, 1.0);
    let t_raw = compute_t_raw(frag.xy);
    let c = sample_stops_at(map_mirror(t_raw));
    return vec4f(c.rgb * coverage, c.a * coverage);
}

@fragment
fn fs_outside_decal(@builtin(position) frag: vec4f) -> @location(0) vec4f {
    let minDist = minSegmentDistance(frag.xy);
    let coverage = clamp(0.5 - minDist, 0.0, 1.0);
    let t_raw = compute_t_raw(frag.xy);
    if (decal_out_of_range(t_raw)) {
        return vec4f(0.0, 0.0, 0.0, 0.0);
    }
    let c = sample_stops_at(t_raw);
    return vec4f(c.rgb * coverage, c.a * coverage);
}
