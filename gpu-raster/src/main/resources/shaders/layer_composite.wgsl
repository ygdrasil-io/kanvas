// Phase G-saveLayer-fast -- direct GPU-to-GPU layer composite shader.
//
// Replaces the G-saveLayer scaffolding flush+readback+re-upload round-
// trip with a single fullscreen-quad render pass that samples the
// child device's `intermediateTexture` directly and writes onto the
// parent's `intermediateTexture`. Both textures share the same WebGPU
// adapter / queue (the child device was built via
// `SkWebGpuDevice.makeLayerDevice`, see its kdoc), so no copy or
// readback is needed.
//
// The fragment stage uses `textureLoad` with integer pixel coords
// (1:1 layer-pixel -> device-pixel grid alignment ; the layer device
// is sized to the integer-rounded layer bounds and the dstOrigin is
// an integer). This matches the present-pass identity copy pattern
// in `present_identity.wgsl` -- no filter, no sampler, no UV
// normalisation. It also makes the shader format-agnostic : the
// intermediate format can be `RGBA16Float` (G6.2 default) or
// `RGBA8Unorm` and the load behaves identically.
//
// The layer's pixels are already premul (the layer's draws hit its
// `intermediateTexture` via the same blend hardware as the parent's
// draws), so the fragment output is also premul. Paint alpha + colour
// modulation fold into a single uniform `paintColor` scale that
// multiplies the loaded RGBA. Blend mode is handled via the pipeline's
// blend state -- matches the natively-blendable subset enforced at
// dispatch (kClear / kSrc / kSrcOver / kDstOver).
//
// Scissor-rect tightening (clip-intersected integer dst rect) is set
// on the render pass by the host -- fragments outside the visible
// composite region never reach this shader.
//
// Phase G-saveLayer-colorFilter -- after the paintColor scale and
// before the blend mode is applied (pipeline blend state), the shader
// optionally runs an SkColorFilter on the sampled texel :
//
//   colorFilterKind == 0 : no-op (default ; fast path).
//   colorFilterKind == 1 : SkColorFilters.Blend(colour, mode). The
//                          constant `colorFilterParam0` (premul RGBA)
//                          is blended as *src* with the texel as *dst*
//                          under the SkBlendMode in `colorFilterMode`.
//                          Supported subset matches `blendPremul` in
//                          SkColorFilters.kt : kClear / kSrc / kDst /
//                          kSrcOver / kDstOver / kSrcIn / kDstIn /
//                          kSrcOut / kDstOut / kSrcATop / kDstATop /
//                          kXor / kPlus / kModulate / kScreen.
//   colorFilterKind == 2 : SkColorFilters.Matrix(20 floats, row-major).
//                          The shader unpremuls -> matrix*RGBA + bias
//                          -> repremuls. Matrix rows are packed into 4
//                          vec4f slots (R / G / B / A) ; per-row bias
//                          lives in the 5th vec4f (`colorFilterBias`).
//
// ASCII strict -- WGSL parser truncates on non-ASCII in wgpu4k 0.2.0.

struct Uniforms {
    // dstOrigin : layer's top-left in parent-device pixel coords
    // (integer-valued floats so the fragment stage's int conversion is
    // exact). `.zw` is the layer's (w, h) in source pixels.
    dstOriginSize: vec4f,    // offset   0
    // Per-draw paint scale folded into the loaded colour. Premul
    // (1, 1, 1, 1) when paint is null ; alpha = 128 paint scales by
    // (a, a, a, a) since the layer pixels are premul.
    paintColor:    vec4f,    // offset  16
    // Phase G-saveLayer-colorFilter -- (kind, blendMode, _, _).
    // `kind` selects the per-pixel transform :
    //   0 = none, 1 = SkBlendModeFilter, 2 = SkMatrixFilter.
    // `blendMode` is the SkBlendMode ordinal used by kind == 1.
    colorFilterKindMode: vec4f,  // offset  32
    // kind == 1 : constant src colour (premul RGBA).
    // kind == 2 : matrix row 0 (R out coefficients : rR, rG, rB, rA).
    colorFilterParam0: vec4f,    // offset  48
    // kind == 2 : matrix row 1 (G out coefficients).
    colorFilterParam1: vec4f,    // offset  64
    // kind == 2 : matrix row 2 (B out coefficients).
    colorFilterParam2: vec4f,    // offset  80
    // kind == 2 : matrix row 3 (A out coefficients).
    colorFilterParam3: vec4f,    // offset  96
    // kind == 2 : per-row bias (R, G, B, A).
    colorFilterBias:   vec4f,    // offset 112
};

@group(0) @binding(0) var<uniform> uniforms: Uniforms;
@group(0) @binding(1) var layer_texture: texture_2d<f32>;

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

// Phase G-saveLayer-colorFilter -- pure-float blend on premul RGBA.
// Mirrors `blendPremul` in SkColorFilters.kt for the same supported
// subset of SkBlendModes. Inputs are premul ; output is premul.
// Unsupported modes (separable / HSL) fall through to identity (src),
// matching the host-side fallback that drops the colour filter for
// unsupported variants.
fn blend_premul(s: vec4f, d: vec4f, mode: u32) -> vec4f {
    // Enum ordinals match SkBlendMode declaration order :
    //   0 kClear,  1 kSrc,   2 kDst,    3 kSrcOver, 4 kDstOver,
    //   5 kSrcIn,  6 kDstIn, 7 kSrcOut, 8 kDstOut,  9 kSrcATop,
    //  10 kDstATop, 11 kXor, 12 kPlus, 13 kModulate, 14 kScreen.
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
    // Unsupported (separable / HSL) -- identity src. Host-side gate
    // should keep this branch dead ; defensive fallback.
    return s;
}

@fragment
fn fs_main(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    // pos.xy is the destination pixel center (column p -> p + 0.5).
    // Floor to integer dst pixel coords, subtract dstOrigin to get the
    // matching layer-pixel coords. textureLoad with lod = 0 returns the
    // exact texel (no filter), so 1:1 grid alignment is exact.
    let dst_px = vec2i(i32(floor(pos.x)), i32(floor(pos.y)));
    let origin_px = vec2i(i32(uniforms.dstOriginSize.x), i32(uniforms.dstOriginSize.y));
    let layer_px = dst_px - origin_px;

    // Guard rail : if the scissor failed and the fragment maps outside
    // the layer extent, return transparent. The scissor on the render
    // pass should make this branch dead, but defensively returning
    // zero is cheaper than a UB-inducing out-of-bounds load.
    let layer_w = i32(uniforms.dstOriginSize.z);
    let layer_h = i32(uniforms.dstOriginSize.w);
    if (layer_px.x < 0 || layer_px.x >= layer_w ||
        layer_px.y < 0 || layer_px.y >= layer_h) {
        return vec4f(0.0, 0.0, 0.0, 0.0);
    }

    let sampled = textureLoad(layer_texture, layer_px, 0);
    var color = vec4f(
        sampled.r * uniforms.paintColor.r,
        sampled.g * uniforms.paintColor.g,
        sampled.b * uniforms.paintColor.b,
        sampled.a * uniforms.paintColor.a,
    );

    // Phase G-saveLayer-colorFilter -- per-pixel filter pass on the
    // post-paintColor texel. Identity (kind == 0) is the fast path.
    let kind = u32(uniforms.colorFilterKindMode.x + 0.5);
    if (kind == 1u) {
        // SkBlendModeFilter : constant `param0` (premul) blended as src
        // with `color` (premul) as dst under the chosen mode.
        let mode = u32(uniforms.colorFilterKindMode.y + 0.5);
        color = blend_premul(uniforms.colorFilterParam0, color, mode);
    } else if (kind == 2u) {
        // SkMatrixFilter : unpremul -> matrix*RGBA + bias -> repremul.
        let a = color.a;
        var rgb_un = vec3f(0.0, 0.0, 0.0);
        if (a > 0.0) {
            rgb_un = color.rgb / a;
        }
        let rgba = vec4f(rgb_un, a);
        let out_r = dot(uniforms.colorFilterParam0, rgba) + uniforms.colorFilterBias.x;
        let out_g = dot(uniforms.colorFilterParam1, rgba) + uniforms.colorFilterBias.y;
        let out_b = dot(uniforms.colorFilterParam2, rgba) + uniforms.colorFilterBias.z;
        let out_a = dot(uniforms.colorFilterParam3, rgba) + uniforms.colorFilterBias.w;
        // Clamp to [0, 1] before re-premul ; matches the device-side
        // clamp upstream's matrix filter applies on storage.
        let cR = clamp(out_r, 0.0, 1.0);
        let cG = clamp(out_g, 0.0, 1.0);
        let cB = clamp(out_b, 0.0, 1.0);
        let cA = clamp(out_a, 0.0, 1.0);
        color = vec4f(cR * cA, cG * cA, cB * cA, cA);
    }

    return color;
}
