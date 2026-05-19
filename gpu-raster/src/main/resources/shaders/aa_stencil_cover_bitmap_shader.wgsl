// G5.2.1 -- AA stencil-and-cover cover-pass for non-rect paths filled
// with a bitmap shader (`paint.shader is SkBitmapShader`). Mirror of
// `aa_stencil_cover_sweep_gradient.wgsl` and the other gradient AA
// cover shaders : same per-fragment minimum-distance edge AA
// machinery, same two-pass inside / outside cover that integrates to
// the full AA profile at the path boundary, but the colour source is
// a sampled texture instead of a gradient lookup.
//
// Two entry points :
//   fs_inside  : coverage in [0.5, 1.0]
//   fs_outside : coverage in [0.0, 0.5]
//
// Tile modes are resolved by the sampler's addressMode
// (ClampToEdge / Repeat / MirrorRepeat) AND a per-axis in-shader
// kDecal check (WebGPU has no `BorderColor` for non-depth sampled
// textures). The per-draw bind group carries the right sampler so
// the pipeline cache only keys on (blend, fillType, side) -- the
// tileX / tileY pair flows through `imageSize.z` / `imageSize.w`
// as bit-reinterpreted u32 for the decal-axis check, matching
// `bitmap_shader.wgsl` byte-for-byte.
//
// G5.3 -- texture color management. `csFlags.x` (bit-reinterpreted
// u32) gates the source-TF -> primaries matrix -> sRGB OETF chain ;
// `csMatrix` is the column-major source-linear -> sRGB-linear
// primaries transform. Identical to the rect pipeline -- mode 1 uses
// the hardcoded sRGB EOTF, mode 2 (G5.3.x) reads parametric TF coefs
// from `csTfParams0/1` (Rec.2020 linear, Adobe RGB, ...). For sRGB
// sources the host uploads identity and `csFlags.x = 0` so the
// transform branch is bypassed.
//
// Coverage modulation matches `aa_stencil_cover_sweep_gradient.wgsl` :
//   inside  : coverage = clamp(minDist + 0.5, 0, 1)   in [0.5, 1.0]
//   outside : coverage = clamp(0.5 - minDist, 0, 1)   in [0.0, 0.5]
// Across the half-pixel boundary band each fragment is gated to
// exactly one of the two pipelines (stencil compare-op flips between
// them), so the two sub-draws sum to the full AA falloff without
// double-painting.
//
// Uniform-layout note : the first two vec4 slots (`color` + `viewport`)
// match `solid_polygon.wgsl` / `aa_stencil_cover.wgsl` byte-for-byte
// so the stencil-write pass (which uses `solid_polygon.wgsl` and reads
// `viewport` for the NDC remap) can share this draw's bind group.
// `color` itself is unused by the bitmap fragment entries but kept in
// the slot to preserve the layout.
//
// G6.2 -- intermediate target is `RGBA16Float` ; output convention is
// unchanged (premul sRGB-coded). F16 gives sub-byte precision in the
// intermediate ; no colorspace switch.
//
// G2.x clip-shape (this slice) -- the two `clipShape*` slots sit
// AFTER the G5.3 colorspace block (`csMatrix + csTfParams0 +
// csTfParams1`) and BEFORE `edgeCountPad`, mirroring the layout of
// `bitmap_shader.wgsl`. Total uniform size grows from 4272 to 4336
// bytes (32 for G5.3.x csTfParams + 32 for G2.x clip slots) ; the
// edge-segment tail shifts forward by 64 bytes.
//
// G5.2.2 (this slice) -- rotated / skewed bitmap shader. The host
// always builds the 2x3 device-to-image affine and ships it via
// `devToImageRow0` / `devToImageRow1`. The fragment stage maps the
// fragment center directly through the affine ; the legacy
// `srcRect / dstRect` pair is kept in the layout for host-side
// scissor reuse but is no longer consumed by the fragment math.
// Total uniform size grows from 4336 to 4368 bytes (+32 bytes for
// the affine) ; the edge tail shifts forward by 32 bytes.
//
// ASCII strict -- WGSL parser truncates on non-ASCII in wgpu4k 0.2.0.

struct Uniforms {
    color:              vec4f,                // offset    0 : unused, kept for layout-compat
    viewport:           vec4f,                // offset   16 : (w, h, 0, 0)
    srcRect:            vec4f,                // offset   32 : (l, t, r, b) in image-pixel coords
    dstRect:            vec4f,                // offset   48 : (l, t, r, b) in device-pixel coords
    imageSize:          vec4f,                // offset   64 : (w, h, tileX as u32, tileY as u32)
    paintColor:         vec4f,                // offset   80 : premul tint
    csFlags:            vec4f,                // offset   96 : .x = csMode bit-reinterp u32
    csMatrix:           mat3x3<f32>,          // offset  112 : column-major 3x3 (std140 padded)
    csTfParams0:        vec4f,                // offset  160 : G5.3.x parametric TF (g, a, b, c)
    csTfParams1:        vec4f,                // offset  176 : G5.3.x parametric TF (d, e, f, _)
    // G2.x -- analytical clip-shape payload, mirrors `bitmap_shader.wgsl`.
    clipShapeBounds:    vec4f,                // offset  192 : (l, t, r, b) device-px
    clipShapeRadiiKind: vec4f,                // offset  208 : (rx, ry, clipKind, _)
    // G5.2.2 -- 2x3 device-to-image affine (`M^-1 = (ctm * localMatrix)^-1`).
    // Replaces the srcRect/dstRect rect-affine in the fragment math ;
    // mirrors `bitmap_shader.wgsl`'s offsets 192/208 (but here those
    // offsets are already taken by the clip slots, so the affine sits
    // immediately after).
    devToImageRow0:     vec4f,                // offset  224 : (sx, kx, tx, _)
    devToImageRow1:     vec4f,                // offset  240 : (ky, sy, ty, _)
    edgeCountPad:       vec4f,                // offset  256 : .x = edgeCount as bit-reinterp f32
    edges:              array<vec4f, 256>,    // offset  272 : (Ax, Ay, Bx, By) per edge
};

@group(0) @binding(0) var<uniform> uniforms: Uniforms;
@group(0) @binding(1) var image_texture: texture_2d<f32>;
@group(0) @binding(2) var image_sampler: sampler;

// Tile-mode constants -- mirror `SkTileMode` ordinals (same as
// `bitmap_shader.wgsl`).
const TILE_DECAL: u32 = 3u;

// G5.3 / G5.3.x -- color-space transform mode sentinel (same as
// bitmap_shader.wgsl).
const CS_MODE_IDENTITY: u32 = 0u;
const CS_MODE_SRGB_TF_MATRIX: u32 = 1u;
const CS_MODE_PARAMETRIC_TF_MATRIX: u32 = 2u;

@vertex
fn vs_main(@location(0) pos: vec2f) -> @builtin(position) vec4f {
    let ndc_x =  pos.x / uniforms.viewport.x * 2.0 - 1.0;
    let ndc_y = -(pos.y / uniforms.viewport.y * 2.0 - 1.0);
    return vec4f(ndc_x, ndc_y, 0.0, 1.0);
}

fn minSegmentDistance(p: vec2f) -> f32 {
    let edgeCount = bitcast<u32>(uniforms.edgeCountPad.x);
    var minDist: f32 = 1.0e9;
    for (var i: u32 = 0u; i < edgeCount; i = i + 1u) {
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

// G5.3 -- sRGB EOTF (encoded -> linear). Matches `bitmap_shader.wgsl`
// byte-for-byte ; kept local so this module is self-contained.
fn srgb_to_linear(v: f32) -> f32 {
    if (v <= 0.04045) {
        return v / 12.92;
    }
    return pow((v + 0.055) / 1.055, 2.4);
}

// G5.3 -- sRGB OETF (linear -> encoded).
fn linear_to_srgb(v: f32) -> f32 {
    let c = max(v, 0.0);
    if (c <= 0.0031308) {
        return 12.92 * c;
    }
    return 1.055 * pow(c, 1.0 / 2.4) - 0.055;
}

// G5.3.x -- parametric sRGBish TF eval. Mirror of `parametric_tf` in
// `bitmap_shader.wgsl` ; same coefficient packing (csTfParams0 = (g,
// a, b, c), csTfParams1 = (d, e, f, _)).
fn parametric_tf(v: f32) -> f32 {
    let g = uniforms.csTfParams0.x;
    let a = uniforms.csTfParams0.y;
    let b = uniforms.csTfParams0.z;
    let c = uniforms.csTfParams0.w;
    let d = uniforms.csTfParams1.x;
    let e = uniforms.csTfParams1.y;
    let f = uniforms.csTfParams1.z;
    let clamped = max(v, 0.0);
    if (clamped < d) {
        return c * clamped + f;
    }
    return pow(a * clamped + b, g) + e;
}

// G2.x -- analytic rrect coverage. Byte-for-byte copy of
// `solid_color.wgsl`'s `rrect_cov` ; see that file for the full
// derivation comment. Kept local so this module stays self-contained.
fn rrect_cov(p: vec2f, bounds: vec4f, rx_in: f32, ry_in: f32) -> f32 {
    let centre = vec2f(0.5 * (bounds.x + bounds.z), 0.5 * (bounds.y + bounds.w));
    let half = vec2f(0.5 * (bounds.z - bounds.x), 0.5 * (bounds.w - bounds.y));
    let rx = max(rx_in, 1e-4);
    let ry = max(ry_in, 1e-4);
    let q_abs = abs(p - centre);
    let q = q_abs - (half - vec2f(rx, ry));
    let inner_rect_sdf = max(q.x, q.y);
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
    let final_sdf = band_sdf;
    return clamp(0.5 - final_sdf, 0.0, 1.0);
}

// G2.x -- per-fragment clip-shape modulation, factored so the inside /
// outside cover entries share the same code path. Returns 1.0 when no
// shape clip is active (the integer scissor already covers the rect
// case) ; otherwise multiplies the fragment alpha by the analytic
// rrect coverage so pixels outside the curved clip get zeroed.
fn clipShapeCoverage(p: vec2f) -> f32 {
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

// Sample the bitmap pattern at device-pixel position `p`. Mirrors the
// fragment body of `bitmap_shader.wgsl` (same affine map, same per-axis
// decal check, same csMode transform, same premul + paint modulation).
// Returns premul RGBA in the sRGB-coded working space.
fn sampleBitmap(p: vec2f) -> vec4f {
    // G5.2.2 -- 2x3 device-to-image affine. See bitmap_shader.wgsl for
    // the derivation comment ; same semantics here.
    let sx = uniforms.devToImageRow0.x * p.x
           + uniforms.devToImageRow0.y * p.y
           + uniforms.devToImageRow0.z;
    let sy = uniforms.devToImageRow1.x * p.x
           + uniforms.devToImageRow1.y * p.y
           + uniforms.devToImageRow1.z;

    let u = sx / uniforms.imageSize.x;
    let v = sy / uniforms.imageSize.y;

    let tile_x = bitcast<u32>(uniforms.imageSize.z);
    let tile_y = bitcast<u32>(uniforms.imageSize.w);
    if (tile_x == TILE_DECAL && (u < 0.0 || u > 1.0)) {
        return vec4f(0.0, 0.0, 0.0, 0.0);
    }
    if (tile_y == TILE_DECAL && (v < 0.0 || v > 1.0)) {
        return vec4f(0.0, 0.0, 0.0, 0.0);
    }

    let sampled = textureSampleLevel(image_texture, image_sampler, vec2f(u, v), 0.0);

    var src_rgb = vec3f(sampled.r, sampled.g, sampled.b);
    let cs_mode = bitcast<u32>(uniforms.csFlags.x);
    if (cs_mode == CS_MODE_SRGB_TF_MATRIX) {
        let lin = vec3f(
            srgb_to_linear(src_rgb.r),
            srgb_to_linear(src_rgb.g),
            srgb_to_linear(src_rgb.b),
        );
        let lin_srgb = uniforms.csMatrix * lin;
        src_rgb = vec3f(
            linear_to_srgb(lin_srgb.r),
            linear_to_srgb(lin_srgb.g),
            linear_to_srgb(lin_srgb.b),
        );
    } else if (cs_mode == CS_MODE_PARAMETRIC_TF_MATRIX) {
        let lin = vec3f(
            parametric_tf(src_rgb.r),
            parametric_tf(src_rgb.g),
            parametric_tf(src_rgb.b),
        );
        let lin_srgb = uniforms.csMatrix * lin;
        src_rgb = vec3f(
            linear_to_srgb(lin_srgb.r),
            linear_to_srgb(lin_srgb.g),
            linear_to_srgb(lin_srgb.b),
        );
    }

    let pa = sampled.a * uniforms.paintColor.a;
    let pr = src_rgb.r * sampled.a * uniforms.paintColor.r;
    let pg = src_rgb.g * sampled.a * uniforms.paintColor.g;
    let pb = src_rgb.b * sampled.a * uniforms.paintColor.b;
    return vec4f(pr, pg, pb, pa);
}

// ----- inside cover (coverage in [0.5, 1.0]) -----

@fragment
fn fs_inside(@builtin(position) frag: vec4f) -> @location(0) vec4f {
    let minDist = minSegmentDistance(frag.xy);
    let coverage = clamp(minDist + 0.5, 0.0, 1.0);
    let sampled = sampleBitmap(frag.xy);
    let clipCov = clipShapeCoverage(frag.xy);
    return sampled * (coverage * clipCov);
}

// ----- outside cover (coverage in [0.0, 0.5]) -----

@fragment
fn fs_outside(@builtin(position) frag: vec4f) -> @location(0) vec4f {
    let minDist = minSegmentDistance(frag.xy);
    let coverage = clamp(0.5 - minDist, 0.0, 1.0);
    let sampled = sampleBitmap(frag.xy);
    let clipCov = clipShapeCoverage(frag.xy);
    return sampled * (coverage * clipCov);
}
