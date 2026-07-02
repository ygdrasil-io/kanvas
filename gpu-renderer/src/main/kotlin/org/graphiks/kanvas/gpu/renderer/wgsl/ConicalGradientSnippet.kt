package org.graphiks.kanvas.gpu.renderer.wgsl

const val ConicalGradientWgsl: String = """
fn compute_t_conical(pos: vec4f, start: vec2f, end: vec2f, r1: f32, r2: f32) -> f32 {
    let dx = end.x - start.x;
    let dy = end.y - start.y;
    let fx = pos.x - start.x;
    let fy = pos.y - start.y;

    let A = dx*dx + dy*dy - (r2 - r1)*(r2 - r1);
    let B = 2.0 * (dx*fx + dy*fy + r1*(r2 - r1));
    let C = fx*fx + fy*fy - r1*r1;

    if (abs(A) < 1.0e-12) {
        if (abs(B) < 1.0e-12) {
            return 0.0;
        }
        return -C / B;
    }

    let disc = B*B - 4.0*A*C;
    if (disc < 0.0) {
        return 0.0;
    }

    let t = (-B + sqrt(disc)) / (2.0 * A);
    return t;
}

fn sample_stops_at(t: f32, count: u32, positions: ptr<function, array<vec4f, 16>>, colors: ptr<function, array<vec4f, 16>>) -> vec4f {
    if (count <= 1u) { return (*colors)[0]; }
    if (t <= (*positions)[0].x) { return (*colors)[0]; }
    let lastIdx = count - 1u;
    if (t >= (*positions)[lastIdx].x) { return (*colors)[lastIdx]; }
    var lo: u32 = 0u;
    for (var i: u32 = 1u; i < count; i = i + 1u) {
        if ((*positions)[i].x >= t) { lo = i - 1u; break; }
    }
    let hi = lo + 1u;
    let t0 = (*positions)[lo].x;
    let t1 = (*positions)[hi].x;
    let span = t1 - t0;
    let u = select((t - t0) / span, 0.0, span <= 0.0);
    return (1.0 - u) * (*colors)[lo] + u * (*colors)[hi];
}

fn tile_clamp(t: f32) -> f32 { return clamp(t, 0.0, 1.0); }
fn tile_repeat(t: f32) -> f32 { return t - floor(t); }
fn tile_mirror(t: f32) -> f32 {
    let i = floor(t);
    let f = t - i;
    let even = (i % 2.0) == 0.0;
    return select(1.0 - f, f, even);
}
fn tile_decal_alpha(t: f32) -> f32 {
    return select(0.0, 1.0, t >= 0.0 && t <= 1.0);
}

fn conical_gradient_clamp(pos: vec4f, start: vec2f, end: vec2f, r1: f32, r2: f32, count: u32, positions: ptr<function, array<vec4f, 16>>, colors: ptr<function, array<vec4f, 16>>) -> vec4f {
    let t_raw = compute_t_conical(pos, start, end, r1, r2);
    let t = tile_clamp(t_raw);
    return sample_stops_at(t, count, positions, colors);
}

fn conical_gradient_repeat(pos: vec4f, start: vec2f, end: vec2f, r1: f32, r2: f32, count: u32, positions: ptr<function, array<vec4f, 16>>, colors: ptr<function, array<vec4f, 16>>) -> vec4f {
    let t_raw = compute_t_conical(pos, start, end, r1, r2);
    let t = tile_repeat(t_raw);
    return sample_stops_at(t, count, positions, colors);
}

fn conical_gradient_mirror(pos: vec4f, start: vec2f, end: vec2f, r1: f32, r2: f32, count: u32, positions: ptr<function, array<vec4f, 16>>, colors: ptr<function, array<vec4f, 16>>) -> vec4f {
    let t_raw = compute_t_conical(pos, start, end, r1, r2);
    let t = tile_mirror(t_raw);
    return sample_stops_at(t, count, positions, colors);
}

fn conical_gradient_decal(pos: vec4f, start: vec2f, end: vec2f, r1: f32, r2: f32, count: u32, positions: ptr<function, array<vec4f, 16>>, colors: ptr<function, array<vec4f, 16>>) -> vec4f {
    let t_raw = compute_t_conical(pos, start, end, r1, r2);
    let t = tile_clamp(t_raw);
    let alpha = tile_decal_alpha(t_raw);
    let color = sample_stops_at(t, count, positions, colors);
    return vec4f(color.rgb, color.a * alpha);
}
"""

const val ConicalGradientSnippetSourceHash: String = "fragment:conical_gradient:v1"
const val ConicalGradientEntryPoint: String = "conical_gradient_clamp"
const val ConicalGradientRepeatEntryPoint: String = "conical_gradient_repeat"
const val ConicalGradientMirrorEntryPoint: String = "conical_gradient_mirror"
const val ConicalGradientDecalEntryPoint: String = "conical_gradient_decal"
