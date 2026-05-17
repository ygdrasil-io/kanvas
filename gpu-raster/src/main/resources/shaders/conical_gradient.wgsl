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
// Today only fs_clamp is dispatched by the host ; the other 3 entry
// points are kept in lockstep with the radial / sweep shaders so the
// G4.4.1 follow-up that widens the dispatch gate is a no-op on the
// shader side. (For the kRadial sub-case specifically the tile-mode
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
