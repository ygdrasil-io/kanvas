// Phase G-saveLayer-blend -- saveLayer composite with in-shader blend
// for non-natively-blendable SkBlendModes (kPlus, kMultiply, kScreen,
// kModulate, kDarken, kLighten, kOverlay, ...). WebGPU's fixed-function
// BlendState only expresses simple factor+op tuples, so modes that
// require min/max/conditional / multiplicative cross-terms must run
// the blend math in the fragment stage. The host pipeline writes the
// result to the parent intermediate via `BlendState = kSrc`.
//
// The blend math reads the destination from a *snapshot* of the parent
// intermediate (binding 2). WebGPU disallows binding the same texture
// as both render target and sample source -- we copy the parent into
// a same-format scratch via a separate render pass before this
// composite runs, then bind the scratch here.
//
// Shape mirrors `layer_composite.wgsl` exactly (same vs_main, same
// uniform layout, same paintColor + colorFilter handling on the
// source side). Only the final fragment value differs : instead of
// returning the post-colorFilter premul colour as the fragment output
// (which the pipeline blend hardware would then blend against the
// destination), this shader samples the destination snapshot,
// computes the canonical Skia blend mode formula in premul space,
// and returns the result as-is (the pipeline's kSrc blend writes it
// verbatim to the parent).
//
// Supported modes (selected via `blendModeOrdinal` uniform) :
//   12 kPlus      : min(s + d, 1)
//   13 kModulate  : s * d
//   14 kScreen    : s + d - s*d
//   16 kDarken    : sc + dc - max(sc*da, dc*sa) per channel, a = SrcOver
//   17 kLighten   : sc + dc - min(sc*da, dc*sa) per channel, a = SrcOver
//   22 kDifference: sc + dc - 2*min(sc*da, dc*sa) per channel, a = SrcOver
//   23 kExclusion : sc + dc - 2*sc*dc per channel, a = SrcOver
//   24 kMultiply  : (1-sa)*dc + (1-da)*sc + sc*dc per channel,
//                    a = sa + da - sa*da
// Other non-native modes (kOverlay / kHardLight / kColorDodge /
// kColorBurn / kSoftLight / HSL) -> the host gates and throws ; the
// shader's fallback returns kSrc-equivalent (the source after
// colorFilter) for defence in depth.
//
// ASCII strict -- WGSL parser truncates on non-ASCII in wgpu4k 0.2.0.

struct Uniforms {
    // dstOrigin : layer's top-left in parent-device pixel coords
    // (integer-valued floats). `.zw` is layer (w, h).
    dstOriginSize: vec4f,
    // Per-draw paint scale folded into the loaded source colour (premul).
    paintColor:    vec4f,
    // (kind, mode, blendModeOrdinal, _) :
    //   kind             -- colour-filter on source : 0 none, 1 Blend, 2 Matrix.
    //   mode             -- SkBlendMode ordinal for colour-filter (kind == 1).
    //   blendModeOrdinal -- SkBlendMode ordinal of the LAYER paint's blend mode
    //                       (the mode we evaluate against the snapshot dst).
    colorFilterKindMode: vec4f,
    colorFilterParam0: vec4f,
    colorFilterParam1: vec4f,
    colorFilterParam2: vec4f,
    colorFilterParam3: vec4f,
    colorFilterBias:   vec4f,
};

@group(0) @binding(0) var<uniform> uniforms: Uniforms;
@group(0) @binding(1) var layer_texture: texture_2d<f32>;
@group(0) @binding(2) var dst_snapshot_texture: texture_2d<f32>;

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

// Source-side colour filter -- same formulas as layer_composite.wgsl.
fn blend_premul_colorfilter(s: vec4f, d: vec4f, mode: u32) -> vec4f {
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

// SrcOver alpha output -- shared by every separable non-Porter-Duff mode
// in Skia (kDarken / kLighten / kDifference / kExclusion / kMultiply).
// `oa = sa + da * (1 - sa)`.
fn srcover_alpha(sa: f32, da: f32) -> f32 {
    return sa + da * (1.0 - sa);
}

// Apply the LAYER paint's blendMode against the snapshot destination.
// `s` is the post-colorFilter premul source ; `d` is the snapshot
// destination (premul as stored). Returns the premul output the pipeline
// (with BlendState = kSrc) writes verbatim to the parent.
fn blend_layer_paint(s: vec4f, d: vec4f, mode: u32) -> vec4f {
    let sa = s.a; let da = d.a;
    // kPlus -- already supported via fixed-function blend, but we
    // implement it here for completeness so the same shader covers the
    // whole non-native set.
    if (mode == 12u) {
        return min(vec4f(1.0, 1.0, 1.0, 1.0), s + d);
    }
    // kModulate -- s * d (separable channel-by-channel including alpha).
    if (mode == 13u) {
        return s * d;
    }
    // kScreen -- s + d - s*d (separable, including alpha).
    if (mode == 14u) {
        return s + d - s * d;
    }
    // kDarken -- rc = sc + dc - max(sc*da, dc*sa) ; ra = srcover.
    if (mode == 16u) {
        let r = s.r + d.r - max(s.r * da, d.r * sa);
        let g = s.g + d.g - max(s.g * da, d.g * sa);
        let b = s.b + d.b - max(s.b * da, d.b * sa);
        return vec4f(r, g, b, srcover_alpha(sa, da));
    }
    // kLighten -- rc = sc + dc - min(sc*da, dc*sa) ; ra = srcover.
    if (mode == 17u) {
        let r = s.r + d.r - min(s.r * da, d.r * sa);
        let g = s.g + d.g - min(s.g * da, d.g * sa);
        let b = s.b + d.b - min(s.b * da, d.b * sa);
        return vec4f(r, g, b, srcover_alpha(sa, da));
    }
    // kDifference -- rc = sc + dc - 2 * min(sc*da, dc*sa) ; ra = srcover.
    if (mode == 22u) {
        let r = s.r + d.r - 2.0 * min(s.r * da, d.r * sa);
        let g = s.g + d.g - 2.0 * min(s.g * da, d.g * sa);
        let b = s.b + d.b - 2.0 * min(s.b * da, d.b * sa);
        return vec4f(r, g, b, srcover_alpha(sa, da));
    }
    // kExclusion -- rc = sc + dc - 2 * sc * dc ; ra = srcover.
    if (mode == 23u) {
        let r = s.r + d.r - 2.0 * s.r * d.r;
        let g = s.g + d.g - 2.0 * s.g * d.g;
        let b = s.b + d.b - 2.0 * s.b * d.b;
        return vec4f(r, g, b, srcover_alpha(sa, da));
    }
    // kMultiply -- rc = (1-sa)*dc + (1-da)*sc + sc*dc ; ra = sa + da - sa*da.
    if (mode == 24u) {
        let ks = 1.0 - sa; let kd = 1.0 - da;
        let r = kd * s.r + ks * d.r + s.r * d.r;
        let g = kd * s.g + ks * d.g + s.g * d.g;
        let b = kd * s.b + ks * d.b + s.b * d.b;
        return vec4f(r, g, b, srcover_alpha(sa, da));
    }
    // Fallback -- unsupported mode reaches the shader by mistake. Return
    // source-as-is (kSrc-equivalent). The host gate should prevent this.
    return s;
}

@fragment
fn fs_main(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    // pos.xy is the destination pixel centre. Floor -> integer dst pixel
    // coords ; subtract dstOrigin to get layer-pixel coords.
    let dst_px = vec2i(i32(floor(pos.x)), i32(floor(pos.y)));
    let origin_px = vec2i(i32(uniforms.dstOriginSize.x), i32(uniforms.dstOriginSize.y));
    let layer_px = dst_px - origin_px;

    let layer_w = i32(uniforms.dstOriginSize.z);
    let layer_h = i32(uniforms.dstOriginSize.w);

    // Outside the layer rect : the source contributes nothing. We still
    // need a valid output for kSrc -- the snapshot's pixel preserves
    // whatever was there. The scissor on the host pass should keep this
    // branch dead, but defensively returning the snapshot keeps the
    // pixel untouched if the scissor ever slips.
    if (layer_px.x < 0 || layer_px.x >= layer_w ||
        layer_px.y < 0 || layer_px.y >= layer_h) {
        return textureLoad(dst_snapshot_texture, dst_px, 0);
    }

    let sampled = textureLoad(layer_texture, layer_px, 0);
    var s = vec4f(
        sampled.r * uniforms.paintColor.r,
        sampled.g * uniforms.paintColor.g,
        sampled.b * uniforms.paintColor.b,
        sampled.a * uniforms.paintColor.a,
    );

    // Source-side colour filter (kind 0 = no-op fast path).
    let kind = u32(uniforms.colorFilterKindMode.x + 0.5);
    if (kind == 1u) {
        let mode = u32(uniforms.colorFilterKindMode.y + 0.5);
        s = blend_premul_colorfilter(uniforms.colorFilterParam0, s, mode);
    } else if (kind == 2u) {
        let a = s.a;
        var rgb_un = vec3f(0.0, 0.0, 0.0);
        if (a > 0.0) {
            rgb_un = s.rgb / a;
        }
        let rgba = vec4f(rgb_un, a);
        let out_r = dot(uniforms.colorFilterParam0, rgba) + uniforms.colorFilterBias.x;
        let out_g = dot(uniforms.colorFilterParam1, rgba) + uniforms.colorFilterBias.y;
        let out_b = dot(uniforms.colorFilterParam2, rgba) + uniforms.colorFilterBias.z;
        let out_a = dot(uniforms.colorFilterParam3, rgba) + uniforms.colorFilterBias.w;
        let cR = clamp(out_r, 0.0, 1.0);
        let cG = clamp(out_g, 0.0, 1.0);
        let cB = clamp(out_b, 0.0, 1.0);
        let cA = clamp(out_a, 0.0, 1.0);
        s = vec4f(cR * cA, cG * cA, cB * cA, cA);
    }

    // Snapshot destination (premul) at the same dst pixel.
    let d = textureLoad(dst_snapshot_texture, dst_px, 0);

    let blendOrdinal = u32(uniforms.colorFilterKindMode.z + 0.5);
    return blend_layer_paint(s, d, blendOrdinal);
}
