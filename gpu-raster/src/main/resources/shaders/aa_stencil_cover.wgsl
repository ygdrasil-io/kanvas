// G3.3b.3a AA multi-contour shader -- stencil-and-cover with per-fragment
// edge-segment coverage. Vertex layout : bbox triangle list (same as the
// non-AA cover pass). Fragment iterates the path's edge segments across
// all contours, computes the unsigned distance to the nearest segment,
// and produces a premul colour with smooth boundary falloff.
//
// Companion to aa_polygon.wgsl. Why a separate shader : the convex
// single-contour shader uses min-of-signed-perp-distance, which assumes
// "outside any edge = outside polygon" -- valid only for convex. For
// multi-contour (or concave), an interior fragment can be on the
// "outside" half-plane of some edge's infinite line while still being
// inside the path. We avoid that trap by measuring distance to edge
// SEGMENTS (projection clamped to [0, 1]) instead of infinite lines,
// and we rely on the stencil winding count -- not edge orientation --
// to decide inside vs outside.
//
// Coverage : the stencil-test already gated inside-vs-outside via the
// winding count (set by the prior stencil pass). This shader only runs
// on fragments inside the path, so it just needs the boundary AA falloff
// -- coverage = clamp(minDist + 0.5, 0, 1), where minDist is the
// unsigned distance to the nearest edge segment. Range inside the path
// is [0.5, 1.0] : 0.5 right at the edge, 1.0 once we're more than half
// a pixel inside. The OUTSIDE half of the AA boundary (coverage 0 -> 0.5
// as the fragment crosses inwards) is lost because the stencil discards
// count = 0 fragments before this shader runs. Net effect : boundaries
// look "harder than the reference by half a pixel". Acceptable for
// G3.3b.3a vs throwing ; a sample-mask AA or two-pass cover could close
// the gap later.
//
// ASCII strict -- WGSL parser truncates on non-ASCII in wgpu4k 0.2.0.

struct Uniforms {
    color:    vec4f,                       // offset 0
    viewport: vec4f,                       // offset 16 : (w, h, 0, 0)
    edgeCount: u32,                        // offset 32
    _pad0: u32,
    _pad1: u32,
    _pad2: u32,
    edges:    array<vec4f, 256>,           // offset 48 : (Ax, Ay, Bx, By)
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
    var minDist: f32 = 1.0e9;
    let p = frag.xy;
    for (var i: u32 = 0u; i < uniforms.edgeCount; i = i + 1u) {
        let e = uniforms.edges[i];
        let ea = e.xy;
        let eb = e.zw;
        let ab = eb - ea;
        let ap = p - ea;
        let len2 = max(dot(ab, ab), 1.0e-9);
        let t = clamp(dot(ap, ab) / len2, 0.0, 1.0);
        let closest = ea + t * ab;
        let d = length(p - closest);
        minDist = min(minDist, d);
    }
    let coverage = clamp(minDist + 0.5, 0.0, 1.0);
    let c = uniforms.color;
    let alpha = c.a * coverage;
    return vec4f(c.rgb * alpha, alpha);
}
