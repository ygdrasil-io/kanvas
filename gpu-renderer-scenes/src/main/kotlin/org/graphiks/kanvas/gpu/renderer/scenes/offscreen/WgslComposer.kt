package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

object WgslComposer {

    private val vsMain: String = """
@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}
"""

    fun solidColorWgsl(): String = """
struct Uniforms { color: vec4f };

@group(0) @binding(0) var<uniform> uniforms: Uniforms;

$vsMain

@fragment
fn fs_main() -> @location(0) vec4f {
    return uniforms.color;
}
"""

    fun linearGradientWgsl(): String = """
struct Uniforms { start: vec4f, end: vec4f, startColor: vec4f, endColor: vec4f };

@group(0) @binding(0) var<uniform> uniforms: Uniforms;

$vsMain

@fragment
fn fs_main(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let dir = uniforms.end.xy - uniforms.start.xy;
    let lenSq = dot(dir, dir);
    var t = -1.0e30;
    if (lenSq >= 1.0e-12) {
        t = dot(pos.xy - uniforms.start.xy, dir) / lenSq;
    }
    t = clamp(t, 0.0, 1.0);
    return mix(uniforms.startColor, uniforms.endColor, t);
}
"""

    fun radialGradientWgsl(): String = """
struct Uniforms { center: vec4f, startColor: vec4f, endColor: vec4f };

@group(0) @binding(0) var<uniform> uniforms: Uniforms;

$vsMain

@fragment
fn fs_main(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let radius = uniforms.center.z;
    var t = -1.0e30;
    if (radius > 0.0) {
        t = length(pos.xy - uniforms.center.xy) / radius;
    }
    t = clamp(t, 0.0, 1.0);
    return mix(uniforms.startColor, uniforms.endColor, t);
}
"""

    fun sweepGradientWgsl(): String = """
const TWO_PI: f32 = 6.2831853071795864;

struct Uniforms { center: vec4f, angles: vec4f, startColor: vec4f, endColor: vec4f };

@group(0) @binding(0) var<uniform> uniforms: Uniforms;

$vsMain

@fragment
fn fs_main(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let d = pos.xy - uniforms.center.xy;
    var t = 0.0;
    if (d.x != 0.0 || d.y != 0.0) {
        let a = atan2(d.y, d.x);
        var u = a / TWO_PI;
        if (u < 0.0) { u = u + 1.0; }
        let sweep = uniforms.angles.y - uniforms.angles.x;
        if (sweep > 0.0) {
            t = (u - uniforms.angles.x / 360.0) * (360.0 / sweep);
        }
    }
    t = clamp(t, 0.0, 1.0);
    return mix(uniforms.startColor, uniforms.endColor, t);
}
"""
}
