// G4.4.4 -- conical (two-point) gradient, kStrip sub-case, drawRect
// dispatch.
//
// This shader covers the kStrip sub-case of `SkConicalGradient` :
// equal radii `r0 == r1` and distinct centres `c0 != c1`. Geometrically
// the gradient sweeps through a "tube" of constant radius, with the
// parameter t running along the strip's axis.
//
// CPU reference : `cpu-raster/.../SkConicalGradient.kt::computeT`,
// kStrip branch. After applying the precomputed `gradientMatrix`
// (which maps source space such that c0 -> (0, 0) and c1 -> (1, 0)),
// the formula is :
//   disc = fP0 - y*y       with fP0 = (r0 / centerX1)^2 where
//                          centerX1 = |c1 - c0| is the un-mapped centre
//                          distance ; matches upstream's
//                          `scaledR0 = fRadius1 / getCenterX1()` then
//                          `ctx->fP0 = scaledR0 * scaledR0` (see
//                          SkConicalGradient.cpp::appendGradientStages).
//                          The GPU pulls this value via getStripP0().
//   t    = x + sqrt(disc)  iff disc >= 0
// When `disc < 0` the pixel is outside the strip ; CPU returns
// `Float.NaN` -> caller paints transparent black under all tile modes
// (matches `mask_2pt_conical_nan` + `apply_vector_mask`).
//
// Per-pixel mapping device -> conical frame : the host composes
//   M = gradientMatrix * (CTM * localMatrix)^-1
// once and passes it as a 2x3 affine. We apply it to the pixel center
// `(px+0.5, py+0.5)` -- WGSL's @builtin(position) already gives us the
// half-pixel-offset coords in device space, no fudge needed.
//
// Vertex stage : full-screen Bjorke triangle, identical to the other
// gradient pipelines. Pair with `setScissorRect(...)` clipped to the
// rect's bbox so out-of-rect pixels are killed.
//
// Tile-mode formulas mirror the rest of the gradient family ; pixels
// outside the strip (disc < 0) short-circuit to transparent black
// before the tile-mode mapping is applied, so kRepeat / kMirror / kDecal
// all preserve the strip's transparent halo.
//
// `sample_stops_at` is copy-pasted from `conical_focal_gradient.wgsl` ;
// cross-shader helper extraction is deferred to a separate slice.
//
// MAX_STOPS = 16 mirrors the other gradient shaders.
//
// ASCII strict -- WGSL parser truncates on non-ASCII in wgpu4k 0.2.0.

struct Uniforms {
    // viewport (xy used) ; kept symmetric with the other pipelines.
    viewport: vec4f,        // offset  0
    // Forward affine `device -> conical frame` packed as
    //   row0 = (m00, m01, m02, _)  // x' = m00*x + m01*y + m02
    //   row1 = (m10, m11, m12, _)  // y' = m10*x + m11*y + m12
    affineRow0: vec4f,      // offset 16
    affineRow1: vec4f,      // offset 32
    // Strip scalars : (fP0 = (r0/centerX1)^2, _, count_bits, _)
    //   count_bits = u32 stop count bit-reinterpreted as f32.
    stripScalars: vec4f,    // offset 48
    // Stop positions in [0, 1]. Only first `count` entries are valid.
    positions: array<vec4f, 16>, // offset 64
    // Premul sRGB-encoded stop colors. Only first `count` entries valid.
    colors: array<vec4f, 16>,    // offset 64 + 256 = 320
    // G2.x clip-shape -- trailing two vec4 slots carry the optional
    // analytical clip (mirror of `solid_color.wgsl`).
    clipShapeBounds:    vec4f,   // offset 576 : (l, t, r, b) device-px
    clipShapeRadiiKind: vec4f,   // offset 592 : (rx, ry, clipKind, _)
};

@binding(0) @group(0) var<uniform> uniforms: Uniforms;

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

// Compute (t_raw, in_strip). `in_strip = 1.0` if disc >= 0, `0.0`
// otherwise. The fragment entry points multiply the colour by
// `in_strip` so out-of-strip pixels collapse to premul transparent
// black, matching CPU's `mask_2pt_conical_nan` semantics.
struct StripT {
    t: f32,
    in_strip: f32,
};

fn compute_t_raw(pos: vec4f) -> StripT {
    let px = pos.x;
    let py = pos.y;
    let fx = uniforms.affineRow0.x * px + uniforms.affineRow0.y * py + uniforms.affineRow0.z;
    let fy = uniforms.affineRow1.x * px + uniforms.affineRow1.y * py + uniforms.affineRow1.z;

    let fP0 = uniforms.stripScalars.x;
    let disc = fP0 - fy * fy;
    let in_strip = select(0.0, 1.0, disc >= 0.0);
    let t = fx + sqrt(max(disc, 0.0));
    var out: StripT;
    out.t = t;
    out.in_strip = in_strip;
    return out;
}

fn sample_stops_at(t: f32) -> vec4f {
    let count = bitcast<u32>(uniforms.stripScalars.z);
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
    }
    return 1.0;
}

@fragment
fn fs_clamp(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let s = compute_t_raw(pos);
    let t = clamp(s.t, 0.0, 1.0);
    return sample_stops_at(t) * s.in_strip * clip_cov(pos.xy);
}

@fragment
fn fs_repeat(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let s = compute_t_raw(pos);
    let t = s.t - floor(s.t);
    return sample_stops_at(t) * s.in_strip * clip_cov(pos.xy);
}

@fragment
fn fs_mirror(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let s = compute_t_raw(pos);
    let u = s.t * 0.5;
    let w = u - floor(u);
    let t = select(2.0 - w * 2.0, w * 2.0, w < 0.5);
    return sample_stops_at(t) * s.in_strip * clip_cov(pos.xy);
}

@fragment
fn fs_decal(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let s = compute_t_raw(pos);
    if (s.in_strip < 0.5 || s.t < 0.0 || s.t > 1.0) {
        return vec4f(0.0, 0.0, 0.0, 0.0);
    }
    return sample_stops_at(s.t) * clip_cov(pos.xy);
}
