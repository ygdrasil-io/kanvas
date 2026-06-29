struct VertexOutput {
    position: vec2<f32>,
    coverage: f32,
}

@group(0) @binding(0) var<storage, read> vertices: array<vec2<f32>>;
@group(0) @binding(1) var<storage, read_write> outputs: array<VertexOutput>;

override WORKGROUP_SIZE: u32 = 64u;

@compute @workgroup_size(WORKGROUP_SIZE)
fn compute_main(@builtin(global_invocation_id) gid: vec3<u32>) {
    let idx = gid.x;
    if (idx >= arrayLength(&vertices)) {
        return;
    }
    let pos = vertices[idx];
    outputs[idx] = VertexOutput(pos, 1.0);
}
