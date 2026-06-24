package org.graphiks.kanvas.gpu.renderer.wgsl

const val LinearGradientWgsl: String = """
fn compute_t_raw(pos: vec4f, start: vec2f, end: vec2f) -> f32 {
    let dir = end - start;
    let lenSq = dot(dir, dir);
    if (lenSq < 1.0e-12) {
        return -1.0e30;
    }
    return dot(pos.xy - start, dir) / lenSq;
}

fn sample_stops_at(t: f32, count: u32, positions: ptr<function, array<vec4f, 16>>, colors: ptr<function, array<vec4f, 16>>) -> vec4f {
    if (count <= 1u) {
        return (*colors)[0];
    }
    if (t <= (*positions)[0].x) {
        return (*colors)[0];
    }
    let lastIdx = count - 1u;
    if (t >= (*positions)[lastIdx].x) {
        return (*colors)[lastIdx];
    }
    var lo: u32 = 0u;
    for (var i: u32 = 1u; i < count; i = i + 1u) {
        if ((*positions)[i].x >= t) {
            lo = i - 1u;
            break;
        }
    }
    let hi = lo + 1u;
    let t0 = (*positions)[lo].x;
    let t1 = (*positions)[hi].x;
    let span = t1 - t0;
    let u = select((t - t0) / span, 0.0, span <= 0.0);
    let inv = 1.0 - u;
    return inv * (*colors)[lo] + u * (*colors)[hi];
}

fn linear_gradient_clamp(pos: vec4f, start: vec2f, end: vec2f, count: u32, positions: ptr<function, array<vec4f, 16>>, colors: ptr<function, array<vec4f, 16>>) -> vec4f {
    let t_raw = compute_t_raw(pos, start, end);
    let t = clamp(t_raw, 0.0, 1.0);
    return sample_stops_at(t, count, positions, colors);
}
"""

const val LinearGradientSnippetSourceHash: String = "fragment:linear_gradient:v1"
const val LinearGradientEntryPoint: String = "linear_gradient_clamp"
