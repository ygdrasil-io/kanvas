// G4.3 -- sweep gradient for drawRect, all 4 tile modes wired but only
// kClamp is dispatched today (the other entry points exist for cache
// readiness, mirroring the linear / radial layout).
//
// Vertex stage : same full-screen Bjorke triangle as `linear_gradient.wgsl`
// and `radial_gradient.wgsl`. Pair with `setScissorRect(...)` clipped to
// the rect's bbox so pixels outside the rect are killed before reaching
// the fragment stage.
//
// Fragment stage : compute the swept-angle parametric t_raw from the
// pixel position relative to the centre (in device-pixel coords, already
// CTM-transformed at draw time) :
//   angle = atan2(p.y - cy, p.x - cx)        // in [-pi, pi]
//   u     = angle / (2 * pi)                  // in [-0.5, 0.5]
//   u_cw  = if (u < 0) u + 1 else u           // in [0, 1) -- CW from +X
//   t_raw = (u_cw - startTurn) * (1 / sweepTurn)
// where startTurn = startAngle / 360 and sweepTurn = (endAngle - startAngle)
// / 360 ; the host packs `tBias = -startTurn` and `tScale = 1 / sweepTurn`
// so per-pixel cost is one add + one mul. For the canonical full sweep
// (start = 0, end = 360) these collapse to tBias = 0, tScale = 1, so
// t_raw == u_cw and the fragment matches a CSS `conic-gradient` exactly.
//
// Image-space Y is down, so increasing angle goes CLOCKWISE on screen --
// matches `SkSweepGradient` and the upstream `xy_to_unit_angle` raster
// pipeline op (see `cpu-raster/.../SkSweepGradient.kt::unitAngle`).
//
// Tile mode formulas (mirror `lookupStop` / `lookupStopF16` in
// kanvas-skia/.../SkShader.kt -- identical to the linear / radial
// shaders) :
//   kClamp  : t = clamp(t_raw, 0, 1)
//   kRepeat : t = t_raw - floor(t_raw)            (always in [0, 1))
//   kMirror : let u = t_raw * 0.5 ;
//             let w = u - floor(u) ;              (in [0, 1))
//             t = if (w < 0.5) (w * 2) else (2 - w * 2)
//   kDecal  : if (t_raw outside [0, 1]) -> coverage = 0 (transparent)
//             else t = t_raw
//
// `sample_stops_at` is copy-pasted from `radial_gradient.wgsl` rather
// than factored across shaders -- cross-shader helper extraction is
// deferred to a separate slice.
//
// MAX_STOPS = 16 matches the linear / radial shaders so the host-side
// packing code can stay symmetric.
//
// G6.2 -- intermediate target is `RGBA16Float` ; output convention is
// unchanged (premul sRGB-coded). F16 gives sub-byte precision in the
// intermediate ; no colorspace switch.
//
// ASCII strict -- WGSL parser truncates on non-ASCII in wgpu4k 0.2.0.

struct Uniforms {
    // viewport (xy used) ; kept symmetric with the other pipelines.
    viewport: vec4f,    // offset  0
    // center (xy) in device-pixel coords + tBias / tScale in .z / .w.
    centerBiasScale: vec4f, // offset 16
    // stopCount in .x as bit-reinterpreted u32, rest padding.
    countPad: vec4f,    // offset 32
    // Stop positions in [0, 1]. Only first `count` entries are valid.
    positions: array<vec4f, 16>, // offset 48
    // Stop colors as premul sRGB-encoded vec4f. Only first `count`
    // entries are valid.
    colors: array<vec4f, 16>,    // offset 48 + 256
};

@binding(0) @group(0) var<uniform> uniforms: Uniforms;

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

// Two-pi as a literal so the WGSL parser doesn't need to fold a const
// expression involving 3.14159... .
const TWO_PI: f32 = 6.2831853071795864;

// Compute t_raw at the pixel center. `pos.xy` is the pixel center.
// Returns the raw swept-angle ratio in turns (CW from +X axis, scaled
// to [startAngle, endAngle] -> [0, 1]) ; tile-mode mapping happens in
// each fs_* entry. Singularity at center collapses to t_raw = tBias
// (matches the CPU `unitAngle(0, 0) -> 0` convention).
fn compute_t_raw(pos: vec4f) -> f32 {
    let center = uniforms.centerBiasScale.xy;
    let tBias = uniforms.centerBiasScale.z;
    let tScale = uniforms.centerBiasScale.w;
    let d = pos.xy - center;
    if (d.x == 0.0 && d.y == 0.0) {
        return tBias * tScale;
    }
    let a = atan2(d.y, d.x);
    var u = a / TWO_PI;
    if (u < 0.0) {
        u = u + 1.0;
    }
    return (u + tBias) * tScale;
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
