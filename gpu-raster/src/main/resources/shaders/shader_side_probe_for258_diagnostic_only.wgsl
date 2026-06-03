// FOR-258 diagnostic-only shader-side probe.
//
// This module is not part of any normal render path. SkWebGpuDevice loads and
// dispatches it only when `kanvas.webgpu.for258.shaderSideProbe=true`.
//
// It reads the already-rendered intermediate texture after draw shader
// consumption/blend/store and before the normal present pass. Results are
// written to a storage buffer for test evidence only.

@group(0) @binding(0) var intermediate_texture: texture_2d<f32>;
@group(0) @binding(1) var<storage, read_write> probe_values: array<vec4f>;

fn sample_point(index: u32) -> vec2i {
    if (index == 0u) {
        return vec2i(40, 40);
    }
    return vec2i(8, 24);
}

@compute @workgroup_size(1)
fn cs_main(@builtin(global_invocation_id) id: vec3u) {
    let index = id.x;
    if (index >= 2u) {
        return;
    }

    let point = sample_point(index);
    let dims = vec2i(textureDimensions(intermediate_texture));
    if (point.x < 0 || point.y < 0 || point.x >= dims.x || point.y >= dims.y) {
        probe_values[index] = vec4f(-1.0, -1.0, -1.0, -1.0);
        return;
    }

    probe_values[index] = textureLoad(intermediate_texture, point, 0);
}
