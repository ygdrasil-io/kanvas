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
// Coverage : the stencil-test gates inside-vs-outside via the winding
// count (set by the prior stencil pass). G3.3b.3d splits the cover
// into TWO sub-draws sharing the same edge data :
//   * fs_inside  runs on fragments the stencil counts as INSIDE the
//                fill region. coverage = clamp(minDist + 0.5, 0, 1).
//                Range [0.5, 1.0] : 0.5 right at the edge, 1.0 once
//                we're more than half a pixel inside.
//   * fs_outside runs on fragments the stencil counts as OUTSIDE the
//                fill region (with the compare op flipped at the
//                pipeline level). coverage = clamp(0.5 - minDist, 0, 1).
//                Range [0.0, 0.5] : 0.5 right at the edge, 0.0 once
//                we're more than half a pixel outside.
// The two sub-draws are mutually exclusive at fragment level (stencil
// makes each fragment go to exactly one), so they never double-cover.
// Sum across the half-pixel boundary integrates to the correct AA
// profile -- closes the outside-half AA loss of G3.3b.3a.
//
// G6.2 -- intermediate target is `RGBA16Float` ; output convention is
// unchanged (premul sRGB-coded). F16 gives sub-byte precision in the
// intermediate ; no colorspace switch.
//
// G2.x clip-shape (closing slice) -- the trailing two vec4 slots carry
// an optional analytical "simple shape" clip captured from the
// SkCanvas's clip stack. clipKind == 0 means "no shape clip" ;
// clipKind == 1 means "rrect-style shape" (subsumes circle / oval /
// rrect with uniform radii). Both fs_inside and fs_outside multiply
// their coverage by `clip_cov` so the analytical clip mask intersects
// with the polygon's AA boundary band correctly.
//
// Phase G-direct-colorFilter (polygon closing slice) -- the 6 trailing
// vec4 slots carry an optional SkColorFilter applied to the unpremul
// source colour BEFORE the per-pixel premul + coverage modulation.
// Same packing as `solid_color.wgsl` ; host-side packer shared via
// `packLayerCompositeColorFilter`. Both fs_inside and fs_outside route
// through `apply_color_filter` so the filtered colour is used uniformly.
//
//   colorFilterKindMode.x == 0 : no-op (default ; fast path).
//   colorFilterKindMode.x == 1 : SkColorFilters.Blend(colour, mode).
//   colorFilterKindMode.x == 2 : SkColorFilters.Matrix(20 floats).
//
// ASCII strict -- WGSL parser truncates on non-ASCII in wgpu4k 0.2.0.

struct Uniforms {
    color:               vec4f,                       // offset 0
    viewport:            vec4f,                       // offset 16 : (w, h, 0, 0)
    edgeCount:           u32,                         // offset 32
    _pad0: u32,
    _pad1: u32,
    _pad2: u32,
    edges:               array<vec4f, 256>,           // offset 48 : (Ax, Ay, Bx, By)
    clipShapeBounds:     vec4f,                       // offset 4144 : (l, t, r, b) ; ignored when clipKind = 0
    clipShapeRadiiKind:  vec4f,                       // offset 4160 : (rx, ry, clipKind, _) ; clipKind in {0, 1}
    colorFilterKindMode: vec4f,                       // offset 4176 : (kind, blendMode, _, _) ; kind 0 = none
    colorFilterParam0:   vec4f,                       // offset 4192 : kind 1 -> premul colour ; kind 2 -> matrix row 0
    colorFilterParam1:   vec4f,                       // offset 4208 : matrix row 1 (G coefs)
    colorFilterParam2:   vec4f,                       // offset 4224 : matrix row 2 (B coefs)
    colorFilterParam3:   vec4f,                       // offset 4240 : matrix row 3 (A coefs)
    colorFilterBias:     vec4f,                       // offset 4256 : per-row bias (R, G, B, A)
};

@binding(0) @group(0) var<uniform> uniforms: Uniforms;

@vertex
fn vs_main(@location(0) pos: vec2f) -> @builtin(position) vec4f {
    let ndc_x =  pos.x / uniforms.viewport.x * 2.0 - 1.0;
    let ndc_y = -(pos.y / uniforms.viewport.y * 2.0 - 1.0);
    return vec4f(ndc_x, ndc_y, 0.0, 1.0);
}

fn minSegmentDistance(p: vec2f) -> f32 {
    var minDist: f32 = 1.0e9;
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
    return minDist;
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

// Phase G-direct-colorFilter -- premul-space blend table shared with
// solid_color.wgsl / aa_polygon.wgsl.
fn blend_premul(s: vec4f, d: vec4f, mode: u32) -> vec4f {
    let sa = s.a; let da = d.a;
    if (mode == 0u) { return vec4f(0.0); }
    if (mode == 1u) { return s; }
    if (mode == 2u) { return d; }
    if (mode == 3u) {
        let k = 1.0 - sa;
        return vec4f(s.r + d.r * k, s.g + d.g * k, s.b + d.b * k, sa + da * k);
    }
    if (mode == 4u) {
        let k = 1.0 - da;
        return vec4f(d.r + s.r * k, d.g + s.g * k, d.b + s.b * k, da + sa * k);
    }
    if (mode == 5u) { return s * da; }
    if (mode == 6u) { return d * sa; }
    if (mode == 7u) { return s * (1.0 - da); }
    if (mode == 8u) { return d * (1.0 - sa); }
    if (mode == 9u) {
        let k = 1.0 - sa;
        return vec4f(s.r * da + d.r * k, s.g * da + d.g * k,
                     s.b * da + d.b * k, sa * da + da * k);
    }
    if (mode == 10u) {
        let k = 1.0 - da;
        return vec4f(d.r * sa + s.r * k, d.g * sa + s.g * k,
                     d.b * sa + s.b * k, da * sa + sa * k);
    }
    if (mode == 11u) {
        let ks = 1.0 - sa; let kd = 1.0 - da;
        return vec4f(s.r * kd + d.r * ks, s.g * kd + d.g * ks,
                     s.b * kd + d.b * ks, sa * kd + da * ks);
    }
    if (mode == 12u) {
        return min(vec4f(1.0), s + d);
    }
    if (mode == 13u) { return s * d; }
    if (mode == 14u) { return s + d - s * d; }
    return s;
}

fn apply_color_filter(c_un: vec4f) -> vec4f {
    let kind = u32(uniforms.colorFilterKindMode.x + 0.5);
    if (kind == 1u) {
        let mode = u32(uniforms.colorFilterKindMode.y + 0.5);
        let a = c_un.a;
        let dst_premul = vec4f(c_un.r * a, c_un.g * a, c_un.b * a, a);
        let out_premul = blend_premul(uniforms.colorFilterParam0, dst_premul, mode);
        let oa = out_premul.a;
        if (oa <= 0.0) { return vec4f(0.0); }
        let inv = 1.0 / oa;
        return vec4f(out_premul.r * inv, out_premul.g * inv, out_premul.b * inv, oa);
    }
    if (kind == 2u) {
        let out_r = dot(uniforms.colorFilterParam0, c_un) + uniforms.colorFilterBias.x;
        let out_g = dot(uniforms.colorFilterParam1, c_un) + uniforms.colorFilterBias.y;
        let out_b = dot(uniforms.colorFilterParam2, c_un) + uniforms.colorFilterBias.z;
        let out_a = dot(uniforms.colorFilterParam3, c_un) + uniforms.colorFilterBias.w;
        return vec4f(
            clamp(out_r, 0.0, 1.0),
            clamp(out_g, 0.0, 1.0),
            clamp(out_b, 0.0, 1.0),
            clamp(out_a, 0.0, 1.0),
        );
    }
    return c_un;
}

@fragment
fn fs_inside(@builtin(position) frag: vec4f) -> @location(0) vec4f {
    let minDist = minSegmentDistance(frag.xy);
    var coverage = clamp(minDist + 0.5, 0.0, 1.0);
    coverage = coverage * clip_cov(frag.xy);
    let c = apply_color_filter(uniforms.color);
    let alpha = c.a * coverage;
    return vec4f(c.rgb * alpha, alpha);
}

@fragment
fn fs_outside(@builtin(position) frag: vec4f) -> @location(0) vec4f {
    let minDist = minSegmentDistance(frag.xy);
    var coverage = clamp(0.5 - minDist, 0.0, 1.0);
    coverage = coverage * clip_cov(frag.xy);
    let c = apply_color_filter(uniforms.color);
    let alpha = c.a * coverage;
    return vec4f(c.rgb * alpha, alpha);
}
