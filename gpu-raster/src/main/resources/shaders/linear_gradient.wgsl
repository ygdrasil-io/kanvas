// G4.1 -- linear gradient (clamp tile mode) for drawRect.
//
// Vertex stage : same full-screen Bjorke triangle as `solid_color.wgsl`.
// Pair with `setScissorRect(...)` clipped to the rect's bbox so pixels
// outside the rect are killed before reaching the fragment stage.
//
// Fragment stage : compute parametric t along the gradient line
// `start -> end` (both in device-pixel coords, already CTM-transformed
// at draw time). For each fragment, project onto the gradient line :
//   t = clamp(dot(p - start, dir) / dot(dir, dir), 0, 1)
// then walk the (positions, colors) table to find the two stops
// bracketing `t` and lerp in premultiplied sRGB byte-equivalent space.
//
// Clamp tile mode only -- the four-mode generalization (kRepeat,
// kMirror, kDecal) will land in follow-up G4.1.x slices ; the host
// throws today for non-kClamp shaders, so the shader assumes clamp.
//
// Colors are stored as PREMULTIPLIED sRGB-encoded vec4f (alpha already
// folded into RGB). The lerp is straight `(1-u)*A + u*B` in this
// representation -- correct for premul, matches `lookupStopF16` on the
// raster side without the round-trip via integer ARGB. The fragment
// output is also premultiplied (G2.1 convention), so the SrcOver blend
// state (G2.2) composites the result correctly.
//
// MAX_STOPS = 16 covers every gradient GM in scope (the deepest one
// today, FillrectGradientGM, has 6 stops). A larger cap can be added
// when a real GM exceeds it.
//
// ASCII strict -- WGSL parser truncates on non-ASCII in wgpu4k 0.2.0.

struct Uniforms {
    // start point (xy) + end point (zw), all in device-pixel coords.
    startEnd: vec4f,    // offset  0
    // viewport (xy used) ; kept symmetric with the other pipelines.
    viewport: vec4f,    // offset 16
    // stopCount in .x as bit-reinterpreted u32, rest padding.
    countPad: vec4f,    // offset 32
    // Stop positions in [0, 1]. Only first `count` entries are valid.
    // Padded to vec4 stride (16 bytes) for std140-ish alignment ; we
    // read .x of each slot in the shader.
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

@fragment
fn fs_main(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let start = uniforms.startEnd.xy;
    let end = uniforms.startEnd.zw;
    let dir = end - start;
    let lenSq = dot(dir, dir);
    let count = bitcast<u32>(uniforms.countPad.x);

    // Degenerate gradient (start == end) : collapse to first stop.
    if (lenSq < 1.0e-12) {
        return uniforms.colors[0];
    }

    // pos.xy is the pixel center : column `p` has center at `p + 0.5`.
    let t_raw = dot(pos.xy - start, dir) / lenSq;
    let t = clamp(t_raw, 0.0, 1.0);

    // Walk the positions table to find the bracketing pair. Skia does
    // a binary search ; up to 16 stops we just scan linearly to keep
    // the WGSL straightforward (the cost is negligible vs the lookup
    // itself).
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
    let c0 = uniforms.colors[lo];
    let c1 = uniforms.colors[hi];
    return inv * c0 + u * c1;
}
