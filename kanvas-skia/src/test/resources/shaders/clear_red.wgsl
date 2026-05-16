// G0 bootstrap shader -- full-screen triangle, opaque red fragment.
// Three-vertex covering triangle (Bjorke big-triangle trick):
//   vertex 0 -> (-1, -1)
//   vertex 1 -> ( 3, -1)
//   vertex 2 -> (-1,  3)
// The triangle's bounding rect covers the entire NDC square ([-1,1]^2);
// rasterization clips it to the viewport. One fewer vertex than a quad,
// no index buffer, and avoids the diagonal-edge precision artefact a
// two-triangle quad would introduce along the y=x line.

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

@fragment
fn fs_main() -> @location(0) vec4f {
    return vec4f(1.0, 0.0, 0.0, 1.0);
}
