// KAN-031 runtime ColorFilter descriptor: LumaToAlpha.
//
// Bounded direct-rect route only. The uniform sourceColor is the solid
// paint color after the shader/color stage, expressed as unpremul RGBA.
// The color-filter stage computes dot(rgb, vec3(0.3, 0.6, 0.1)) into
// alpha and returns zero RGB. Fragment output is premultiplied for the
// fixed-function BlendPlan that follows.
struct Uniforms {
    sourceColor: vec4f,
};

@binding(0) @group(0) var<uniform> uniforms: Uniforms;

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

@fragment
fn fs_main() -> @location(0) vec4f {
    let c = uniforms.sourceColor;
    let luma = dot(c.rgb, vec3f(0.3, 0.6, 0.1));
    return vec4f(0.0, 0.0, 0.0, luma);
}
