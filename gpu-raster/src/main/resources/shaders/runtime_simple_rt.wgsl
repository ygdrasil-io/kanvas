// GRA-27 runtime-effect descriptor pilot: SimpleRT.
struct Uniforms {
    gColor: vec4f,
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
    return vec4f(pos.x * (1.0 / 255.0), pos.y * (1.0 / 255.0), uniforms.gColor.b, 1.0);
}
