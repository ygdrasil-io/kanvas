// G4.4.1 / G4.4.5 / G4.4.6 -- conical (two-point) gradient, focal-
// inside + focal-outside + focal-on-circle sub-cases, drawRect dispatch.
//
// This shader covers three kFocal sub-cases of `SkConicalGradient` :
//   - **focal-inside well-behaved** (`focalData.fR1 > 1`,
//     not focal-on-circle) -- the most common general conical case ;
//     landed in G4.4.1.
//   - **focal-outside** (`focalData.fR1 < 1`, not focal-on-circle) --
//     the "smaller" / "greater" raster-pipeline variants ; landed in
//     G4.4.5. The host picks `+sqrt` (greater) vs `-sqrt` (smaller) via
//     the `subCaseSign` flag and the host gates `disc < 0` with the
//     `in_cone` factor.
//   - **focal-on-circle** (`|fR1 - 1| < tolerance`) -- the focal point
//     lies exactly on the end circle ; landed in G4.4.6. Formula
//     `t = (x*x + y*y) / x` (with a singularity at `x = 0` that maps to
//     premul transparent black, same `in_cone = 0` route as the focal-
//     outside mask). Also subject to `mask_2pt_conical_degenerates`
//     (t <= 0 or NaN -> in_cone = 0).
//
// CPU reference : `cpu-raster/.../SkConicalGradient.kt::computeTFocal`.
// After applying the precomputed `gradientMatrix` (which maps source
// space to the canonical focal frame where focal = (0, 0) and end
// center = (1, 0)), the per-sub-case formula is :
//   well-behaved    : t = sqrt(x*x + y*y) - x * fP0   with fP0 = 1 / fR1
//   focal-outside   : t = sign * sqrt(x*x - y*y) - x * fP0,
//                     masked to transparent when (x*x - y*y) < 0 OR t <= 0
//                     (`mask_2pt_conical_degenerates` ; sign = +1 for the
//                     "greater" variant, -1 for "smaller").
//   focal-on-circle : t = (x*x + y*y) / x,
//                     masked to transparent when abs(x) < eps (NaN guard)
//                     OR t <= 0 (`mask_2pt_conical_degenerates`).
// Then the CPU applies these post-passes :
//   - negate_x : t = -t                 iff (1 - fFocalX) < 0
//   - compensate_focal : t = t + fFocalX iff fFocalX != 0
//   - unswap : t = 1 - t                iff fIsSwapped
// All three are static (per-draw) so we fold the flags into the uniform.
//
// Per-pixel mapping device -> focal frame : the host composes
//   M = gradientMatrix * (CTM * localMatrix)^-1
// once and passes it as a 2x3 affine. We apply it to the pixel center
// `(px+0.5, py+0.5)` -- WGSL's @builtin(position) already gives us the
// half-pixel-offset coords in device space, no fudge needed.
//
// Vertex stage : full-screen Bjorke triangle, identical to the other
// gradient pipelines. Pair with `setScissorRect(...)` clipped to the
// rect's bbox so out-of-rect pixels are killed.
//
// Tile mode formulas mirror the rest of the gradient family
// (see `conical_gradient.wgsl` for the full table). G4.4.2 widened the
// host dispatch gate so all 4 entry points are reachable.
//
// `sample_stops_at` is copy-pasted from `conical_gradient.wgsl` /
// `radial_gradient.wgsl` ; cross-shader helper extraction is deferred to
// a separate slice.
//
// MAX_STOPS = 16 mirrors the other gradient shaders so the host packing
// code stays symmetric.
//
// ASCII strict -- WGSL parser truncates on non-ASCII in wgpu4k 0.2.0.

struct Uniforms {
    // viewport (xy used) ; kept symmetric with the other pipelines.
    viewport: vec4f,        // offset  0
    // Forward affine `device -> focal frame` packed as
    //   row0 = (m00, m01, m02, _)  // x' = m00*x + m01*y + m02
    //   row1 = (m10, m11, m12, _)  // y' = m10*x + m11*y + m12
    affineRow0: vec4f,      // offset 16
    affineRow1: vec4f,      // offset 32
    // Focal-frame scalars : (fP0, fP1, compensate, unswap)
    //   fP0       = 1 / fR1
    //   fP1       = fFocalX
    //   compensate = 1.0 if (fFocalX != 0), 0.0 otherwise
    //   unswap    = 1.0 if fIsSwapped, 0.0 otherwise
    focalScalars: vec4f,    // offset 48
    // Flags + stop count : (negateX, subCase, count_bits, subCaseSign)
    //   negateX     = 1.0 if (1 - fFocalX) < 0, 0.0 otherwise
    //   subCase     = 0.0 well-behaved, 1.0 focal-outside (greater /
    //                 smaller), 2.0 focal-on-circle.
    //   count_bits  = u32 stop count bit-reinterpreted as f32
    //   subCaseSign = +1.0 for "greater" (+sqrt), -1.0 for "smaller"
    //                 (-sqrt) ; only consulted when subCase == 1.0.
    flagsCount: vec4f,      // offset 64
    // Stop positions in [0, 1]. Only first `count` entries are valid.
    positions: array<vec4f, 16>, // offset 80
    // Premul sRGB-encoded stop colors. Only first `count` entries valid.
    colors: array<vec4f, 16>,    // offset 80 + 256 = 336
    // G2.x clip-shape -- trailing two vec4 slots carry the optional
    // analytical clip (mirror of `solid_color.wgsl` /
    // `linear_gradient.wgsl`).
    clipShapeBounds:    vec4f,   // offset 592 : (l, t, r, b) device-px
    clipShapeRadiiKind: vec4f,   // offset 608 : (rx, ry, clipKind, _)
};

@binding(0) @group(0) var<uniform> uniforms: Uniforms;

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

// Compute t_raw at the pixel center. `in_cone = 1.0` iff the pixel is
// inside the conical region (always 1 for well-behaved ; for
// focal-outside, set to 0 when the discriminant is negative OR when
// the raw `t` lands in the degenerate half-plane, mirroring the CPU's
// `mask_2pt_conical_degenerates`). The fragment entry points multiply
// the sampled colour by `in_cone` so out-of-cone pixels collapse to
// premul transparent black under every tile mode.
struct FocalT {
    t: f32,
    in_cone: f32,
};

fn compute_t_raw(pos: vec4f) -> FocalT {
    let px = pos.x;
    let py = pos.y;
    let fx = uniforms.affineRow0.x * px + uniforms.affineRow0.y * py + uniforms.affineRow0.z;
    let fy = uniforms.affineRow1.x * px + uniforms.affineRow1.y * py + uniforms.affineRow1.z;

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
    } else if (subCase < 1.5) {
        // Focal-outside greater / smaller :
        //   disc = x*x - y*y
        //   t    = subCaseSign * sqrt(max(disc, 0)) - x * fP0
        //   in_cone = 0 if disc < 0 or t <= 0 (mask_2pt_conical_degenerates)
        let disc = fx * fx - fy * fy;
        let safeDisc = max(disc, 0.0);
        let raw = subCaseSign * sqrt(safeDisc) - fx * fP0;
        t = raw;
        let inside = (disc >= 0.0) && (raw > 0.0);
        in_cone = select(0.0, 1.0, inside);
    } else {
        // Focal-on-circle : t = (x*x + y*y) / x.
        //   Singularity at x = 0 -> premul transparent black via in_cone = 0
        //   (matches `mask_2pt_conical_nan` in the CPU pipeline).
        //   Also subject to `mask_2pt_conical_degenerates` : t <= 0 or NaN
        //   -> in_cone = 0.
        let safeX = select(fx, 1.0, abs(fx) < 1.0e-6);
        let raw = (fx * fx + fy * fy) / safeX;
        t = raw;
        let xOk = abs(fx) >= 1.0e-6;
        let inside = xOk && (raw > 0.0);
        in_cone = select(0.0, 1.0, inside);
    }

    // Post-passes (matches SkConicalGradient.kt::computeTFocal) :
    //   negate_x       : t = -t           iff (1 - fFocalX) < 0
    //   compensateFocal: t = t + fFocalX  iff fFocalX != 0  (isNativelyFocal false)
    //   unswap         : t = 1 - t        iff fIsSwapped
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

// Walk the (positions, colors) table and lerp at parametric `t`.
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
    return sample_stops_at(t) * s.in_cone * clip_cov(pos.xy);
}

@fragment
fn fs_repeat(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let s = compute_t_raw(pos);
    let t = s.t - floor(s.t);
    return sample_stops_at(t) * s.in_cone * clip_cov(pos.xy);
}

@fragment
fn fs_mirror(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let s = compute_t_raw(pos);
    let u = s.t * 0.5;
    let w = u - floor(u);
    let t = select(2.0 - w * 2.0, w * 2.0, w < 0.5);
    return sample_stops_at(t) * s.in_cone * clip_cov(pos.xy);
}

@fragment
fn fs_decal(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let s = compute_t_raw(pos);
    if (s.in_cone < 0.5 || s.t < 0.0 || s.t > 1.0) {
        return vec4f(0.0, 0.0, 0.0, 0.0);
    }
    return sample_stops_at(s.t) * clip_cov(pos.xy);
}
