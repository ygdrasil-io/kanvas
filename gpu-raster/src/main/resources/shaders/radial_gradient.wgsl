// G4.2 / G4.2.1 -- radial gradient for drawRect, all 4 tile modes.
//
// Vertex stage : same full-screen Bjorke triangle as `linear_gradient.wgsl`.
// Pair with `setScissorRect(...)` clipped to the rect's bbox so pixels
// outside the rect are killed before reaching the fragment stage.
//
// Fragment stage : compute t_raw = length(p - center) / radius (with
// `center` and `radius` already CTM-transformed to device-pixel coords),
// map to t in [0, 1] per the tile mode, then sample the stops table the
// same way `linear_gradient.wgsl` does.
//
// Tile mode formulas (mirror `lookupStop` / `lookupStopF16` in
// kanvas-skia/.../SkShader.kt -- identical to `linear_gradient.wgsl`) :
//   kClamp  : t = clamp(t_raw, 0, 1)
//   kRepeat : t = t_raw - floor(t_raw)            (always in [0, 1))
//   kMirror : let u = t_raw * 0.5 ;
//             let w = u - floor(u) ;              (in [0, 1))
//             t = if (w < 0.5) (w * 2) else (2 - w * 2)
//   kDecal  : if (t_raw outside [0, 1]) -> coverage = 0 (transparent)
//             else t = t_raw
//
// Note : t_raw for a radial gradient is `length / radius` and so is
// always >= 0 ; the kRepeat / kMirror formulas above remain correct
// (floor(positive) is well-defined and matches the linear shader).
//
// `sample_stops_at` is copy-pasted from `linear_gradient.wgsl` rather
// than factored across shaders -- the G4.2 plan explicitly defers any
// cross-shader helper extraction until more shader types arrive.
//
// MAX_STOPS = 16 matches the linear shader so the host-side packing
// code can stay symmetric.
//
// G6.2 -- intermediate target is `RGBA16Float` ; output convention is
// unchanged (premul sRGB-coded). F16 gives sub-byte precision in the
// intermediate ; no colorspace switch.
//
// ASCII strict -- WGSL parser truncates on non-ASCII in wgpu4k 0.2.0.

struct Uniforms {
    // viewport (xy used) ; kept symmetric with the other pipelines.
    viewport: vec4f,    // offset  0
    // center (xy) in device-pixel coords + radius in .z + .w padding.
    centerRadius: vec4f, // offset 16
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

// Compute t_raw at the pixel center. `pos.xy` is the pixel center.
// Returns the raw distance ratio (length / radius) ; tile-mode mapping
// happens in each fs_* entry.
fn compute_t_raw(pos: vec4f) -> f32 {
    let center = uniforms.centerRadius.xy;
    let radius = uniforms.centerRadius.z;
    if (radius <= 0.0) {
        // Degenerate gradient : caller should never reach here (the
        // dispatch gate rejects radius <= 0) but stay safe.
        return -1.0e30;
    }
    let d = pos.xy - center;
    return length(d) / radius;
}

// Walk the (positions, colors) table and lerp at parametric `t` in
// [0, 1]. Same shape as `linear_gradient.wgsl::sample_stops_at`.
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
    // t = t_raw - floor(t_raw) is always in [0, 1) for any sign.
    let t = t_raw - floor(t_raw);
    return sample_stops_at(t);
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
    return sample_stops_at(t);
}

@fragment
fn fs_decal(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let t_raw = compute_t_raw(pos);
    // Outside [0, 1] -> fully transparent (premul (0, 0, 0, 0)).
    // Inside -> straight t_raw, no clamp (it's already in range).
    if (t_raw < 0.0 || t_raw > 1.0) {
        return vec4f(0.0, 0.0, 0.0, 0.0);
    }
    return sample_stops_at(t_raw);
}
