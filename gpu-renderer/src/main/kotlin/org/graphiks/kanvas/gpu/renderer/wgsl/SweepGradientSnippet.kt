package org.graphiks.kanvas.gpu.renderer.wgsl

const val SweepGradientWgsl: String = """
const TWO_PI: f32 = 6.2831853071795864;

fn compute_t_sweep(pos: vec4f, center: vec2f, startAngle: f32, endAngle: f32) -> f32 {
    let d = pos.xy - center;
    if (d.x == 0.0 && d.y == 0.0) {
        return 0.0;
    }
    let a = atan2(d.y, d.x);
    var u = a / TWO_PI;
    if (u < 0.0) {
        u = u + 1.0;
    }
    let sweep = endAngle - startAngle;
    if (sweep <= 0.0) {
        return 0.0;
    }
    return (u - startAngle / 360.0) * (360.0 / sweep);
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

fn sweep_gradient_clamp(pos: vec4f, center: vec2f, startAngle: f32, endAngle: f32, count: u32, positions: ptr<function, array<vec4f, 16>>, colors: ptr<function, array<vec4f, 16>>) -> vec4f {
    let t_raw = compute_t_sweep(pos, center, startAngle, endAngle);
    let t = clamp(t_raw, 0.0, 1.0);
    return sample_stops_at(t, count, positions, colors);
}
"""

const val SweepGradientSnippetSourceHash: String = "fragment:sweep_gradient:v1"
const val SweepGradientEntryPoint: String = "sweep_gradient_clamp"
