// G2.3a -- single shader for axis-aligned rect fill, both AA and non-AA.
//
// Vertex stage : the same full-screen Bjorke triangle as G0. Pair with
// `setScissorRect(...)` (set conservatively to floor/ceil of the rect
// bounds) so pixels outside the rect's bbox are killed before reaching
// the fragment stage.
//
// Fragment stage : analytical coverage. For each fragment, compute the
// fraction of the pixel that lies inside the rect [bounds.l, bounds.r]
// x [bounds.t, bounds.b] (device-pixel coords). Multiply the premul
// source color by that coverage so SrcOver blending sees a correctly
// alpha-weighted source -- the standard "analytic coverage" path that
// matches SkBitmapDevice's axis-aligned AA rasterizer.
//
// Non-AA path : `bounds` are the pixelEdge-rounded integers ; every
// interior pixel-center sits at least 0.5 device pixels away from any
// edge, so the coverage formula evaluates to exactly 1.0 and the
// output equals the G2.2 unconditional-premul output. Existing tests
// (ClearRedTest / RectFillCrossTest / TranslucentSrcOverTest /
// BlendModeTest) stay byte-identical.
//
// ASCII strict -- WGSL parser truncates on non-ASCII in wgpu4k 0.2.0.

// G3.1.1 -- extended uniform with optional inner-rect cutout for AA
// stroke / AA hairline rasterization. Coverage at each pixel is
//   outer_cov - inner_cov   (clamped to [0, 1])
// which matches SkBitmapDevice.strokeRectAA's annular formulation.
//
// Fill rects pass `innerBounds` as a degenerate (l > r) rect ; the
// shader's clamp(min - max, 0, 1) naturally collapses inner_cov to 0
// and the result equals the prior fill-only output.
struct Uniforms {
    color:       vec4f,   // offset  0 : unpremul ARGB
    outerBounds: vec4f,   // offset 16 : (l, t, r, b) in device pixels
    innerBounds: vec4f,   // offset 32 : (l, t, r, b) ; degenerate for fill
};

@binding(0) @group(0) var<uniform> uniforms: Uniforms;

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

@fragment
fn fs_main(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    // pos.xy is the pixel center : column `p` has center at `p + 0.5`.
    // Intersection length per axis between the pixel [p, p+1] and the
    // rect edges, clamped to [0, 1]. Outer coverage = full fill area ;
    // inner coverage = the area to subtract (for stroke / hairline).
    // For fill rects the inner bounds are degenerate so inner_cov = 0
    // and the result equals the prior fill output.
    let ob = uniforms.outerBounds;
    let outer_cov_x = clamp(min(pos.x + 0.5, ob.z) - max(pos.x - 0.5, ob.x), 0.0, 1.0);
    let outer_cov_y = clamp(min(pos.y + 0.5, ob.w) - max(pos.y - 0.5, ob.y), 0.0, 1.0);

    let ib = uniforms.innerBounds;
    let inner_cov_x = clamp(min(pos.x + 0.5, ib.z) - max(pos.x - 0.5, ib.x), 0.0, 1.0);
    let inner_cov_y = clamp(min(pos.y + 0.5, ib.w) - max(pos.y - 0.5, ib.y), 0.0, 1.0);

    let coverage = max(0.0, outer_cov_x * outer_cov_y - inner_cov_x * inner_cov_y);

    // Premul output for SrcOver pipeline (src=One, dst=OneMinusSrcAlpha).
    // Coverage folds into alpha : a partially-covered translucent pixel
    // contributes less than a fully-covered one, in the standard
    // premul-correct way.
    let c = uniforms.color;
    let a = c.a * coverage;
    return vec4f(c.rgb * a, a);
}
