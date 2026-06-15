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
// from `csTfParams0/1` (Rec.2020 linear, Adobe RGB, ...), mode 3
// (G5.3.y ; Rec.2020 PQ) uses the hardcoded SMPTE ST 2084 EOTF then
// tone-maps to SDR by dividing by peak luminance (`csTfParams0.x`,
// default 1000 nits), mode 4 (G5.3.y ; Rec.2020 HLG) uses the BT.2100
// HLG inverse OETF with the same `Lw = peak` convention. For sRGB
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
// G5.2.2 -- rotated / skewed bitmap shader. The host
// always builds the 2x3 device-to-image affine and ships it via
// `devToImageRow0` / `devToImageRow1`. The fragment stage maps the
// fragment center directly through the affine ; the legacy
// `srcRect / dstRect` pair is kept in the layout for host-side
// scissor reuse but is no longer consumed by the fragment math.
// Total uniform size grows from 4336 to 4368 bytes (+32 bytes for
// the affine) ; the edge tail shifts forward by 32 bytes.
//
// Phase H2 paint-colorFilter (this slice) -- 6 trailing vec4f slots
// carry an optional `SkColorFilter`, mirror of `bitmap_shader.wgsl`'s
// extension. The filter is applied AFTER the G5.3 colorspace
// transform (`src_rgb` already sRGB-coded) and BEFORE the
// `clipShapeCoverage` rrect multiply, matching Skia's `shader ->
// colorFilter -> maskFilter -> clip -> blend` ordering. The slots sit
// AFTER the G5.2.2 affine and BEFORE `edgeCountPad`, so the edge tail
// shifts forward by 96 bytes ; total uniform size grows from 4368 to
// 4464 (+96 = 6 * 16 for the colorFilter payload).
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
    // G5.2.2 / sampler widening -- homogeneous device-to-image inverse.
    devToImageRow0:     vec4f,                // offset  224 : (sx, kx, tx, _)
    devToImageRow1:     vec4f,                // offset  240 : (ky, sy, ty, _)
    devToImageRow2:     vec4f,                // offset  256 : (p0, p1, p2, _)
    sampleOptions:      vec4f,                // offset  272 : (mode, B, C, _)
    // Phase H2 paint-colorFilter -- 6 trailing vec4f mirroring
    // `bitmap_shader.wgsl`'s tail. Same packing, same fast-path semantics
    // (kind == 0 -> no-op). Slots sit between the G5.2.2 affine and
    // `edgeCountPad` ; the edge tail shifts forward by 96 bytes.
    colorFilterKindMode: vec4f,               // offset  288 : (kind, blendMode, _, _)
    colorFilterParam0:   vec4f,               // offset  304
    colorFilterParam1:   vec4f,               // offset  320
    colorFilterParam2:   vec4f,               // offset  336
    colorFilterParam3:   vec4f,               // offset  352
    colorFilterBias:     vec4f,               // offset  368
    edgeCountPad:       vec4f,                // offset  384 : .x = edgeCount as bit-reinterp f32
    edges:              array<vec4f, 256>,    // offset  400 : (Ax, Ay, Bx, By) per edge
};

@group(0) @binding(0) var<uniform> uniforms: Uniforms;
@group(0) @binding(1) var image_texture: texture_2d<f32>;
@group(0) @binding(2) var image_sampler: sampler;

// Tile-mode constants -- mirror `SkTileMode` ordinals (same as
// `bitmap_shader.wgsl`).
const TILE_DECAL: u32 = 3u;
const SAMPLE_MODE_CUBIC: u32 = 1u;
const SAMPLE_MODE_IMPLICIT_SAMPLER: u32 = 2u;

// G5.3 / G5.3.x / G5.3.y -- color-space transform mode sentinel (same
// as bitmap_shader.wgsl). Modes 3 / 4 cover Rec.2020 PQ and HLG HDR
// sources tone-mapped to SDR (`csTfParams0.x` carries peak luminance).
const CS_MODE_IDENTITY: u32 = 0u;
const CS_MODE_SRGB_TF_MATRIX: u32 = 1u;
const CS_MODE_PARAMETRIC_TF_MATRIX: u32 = 2u;
const CS_MODE_PQ: u32 = 3u;
const CS_MODE_HLG: u32 = 4u;

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

// G5.3.y -- SMPTE ST 2084 PQ EOTF. Mirror of `pq_eotf` in
// `bitmap_shader.wgsl` ; identical constants. Returns nits ; caller
// tone-maps by dividing by peak luminance.
fn pq_eotf(N_in: f32) -> f32 {
    let m1 = 0.1593017578125;
    let m2 = 78.84375;
    let c1 = 0.8359375;
    let c2 = 18.8515625;
    let c3 = 18.6875;
    let N = clamp(N_in, 0.0, 1.0);
    let Np = pow(N, 1.0 / m2);
    let num = max(Np - c1, 0.0);
    let den = max(c2 - c3 * Np, 1.0e-6);
    return pow(num / den, 1.0 / m1) * 10000.0;
}

// G5.3.y -- BT.2100 HLG inverse OETF. Mirror of `hlg_inverse_oetf` in
// `bitmap_shader.wgsl` ; same `a, b, c` constants. Returns linear
// scene light in `[0, 1]` ; with `Lw = peakLuminance` the EOTF tone-
// mapped output equals the inverse-OETF value (no extra divide).
fn hlg_inverse_oetf(Ep_in: f32) -> f32 {
    let a = 0.17883277;
    let b = 0.28466892;
    let c = 0.55991073;
    let Ep = clamp(Ep_in, 0.0, 1.0);
    if (Ep <= 0.5) {
        return Ep * Ep / 3.0;
    }
    return (exp((Ep - c) / a) + b) / 12.0;
}

// Phase H2 paint-colorFilter -- pure-float blend on premul RGBA.
// Byte-for-byte copy of `bitmap_shader.wgsl`'s helper ; kept local so
// this module stays self-contained.
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

// Phase H2 paint-colorFilter -- apply the optional `SkColorFilter` to
// an unpremul RGBA source colour. Mirror of `bitmap_shader.wgsl`'s
// helper, byte-for-byte.
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
    } else if (clip_kind == 2) {
        // M4 -- kDifference : invert coverage so the shape carves a hole.
        return 1.0 - rrect_cov(
            p,
            uniforms.clipShapeBounds,
            uniforms.clipShapeRadiiKind.x,
            uniforms.clipShapeRadiiKind.y,
        );
    }
    return 1.0;
}

fn cubic_weight(t: f32, B: f32, C: f32) -> f32 {
    let x = abs(t);
    if (x >= 2.0) {
        return 0.0;
    }
    let x2 = x * x;
    let x3 = x2 * x;
    if (x < 1.0) {
        return ((12.0 - 9.0 * B - 6.0 * C) * x3 +
                (-18.0 + 12.0 * B + 6.0 * C) * x2 +
                (6.0 - 2.0 * B)) / 6.0;
    }
    return ((-B - 6.0 * C) * x3 +
            (6.0 * B + 30.0 * C) * x2 +
            (-12.0 * B - 48.0 * C) * x +
            (8.0 * B + 24.0 * C)) / 6.0;
}

fn wrap_index(i: i32, size: i32, tile: u32) -> i32 {
    if (tile == 1u) {
        return ((i % size) + size) % size;
    }
    if (tile == 2u) {
        let period = max(size * 2, 1);
        let m = ((i % period) + period) % period;
        if (m < size) {
            return m;
        }
        return period - 1 - m;
    }
    return clamp(i, 0, size - 1);
}

fn cubic_tap(ix: i32, iy: i32, tile_x: u32, tile_y: u32) -> vec4f {
    let width = i32(uniforms.imageSize.x);
    let height = i32(uniforms.imageSize.y);
    var tx = ix;
    var ty = iy;
    if (tile_x == TILE_DECAL && (tx < 0 || tx >= width)) {
        return vec4f(0.0);
    }
    if (tile_y == TILE_DECAL && (ty < 0 || ty >= height)) {
        return vec4f(0.0);
    }
    tx = wrap_index(tx, width, tile_x);
    ty = wrap_index(ty, height, tile_y);
    return textureLoad(image_texture, vec2i(tx, ty), 0);
}

fn cubic_sample(sx: f32, sy: f32, tile_x: u32, tile_y: u32) -> vec4f {
    let B = uniforms.sampleOptions.y;
    let C = uniforms.sampleOptions.z;
    let xf = sx - 0.5;
    let yf = sy - 0.5;
    let ix_base = i32(floor(xf));
    let iy_base = i32(floor(yf));
    let fx = xf - f32(ix_base);
    let fy = yf - f32(iy_base);
    var sum = vec4f(0.0);
    var sum_w = 0.0;
    for (var j: i32 = 0; j < 4; j = j + 1) {
        let dy = select(2.0 - fy, select(1.0 - fy, select(fy, 1.0 + fy, j == 0), j == 1), j == 2);
        let wy = cubic_weight(dy, B, C);
        for (var i: i32 = 0; i < 4; i = i + 1) {
            let dx = select(2.0 - fx, select(1.0 - fx, select(fx, 1.0 + fx, i == 0), i == 1), i == 2);
            let wx = cubic_weight(dx, B, C);
            let wt = wx * wy;
            let tap = cubic_tap(ix_base + i - 1, iy_base + j - 1, tile_x, tile_y);
            sum = sum + tap * wt;
            sum_w = sum_w + wt;
        }
    }
    if (abs(sum_w) <= 1.0e-6) {
        return vec4f(0.0);
    }
    return clamp(sum / sum_w, vec4f(0.0), vec4f(1.0));
}

// Sample the bitmap pattern at device-pixel position `p`. Mirrors the
// fragment body of `bitmap_shader.wgsl` (same affine map, same per-axis
// decal check, same csMode transform, same premul + paint modulation).
// Returns premul RGBA in the sRGB-coded working space.
fn sampleBitmap(p: vec2f) -> vec4f {
    let hx = uniforms.devToImageRow0.x * p.x
           + uniforms.devToImageRow0.y * p.y
           + uniforms.devToImageRow0.z;
    let hy = uniforms.devToImageRow1.x * p.x
           + uniforms.devToImageRow1.y * p.y
           + uniforms.devToImageRow1.z;
    let hw = uniforms.devToImageRow2.x * p.x
           + uniforms.devToImageRow2.y * p.y
           + uniforms.devToImageRow2.z;
    if (abs(hw) <= 1.0e-6) {
        return vec4f(0.0);
    }
    let sx = hx / hw;
    let sy = hy / hw;

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

    var sampled: vec4f;
    let sample_mode = u32(uniforms.sampleOptions.x + 0.5);
    if (sample_mode == SAMPLE_MODE_CUBIC) {
        sampled = cubic_sample(sx, sy, tile_x, tile_y);
    } else if (sample_mode == SAMPLE_MODE_IMPLICIT_SAMPLER) {
        sampled = textureSample(image_texture, image_sampler, vec2f(u, v));
    } else {
        sampled = textureSampleLevel(image_texture, image_sampler, vec2f(u, v), 0.0);
    }

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
    } else if (cs_mode == CS_MODE_PQ) {
        // G5.3.y -- Rec.2020 PQ HDR. Mirrors `bitmap_shader.wgsl`.
        let peak_nits = max(uniforms.csTfParams0.x, 1.0);
        let lin = vec3f(
            clamp(pq_eotf(src_rgb.r) / peak_nits, 0.0, 1.0),
            clamp(pq_eotf(src_rgb.g) / peak_nits, 0.0, 1.0),
            clamp(pq_eotf(src_rgb.b) / peak_nits, 0.0, 1.0),
        );
        let lin_srgb = uniforms.csMatrix * lin;
        src_rgb = vec3f(
            linear_to_srgb(lin_srgb.r),
            linear_to_srgb(lin_srgb.g),
            linear_to_srgb(lin_srgb.b),
        );
    } else if (cs_mode == CS_MODE_HLG) {
        // G5.3.y -- Rec.2020 HLG HDR. Mirrors `bitmap_shader.wgsl`.
        let lin = vec3f(
            hlg_inverse_oetf(src_rgb.r),
            hlg_inverse_oetf(src_rgb.g),
            hlg_inverse_oetf(src_rgb.b),
        );
        let lin_srgb = uniforms.csMatrix * lin;
        src_rgb = vec3f(
            linear_to_srgb(clamp(lin_srgb.r, 0.0, 1.0)),
            linear_to_srgb(clamp(lin_srgb.g, 0.0, 1.0)),
            linear_to_srgb(clamp(lin_srgb.b, 0.0, 1.0)),
        );
    }

    // Phase H2 paint-colorFilter -- run the optional `SkColorFilter`
    // on the unpremul (sRGB-coded) RGBA before the premul + paint
    // modulation. Order matches `bitmap_shader.wgsl` (shader stage
    // sample/colorspace -> colorFilter -> premul -> clip in the
    // caller).
    let filtered_un = apply_color_filter(vec4f(src_rgb.r, src_rgb.g, src_rgb.b, sampled.a));
    let pa = filtered_un.a * uniforms.paintColor.a;
    let pr = filtered_un.r * filtered_un.a * uniforms.paintColor.r;
    let pg = filtered_un.g * filtered_un.a * uniforms.paintColor.g;
    let pb = filtered_un.b * filtered_un.a * uniforms.paintColor.b;
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
