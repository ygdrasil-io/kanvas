// Renderer conversion sprint: registered LinearGradientRT descriptor.
struct Uniforms {
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

fn srgb_to_linear(c: f32) -> f32 {
    if (c <= 0.04045) {
        return c / 12.92;
    }
    return pow((c + 0.055) / 1.055, 2.4);
}

fn linear_to_srgb(c: f32) -> f32 {
    if (c <= 0.003130804980173707) {
        return c * 12.920000076293945;
    }
    return pow(1.1371188163757324 * c, 0.4166666567325592) - 0.054999999701976776;
}

fn quantize_rgba8_channel(c: f32) -> f32 {
    let clamped = clamp(c, 0.0, 1.0);
    return floor(clamped * 255.0 + 0.5) * (1.0 / 255.0);
}

@fragment
fn fs_main(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let t = pos.x / 256.0;
    if (pos.y < 32.0) {
        let encoded = mix(uniforms.in_colors0, uniforms.in_colors1, vec4f(t));
        return vec4f(
            quantize_rgba8_channel(encoded.r),
            quantize_rgba8_channel(encoded.g),
            quantize_rgba8_channel(encoded.b),
            quantize_rgba8_channel(encoded.a),
        );
    }
    let lin0 = vec3f(
        srgb_to_linear(uniforms.in_colors0.r),
        srgb_to_linear(uniforms.in_colors0.g),
        srgb_to_linear(uniforms.in_colors0.b),
    );
    let lin1 = vec3f(
        srgb_to_linear(uniforms.in_colors1.r),
        srgb_to_linear(uniforms.in_colors1.g),
        srgb_to_linear(uniforms.in_colors1.b),
    );
    let lin = mix(lin0, lin1, vec3f(t));
    return vec4f(
        quantize_rgba8_channel(linear_to_srgb(lin.r)),
        quantize_rgba8_channel(linear_to_srgb(lin.g)),
        quantize_rgba8_channel(linear_to_srgb(lin.b)),
        1.0,
    );
}
