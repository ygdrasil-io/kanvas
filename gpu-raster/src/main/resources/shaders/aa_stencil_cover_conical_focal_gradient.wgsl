// G4.4.3 / G4.4.5 -- AA stencil-and-cover cover-pass for non-rect paths
// filled with a conical (two-point) gradient, **focal-inside well-behaved**
// (focal point inside the end circle, `focalData.fR1 > 1`, not focal-
// on-circle ; G4.4.3) or **focal-outside** (`focalData.fR1 < 1`, not
// focal-on-circle ; G4.4.5) sub-case. Mirror of
// `aa_stencil_cover_conical_gradient.wgsl` (kRadial-on-non-rect) : same
// per-fragment minimum-distance edge AA machinery, same two-pass
// inside / outside cover. Only the gradient formula changes -- this
// shader uses the same focal formula as `conical_focal_gradient.wgsl`
// (rect-only G4.4.1 / G4.4.5) :
//   well-behaved : t = sqrt(x*x + y*y) - x * fP0
//   focal-outside: t = sign * sqrt(x*x - y*y) - x * fP0 (masked outside
//                  the cone via the `in_cone` factor)
// where `(x, y)` are the device-pixel coords mapped through the 2x3
// `device -> focal frame` affine, plus the per-draw post-passes
// `negate_x`, `compensate_focal`, `unswap`.
//
// One entry point per (CoverageSide x SkTileMode) pair :
//   fs_inside_clamp   / fs_outside_clamp
//   fs_inside_repeat  / fs_outside_repeat
//   fs_inside_mirror  / fs_outside_mirror
//   fs_inside_decal   / fs_outside_decal
//
// Uniform-layout note : the first two vec4 slots (`color` + `viewport`)
// match `solid_polygon.wgsl` / `aa_stencil_cover.wgsl` byte-for-byte so
// the stencil-write pass (which uses `solid_polygon.wgsl` and reads
// `viewport` for the NDC remap) can share this draw's bind group.
// `color` itself is unused by the gradient fragment entries but kept
// in the slot to preserve the layout.
//
// ASCII strict -- WGSL parser truncates on non-ASCII in wgpu4k 0.2.0.

struct Uniforms {
    color:        vec4f,                       // offset    0 : unused, kept for layout-compat
    viewport:     vec4f,                       // offset   16 : (w, h, 0, 0)
    affineRow0:   vec4f,                       // offset   32 : (m00, m01, m02, _)
    affineRow1:   vec4f,                       // offset   48 : (m10, m11, m12, _)
    focalScalars: vec4f,                       // offset   64 : (fP0, fP1, compensate, unswap)
    flagsCount:   vec4f,                       // offset   80 : (negateX, subCase, count_bits, subCaseSign)
    edgeCountPad: vec4f,                       // offset   96 : .x = edgeCount as bit-reinterp f32
    edges:        array<vec4f, 256>,           // offset  112 : (Ax, Ay, Bx, By) per edge
    positions:    array<vec4f, 16>,            // offset 4208 : (pos, _, _, _) per stop
    colors:       array<vec4f, 16>,            // offset 4464 : premul (r, g, b, a) per stop
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

// Compute t_raw at the pixel center. `in_cone = 1.0` iff the pixel is
// inside the conical region (always 1 for well-behaved ; 0 for focal-
// outside pixels with negative discriminant or non-positive raw t,
// mirroring CPU's `mask_2pt_conical_degenerates`). The fragment entry
// points multiply the sampled colour by `in_cone` after applying the
// AA coverage, so out-of-cone pixels collapse to premul transparent
// black under every tile mode -- matching the rect-only shader.
struct FocalT {
    t: f32,
    in_cone: f32,
};

fn compute_t_raw(p: vec2f) -> FocalT {
    let fx = uniforms.affineRow0.x * p.x + uniforms.affineRow0.y * p.y + uniforms.affineRow0.z;
    let fy = uniforms.affineRow1.x * p.x + uniforms.affineRow1.y * p.y + uniforms.affineRow1.z;

    let fP0 = uniforms.focalScalars.x;
    let fP1 = uniforms.focalScalars.y;
    let compensate = uniforms.focalScalars.z;
    let unswap = uniforms.focalScalars.w;
    let negateX = uniforms.flagsCount.x;
    let subCase = uniforms.flagsCount.y;
    let subCaseSign = uniforms.flagsCount.w;

    var t: f32;
    var in_cone: f32 = 1.0;

    if (subCase < 0.5) {
        // Well-behaved focal-inside : t = sqrt(x*x + y*y) - x * fP0.
        t = sqrt(fx * fx + fy * fy) - fx * fP0;
    } else {
        // Focal-outside greater / smaller : t = sign * sqrt(x*x - y*y) - x * fP0,
        // masked when (x*x - y*y) < 0 or raw t <= 0.
        let disc = fx * fx - fy * fy;
        let safeDisc = max(disc, 0.0);
        let raw = subCaseSign * sqrt(safeDisc) - fx * fP0;
        t = raw;
        let inside = (disc >= 0.0) && (raw > 0.0);
        in_cone = select(0.0, 1.0, inside);
    }

    if (negateX > 0.5) {
        t = -t;
    }
    if (compensate > 0.5) {
        t = t + fP1;
    }
    if (unswap > 0.5) {
        t = 1.0 - t;
    }
    var out: FocalT;
    out.t = t;
    out.in_cone = in_cone;
    return out;
}

fn sample_stops_at(t: f32) -> vec4f {
    let count = bitcast<u32>(uniforms.flagsCount.z);
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

fn decal_out_of_range(t_raw: f32) -> bool {
    return t_raw < 0.0 || t_raw > 1.0;
}

// ----- inside cover (coverage in [0.5, 1.0]) -----

@fragment
fn fs_inside_clamp(@builtin(position) frag: vec4f) -> @location(0) vec4f {
    let minDist = minSegmentDistance(frag.xy);
    let coverage = clamp(minDist + 0.5, 0.0, 1.0);
    let s = compute_t_raw(frag.xy);
    let c = sample_stops_at(map_clamp(s.t));
    return vec4f(c.rgb * coverage, c.a * coverage) * s.in_cone;
}

@fragment
fn fs_inside_repeat(@builtin(position) frag: vec4f) -> @location(0) vec4f {
    let minDist = minSegmentDistance(frag.xy);
    let coverage = clamp(minDist + 0.5, 0.0, 1.0);
    let s = compute_t_raw(frag.xy);
    let c = sample_stops_at(map_repeat(s.t));
    return vec4f(c.rgb * coverage, c.a * coverage) * s.in_cone;
}

@fragment
fn fs_inside_mirror(@builtin(position) frag: vec4f) -> @location(0) vec4f {
    let minDist = minSegmentDistance(frag.xy);
    let coverage = clamp(minDist + 0.5, 0.0, 1.0);
    let s = compute_t_raw(frag.xy);
    let c = sample_stops_at(map_mirror(s.t));
    return vec4f(c.rgb * coverage, c.a * coverage) * s.in_cone;
}

@fragment
fn fs_inside_decal(@builtin(position) frag: vec4f) -> @location(0) vec4f {
    let minDist = minSegmentDistance(frag.xy);
    let coverage = clamp(minDist + 0.5, 0.0, 1.0);
    let s = compute_t_raw(frag.xy);
    if (s.in_cone < 0.5 || decal_out_of_range(s.t)) {
        return vec4f(0.0, 0.0, 0.0, 0.0);
    }
    let c = sample_stops_at(s.t);
    return vec4f(c.rgb * coverage, c.a * coverage);
}

// ----- outside cover (coverage in [0.0, 0.5]) -----

@fragment
fn fs_outside_clamp(@builtin(position) frag: vec4f) -> @location(0) vec4f {
    let minDist = minSegmentDistance(frag.xy);
    let coverage = clamp(0.5 - minDist, 0.0, 1.0);
    let s = compute_t_raw(frag.xy);
    let c = sample_stops_at(map_clamp(s.t));
    return vec4f(c.rgb * coverage, c.a * coverage) * s.in_cone;
}

@fragment
fn fs_outside_repeat(@builtin(position) frag: vec4f) -> @location(0) vec4f {
    let minDist = minSegmentDistance(frag.xy);
    let coverage = clamp(0.5 - minDist, 0.0, 1.0);
    let s = compute_t_raw(frag.xy);
    let c = sample_stops_at(map_repeat(s.t));
    return vec4f(c.rgb * coverage, c.a * coverage) * s.in_cone;
}

@fragment
fn fs_outside_mirror(@builtin(position) frag: vec4f) -> @location(0) vec4f {
    let minDist = minSegmentDistance(frag.xy);
    let coverage = clamp(0.5 - minDist, 0.0, 1.0);
    let s = compute_t_raw(frag.xy);
    let c = sample_stops_at(map_mirror(s.t));
    return vec4f(c.rgb * coverage, c.a * coverage) * s.in_cone;
}

@fragment
fn fs_outside_decal(@builtin(position) frag: vec4f) -> @location(0) vec4f {
    let minDist = minSegmentDistance(frag.xy);
    let coverage = clamp(0.5 - minDist, 0.0, 1.0);
    let s = compute_t_raw(frag.xy);
    if (s.in_cone < 0.5 || decal_out_of_range(s.t)) {
        return vec4f(0.0, 0.0, 0.0, 0.0);
    }
    let c = sample_stops_at(s.t);
    return vec4f(c.rgb * coverage, c.a * coverage);
}
