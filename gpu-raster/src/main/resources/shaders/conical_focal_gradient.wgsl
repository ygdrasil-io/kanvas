// G4.4.1 -- conical (two-point) gradient, focal-inside sub-case (well-behaved),
// kClamp tile mode, drawRect dispatch.
//
// This shader covers the kFocal sub-case of `SkConicalGradient` where
// the focal point is inside the end circle -- the most common general
// conical configuration. The host filters to the well-behaved variant
// (`focalData.fR1 > 1` and not focal-on-circle) ; the focal-on-circle /
// focal-outside cases fall through to solid color at the dispatch gate.
//
// CPU reference : `cpu-raster/.../SkConicalGradient.kt::computeTFocal`,
// well-behaved branch. After applying the precomputed `gradientMatrix`
// (which maps source space to the canonical focal frame where focal =
// (0, 0) and end center = (1, 0)), the well-behaved formula is :
//   t = sqrt(x*x + y*y) - x * fP0       with fP0 = 1 / fR1
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
    // Flags + stop count : (negateX, _, count_bits, _)
    //   negateX = 1.0 if (1 - fFocalX) < 0, 0.0 otherwise
    //   count_bits = u32 stop count bit-reinterpreted as f32
    flagsCount: vec4f,      // offset 64
    // Stop positions in [0, 1]. Only first `count` entries are valid.
    positions: array<vec4f, 16>, // offset 80
    // Premul sRGB-encoded stop colors. Only first `count` entries valid.
    colors: array<vec4f, 16>,    // offset 80 + 256 = 336
};

@binding(0) @group(0) var<uniform> uniforms: Uniforms;

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

// Compute t_raw at the pixel center. Apply the device -> focal-frame
// affine, then the well-behaved formula + post-passes.
fn compute_t_raw(pos: vec4f) -> f32 {
    let px = pos.x;
    let py = pos.y;
    let fx = uniforms.affineRow0.x * px + uniforms.affineRow0.y * py + uniforms.affineRow0.z;
    let fy = uniforms.affineRow1.x * px + uniforms.affineRow1.y * py + uniforms.affineRow1.z;

    let fP0 = uniforms.focalScalars.x;
    let fP1 = uniforms.focalScalars.y;
    let compensate = uniforms.focalScalars.z;
    let unswap = uniforms.focalScalars.w;
    let negateX = uniforms.flagsCount.x;

    // Well-behaved focal-inside : t = sqrt(x*x + y*y) - x * fP0.
    var t = sqrt(fx * fx + fy * fy) - fx * fP0;

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
    return t;
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

@fragment
fn fs_clamp(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let t_raw = compute_t_raw(pos);
    let t = clamp(t_raw, 0.0, 1.0);
    return sample_stops_at(t);
}

@fragment
fn fs_repeat(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let t_raw = compute_t_raw(pos);
    let t = t_raw - floor(t_raw);
    return sample_stops_at(t);
}

@fragment
fn fs_mirror(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let t_raw = compute_t_raw(pos);
    let u = t_raw * 0.5;
    let w = u - floor(u);
    let t = select(2.0 - w * 2.0, w * 2.0, w < 0.5);
    return sample_stops_at(t);
}

@fragment
fn fs_decal(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let t_raw = compute_t_raw(pos);
    if (t_raw < 0.0 || t_raw > 1.0) {
        return vec4f(0.0, 0.0, 0.0, 0.0);
    }
    return sample_stops_at(t_raw);
}
