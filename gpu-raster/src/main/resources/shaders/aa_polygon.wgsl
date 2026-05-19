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
// G2.x clip-shape (closing slice) -- the trailing two vec4 slots carry
// an optional analytical "simple shape" clip captured from the
// SkCanvas's clip stack. clipKind == 0 means "no shape clip" ;
// clipKind == 1 means "rrect-style shape" (subsumes circle / oval /
// rrect with uniform radii). The fragment stage multiplies the polygon
// edge coverage by `clip_cov` so pixels outside the analytical clip
// drop to 0.
//
// ASCII strict -- WGSL parser truncates on non-ASCII in wgpu4k 0.2.0.

struct Uniforms {
    color:               vec4f,                       // offset 0
    viewport:            vec4f,                       // offset 16 : (w, h, 0, 0)
    edgeCount:           u32,                         // offset 32
    _pad0: u32,
    _pad1: u32,
    _pad2: u32,
    edges:               array<vec4f, 256>,           // offset 48 : (a, b, c, _) per edge
    clipShapeBounds:     vec4f,                       // offset 4144 : (l, t, r, b) ; ignored when clipKind = 0
    clipShapeRadiiKind:  vec4f,                       // offset 4160 : (rx, ry, clipKind, _) ; clipKind in {0, 1}
};

@binding(0) @group(0) var<uniform> uniforms: Uniforms;

@vertex
fn vs_main(@location(0) pos: vec2f) -> @builtin(position) vec4f {
    let ndc_x =  pos.x / uniforms.viewport.x * 2.0 - 1.0;
    let ndc_y = -(pos.y / uniforms.viewport.y * 2.0 - 1.0);
    return vec4f(ndc_x, ndc_y, 0.0, 1.0);
}

fn rrect_cov(p: vec2f, bounds: vec4f, rx_in: f32, ry_in: f32) -> f32 {
    let centre = vec2f(0.5 * (bounds.x + bounds.z), 0.5 * (bounds.y + bounds.w));
    let half = vec2f(0.5 * (bounds.z - bounds.x), 0.5 * (bounds.w - bounds.y));
    let rx = max(rx_in, 1e-4);
    let ry = max(ry_in, 1e-4);
    let q_abs = abs(p - centre);
    let q = q_abs - (half - vec2f(rx, ry));
    let outer_rect_sdf = max(q_abs.x - half.x, q_abs.y - half.y);
    let qm = max(q, vec2f(0.0, 0.0));
    let n = vec2f(qm.x / rx, qm.y / ry);
    let nl = length(n);
    let nl_safe = max(nl, 1e-6);
    let dir = n / nl_safe;
    let effective_r = length(vec2f(rx * dir.x, ry * dir.y));
    let corner_sdf = (nl - 1.0) * effective_r;
    let in_corner_band = step(0.0, q.x) * step(0.0, q.y);
    let band_sdf = mix(outer_rect_sdf, corner_sdf, in_corner_band);
    return clamp(0.5 - band_sdf, 0.0, 1.0);
}

fn clip_cov(p: vec2f) -> f32 {
    let clip_kind = i32(uniforms.clipShapeRadiiKind.z + 0.5);
    if (clip_kind == 1) {
        return rrect_cov(
            p,
            uniforms.clipShapeBounds,
            uniforms.clipShapeRadiiKind.x,
            uniforms.clipShapeRadiiKind.y,
        );
    }
    return 1.0;
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
    coverage = coverage * clip_cov(frag.xy);
    let c = uniforms.color;
    let a = c.a * coverage;
    return vec4f(c.rgb * a, a);
}
