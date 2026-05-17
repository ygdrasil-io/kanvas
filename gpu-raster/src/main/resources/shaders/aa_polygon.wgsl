// G3.3b.2a AA polygon shader -- triangle list (fan tessellation),
// per-fragment edge-distance coverage, premul color.
//
// Vertex stage : identical to solid_polygon.wgsl. Each vertex is a
// device-pixel coord ; remap to NDC with Y-flip.
//
// Fragment stage : iterate over the polygon's perimeter edges (n
// edges, where n = polygon vertex count -- the interior fan edges
// are deliberately NOT in this list). For each edge, compute the
// signed perpendicular distance from the fragment to the edge ;
// positive = inside the polygon, negative = outside. Convert to a
// coverage value with clamp(dist + 0.5, 0, 1) -- this is the
// "1-pixel-wide AA falloff" convention. The min over all edges
// gives the polygon coverage (a fragment outside ANY edge is
// outside the polygon).
//
// Premul output : color.rgb * (color.a * coverage), color.a * coverage.
// Matches solid_color.wgsl / solid_polygon.wgsl convention so the
// SrcOver blend math is correct.
//
// MAX_EDGES = 256 is plenty for a circle flattened to ~32 segments,
// even with multiple Bezier curves. Polygons exceeding this fall back
// to the non-AA polygon pipeline (SkWebGpuDevice.drawPath).
//
// G6.2 -- intermediate target is `RGBA16Float` ; the output convention
// is unchanged (premul sRGB-coded). The benefit is sub-byte precision
// in the intermediate, not a colorspace switch.
//
// ASCII strict -- WGSL parser truncates on non-ASCII in wgpu4k 0.2.0.

struct Uniforms {
    color:    vec4f,                       // offset 0
    viewport: vec4f,                       // offset 16 : (w, h, 0, 0)
    edgeCount: u32,                        // offset 32
    _pad0: u32,
    _pad1: u32,
    _pad2: u32,
    edges:    array<vec4f, 256>,           // offset 48 : (a, b, c, _) per edge
};

@binding(0) @group(0) var<uniform> uniforms: Uniforms;

@vertex
fn vs_main(@location(0) pos: vec2f) -> @builtin(position) vec4f {
    let ndc_x =  pos.x / uniforms.viewport.x * 2.0 - 1.0;
    let ndc_y = -(pos.y / uniforms.viewport.y * 2.0 - 1.0);
    return vec4f(ndc_x, ndc_y, 0.0, 1.0);
}

@fragment
fn fs_main(@builtin(position) frag: vec4f) -> @location(0) vec4f {
    var coverage: f32 = 1.0;
    for (var i: u32 = 0u; i < uniforms.edgeCount; i = i + 1u) {
        let e = uniforms.edges[i];
        let dist = e.x * frag.x + e.y * frag.y + e.z;
        let edge_cov = clamp(dist + 0.5, 0.0, 1.0);
        coverage = min(coverage, edge_cov);
    }
    let c = uniforms.color;
    let a = c.a * coverage;
    return vec4f(c.rgb * a, a);
}
