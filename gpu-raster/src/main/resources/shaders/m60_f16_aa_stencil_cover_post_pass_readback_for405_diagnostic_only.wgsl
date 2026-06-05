// FOR-405 diagnostic-only AA stencil-cover post-pass readback.
//
// Dispatched only behind `kanvas.webgpu.m60F16DirectPassWriteHook.enabled`
// after a bounded M60 F16 StencilCoverAaPolygonDraw render pass and before the
// final present/readback pass. It samples exactly the 16 FOR-401 residual
// coordinates from the RGBA16Float intermediate texture.

@group(0) @binding(0) var intermediate_texture: texture_2d<f32>;
@group(0) @binding(1) var<storage, read_write> post_pass_values: array<vec4f>;

fn sample_point(index: u32) -> vec2i {
    if (index == 0u) {
        return vec2i(92, 75);
    }
    if (index == 1u) {
        return vec2i(91, 76);
    }
    if (index == 2u) {
        return vec2i(90, 77);
    }
    if (index == 3u) {
        return vec2i(89, 78);
    }
    if (index == 4u) {
        return vec2i(88, 79);
    }
    if (index == 5u) {
        return vec2i(87, 80);
    }
    if (index == 6u) {
        return vec2i(101, 37);
    }
    if (index == 7u) {
        return vec2i(102, 37);
    }
    if (index == 8u) {
        return vec2i(99, 38);
    }
    if (index == 9u) {
        return vec2i(100, 38);
    }
    if (index == 10u) {
        return vec2i(101, 38);
    }
    if (index == 11u) {
        return vec2i(102, 38);
    }
    if (index == 12u) {
        return vec2i(103, 38);
    }
    if (index == 13u) {
        return vec2i(104, 38);
    }
    if (index == 14u) {
        return vec2i(98, 39);
    }
    return vec2i(99, 39);
}

@compute @workgroup_size(1)
fn cs_main(@builtin(global_invocation_id) id: vec3u) {
    let index = id.x;
    if (index >= 16u) {
        return;
    }

    let point = sample_point(index);
    let dims = vec2i(textureDimensions(intermediate_texture));
    if (point.x < 0 || point.y < 0 || point.x >= dims.x || point.y >= dims.y) {
        post_pass_values[index] = vec4f(-1.0, -1.0, -1.0, -1.0);
        return;
    }

    post_pass_values[index] = textureLoad(intermediate_texture, point, 0);
}
