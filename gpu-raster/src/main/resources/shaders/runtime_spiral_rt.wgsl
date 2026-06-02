// FOR-223 runtime-effect descriptor: SpiralRT.
struct Uniforms {
    rad_scale: f32,
    in_center: vec2f,
    in_colors0: vec4f,
    in_colors1: vec4f,
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
    let pp = pos.xy - uniforms.in_center;
    let radius = sqrt(length(pp));
    let angle = atan(pp.y / pp.x);
    var t = (angle + 3.1415926 / 2.0) / 3.1415926;
    t = fract(t + radius * uniforms.rad_scale);
    return uniforms.in_colors0 * (1.0 - t) + uniforms.in_colors1 * t;
}
