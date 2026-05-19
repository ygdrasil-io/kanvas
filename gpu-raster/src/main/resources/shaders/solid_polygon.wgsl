// G3.3a polygon shader -- triangle list, solid premul color.
//
// Vertex stage : each vertex is a device-pixel coord (x, y). Convert
// to NDC : [0, viewport.x] -> [-1, 1] on X ; [0, viewport.y] -> [1, -1]
// on Y (WebGPU NDC has Y+ pointing up, while device pixels are
// Y-down). Each draw maps a triangle-list of post-CTM vertices that
// SkWebGpuDevice.drawPath has already transformed.
//
// Fragment stage : solid color from the uniform, premultiplied for
// the SrcOver pipeline (same convention as solid_color.wgsl).
//
// No coverage / AA in this slice -- analytical edge coverage for
// generic polygons is G3.3b. Existing AA-rect path stays on the
// solid_color.wgsl pipeline (drawRect / drawPaint / drawFillRect).
//
// G6.2 -- intermediate target is `RGBA16Float` ; the output convention
// is unchanged (premul sRGB-coded). The benefit of the format change
// is sub-byte precision in the intermediate, not a colorspace switch.
//
// G2.x clip-shape (closing slice) -- the trailing two vec4 slots carry
// an optional analytical "simple shape" clip captured from the
// SkCanvas's clip stack. clipKind == 0 means "no shape clip" (the
// integer scissor is the only clip). clipKind == 1 means "rrect-style
// shape" (subsumes circle / oval / rrect with uniform radii). The
// fragment stage modulates the premul output by `rrect_cov` so pixels
// outside the analytical clip get 0 and the boundary band gets a
// smooth half-pixel falloff.
//
// ASCII strict -- WGSL parser truncates on non-ASCII in wgpu4k 0.2.0.

struct Uniforms {
    color:               vec4f,   // offset  0
    viewport:            vec4f,   // offset 16 : (width, height, 0, 0) in device pixels
    clipShapeBounds:     vec4f,   // offset 32 : (l, t, r, b) device-px ; ignored when clipKind = 0
    clipShapeRadiiKind:  vec4f,   // offset 48 : (rx, ry, clipKind, _) ; clipKind in {0, 1}
};

@binding(0) @group(0) var<uniform> uniforms: Uniforms;

@vertex
fn vs_main(@location(0) pos: vec2f) -> @builtin(position) vec4f {
    let ndc_x =  pos.x / uniforms.viewport.x * 2.0 - 1.0;
    let ndc_y = -(pos.y / uniforms.viewport.y * 2.0 - 1.0);
    return vec4f(ndc_x, ndc_y, 0.0, 1.0);
}

// Analytic coverage of an axis-aligned rounded rect at fragment center
// `p`. Identical formula to the one in solid_color.wgsl ; duplicated
// here to keep each shader file self-contained (wgpu4k 0.2.0 has no
// shader include / preprocessor).
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
    let c = uniforms.color;
    let cov = clip_cov(frag.xy);
    let a = c.a * cov;
    return vec4f(c.rgb * a, a);
}
