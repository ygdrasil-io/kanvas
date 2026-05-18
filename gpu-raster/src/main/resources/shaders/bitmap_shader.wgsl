// G5.1 -- bitmap-shader pipeline used by drawImageRect.
//
// Vertex stage : full-screen Bjorke triangle, same pattern as
// `solid_color.wgsl` / `linear_gradient.wgsl`. Pair with
// `setScissorRect(...)` clipped to the (clip-intersected) integer
// pixelEdge-rounded devDst rect so fragments outside the dst rect are
// killed before the fragment stage.
//
// Fragment stage : for each fragment center `(px + 0.5, py + 0.5)` in
// device-pixel coords, map back to source-pixel coords via the affine
//   sx = src.l + (devX - dst.l) * (src.w / dst.w)
//   sy = src.t + (devY - dst.t) * (src.h / dst.h)
// then divide by image size to derive normalised UV. `textureSampleLevel`
// with `lod = 0` does the (bilinear or nearest) filter on the sampler
// bound at slot 1. The sampler picks the filter mode (Linear/Nearest)
// AND the address mode (ClampToEdge/Repeat/MirrorRepeat) -- the shader
// is filter-agnostic and only branches on tile mode for kDecal (G5.1.1).
//
// The texture is uploaded as RGBA8Unorm with **unpremul sRGB-encoded
// bytes** (the SkImage convention -- `pixels[i*4+0..3] = R G B A` non-
// premultiplied, sRGB-tagged). The intermediate target convention is
// **premul sRGB-coded** (matches solid_color.wgsl and the gradients), so
// the fragment output multiplies RGB by alpha before returning. The
// `paint.alpha` and uniform tint (color filter / blend modulation) are
// folded into the sampled alpha by a uniform `paintColor` scale that
// defaults to (1, 1, 1, 1) when the paint is null.
//
// G5.1.1 -- tile mode plumbed through `imageSize.z` (bit-reinterpreted
// u32 ; matches the `Float.fromBits` packing used by the gradient
// shaders). Values mirror `SkTileMode` ordinals :
//   0 = kClamp    -> sampler ClampToEdge ; shader pass-through.
//   1 = kRepeat   -> sampler Repeat       ; shader pass-through.
//   2 = kMirror   -> sampler MirrorRepeat ; shader pass-through.
//   3 = kDecal    -> sampler ClampToEdge (WebGPU has no `BorderColor`
//                    for non-depth textures) ; shader returns transparent
//                    when the requested UV falls outside [0, 1].
// The dispatch gate (see SkWebGpuDevice.drawImageRect) currently feeds
// kClamp ; the other modes are reachable via the test-only enqueue path.
//
// G6.2 -- intermediate target is `RGBA16Float` ; the output convention
// is unchanged (premul sRGB-coded). F16 buys sub-byte precision on
// downstream blends and bilinear lerps ; no colorspace switch.
//
// G5.3 -- texture color management. The intermediate target convention
// is premul sRGB-coded (sRGB primaries, sRGB OETF). If the source
// `SkImage` carries a non-sRGB color space, its texels are in that
// source space's encoded primaries. To draw correctly we :
//   (1) sample the texel (unpremul, source-encoded),
//   (2) linearize through the source TF,
//   (3) multiply by a 3x3 primaries matrix (source -> sRGB),
//   (4) re-encode through the sRGB OETF,
//   (5) then proceed with the existing premul-by-alpha + paintColor.
// The TF used in steps (2) and (4) is the sRGB curve in this slice --
// it covers sRGB sources (no-op identity matrix) and Display P3 sources
// (P3 shares the sRGB TF ; only the primaries matrix is non-trivial).
// `csFlags.x` is a bit-reinterpreted u32 sentinel : 0 = no transform
// (existing fast path), 1 = apply the matrix-based transform. The
// matrix is column-major (`mat3x3<f32>`) ; for sRGB images the host
// uploads the identity so the multiply is a no-op even when the flag
// would route through the transform branch -- the flag is the gate.
//
// Limitations enforced at the dispatch gate (see SkWebGpuDevice
// drawImageRect) :
//  - No paint.shader-as-bitmap (drawImageRect only ; G5.2 onwards).
//  - Color management : sRGB (no-op) + Display P3 (sRGB TF, P3 gamut).
//    Other source colorspaces (Rec.2020 linear, Adobe RGB, ...) still
//    bypass the transform branch -- they'd need their own TF coefs in
//    the uniform, which is out of scope for G5.3.
//
// ASCII strict -- WGSL parser truncates on non-ASCII in wgpu4k 0.2.0.

struct Uniforms {
    // Source rect in source-image-pixel coords (l, t, r, b).
    srcRect:   vec4f,    // offset  0
    // Destination rect in device-pixel coords (l, t, r, b).
    dstRect:   vec4f,    // offset 16
    // Image size in source pixels (w, h, _, _).
    // `.z` is repurposed as the tile-mode flag (bit-reinterpreted u32 ;
    // see header comment above). `.w` stays zero / reserved.
    imageSize: vec4f,    // offset 32
    // Per-draw paint scale folded into the sampled color (premul vec4f).
    // Defaults to (1, 1, 1, 1) -- paint.alpha and color filter overrides
    // can scale rgba multiplicatively here (G5.x follow-ups).
    paintColor: vec4f,   // offset 48
    // G5.3 -- color-space transform gate. `.x` is a bit-reinterpreted
    // u32 sentinel : 0 = no transform (identity / sRGB fast path), 1 =
    // apply the sRGB EOTF -> matrix -> sRGB OETF transform. `.y/.z/.w`
    // are reserved (zero-padded).
    csFlags: vec4f,      // offset 64
    // G5.3 -- column-major 3x3 primaries matrix (source-linear ->
    // sRGB-linear). std140 stores each column padded to 16 bytes, so
    // the struct consumes 48 bytes here (offsets 80 / 96 / 112). For
    // sRGB sources the host uploads the identity ; for Display P3 it
    // uploads the P3 -> sRGB primaries transform built on the CPU via
    // `SkColorSpaceXformSteps`.
    csMatrix:  mat3x3<f32>, // offset 80
};

// Tile-mode constants -- mirror `SkTileMode` ordinals.
const TILE_DECAL: u32 = 3u;

// G5.3 -- color-space transform mode sentinel. 0 = no-op (sRGB source
// or any source whose pipeline reduces to identity ; the host gate
// covers Skia's `SkColorSpaceXformSteps::Flags::isIdentity`). 1 =
// apply linearize -> matrix -> encode. Higher values are reserved for
// future TF families (Rec.2020 linear, ...) if needed.
const CS_MODE_IDENTITY: u32 = 0u;
const CS_MODE_SRGB_TF_MATRIX: u32 = 1u;

@group(0) @binding(0) var<uniform> uniforms: Uniforms;
@group(0) @binding(1) var image_texture: texture_2d<f32>;
@group(0) @binding(2) var image_sampler: sampler;

// G5.3 -- sRGB EOTF (encoded -> linear). Matches `present_pass.wgsl`'s
// `srgb_to_linear` byte-for-byte ; we keep a local copy here so the
// bitmap-shader module is self-contained.
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

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

@fragment
fn fs_main(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    // pos.xy is the pixel center (column p -> p + 0.5). Map back to
    // source-image-pixel coords through the dst -> src affine.
    let dst_w = uniforms.dstRect.z - uniforms.dstRect.x;
    let dst_h = uniforms.dstRect.w - uniforms.dstRect.y;
    let src_w = uniforms.srcRect.z - uniforms.srcRect.x;
    let src_h = uniforms.srcRect.w - uniforms.srcRect.y;

    let sx = uniforms.srcRect.x + (pos.x - uniforms.dstRect.x) * (src_w / dst_w);
    let sy = uniforms.srcRect.y + (pos.y - uniforms.dstRect.y) * (src_h / dst_h);

    // Normalise to [0, 1] UV. Tile-mode handling is split between the
    // sampler (Clamp/Repeat/Mirror via addressModeU/V) and the shader
    // (kDecal -- transparent outside [0, 1]).
    let u = sx / uniforms.imageSize.x;
    let v = sy / uniforms.imageSize.y;

    // kDecal : WebGPU has no `BorderColor` mode for sampled (non-depth)
    // textures, so we emulate it -- the sampler stays ClampToEdge and
    // the shader kills out-of-rect fragments here. Matches the
    // `fs_decal` idiom in linear_gradient.wgsl.
    let tile_flag = bitcast<u32>(uniforms.imageSize.z);
    if (tile_flag == TILE_DECAL) {
        if (u < 0.0 || u > 1.0 || v < 0.0 || v > 1.0) {
            return vec4f(0.0, 0.0, 0.0, 0.0);
        }
    }

    // Sample (filter mode = Nearest or Linear, picked by the sampler).
    let sampled = textureSampleLevel(image_texture, image_sampler, vec2f(u, v), 0.0);

    // G5.3 -- texture color management. When the host marks the source
    // image as non-sRGB (csFlags.x != CS_MODE_IDENTITY), apply the
    // sRGB-EOTF -> primaries-matrix -> sRGB-OETF chain on the sampled
    // *unpremul* RGB before the premul step below. Alpha is untouched
    // by the colorspace transform (matches Skia's xform pipeline).
    //
    // Note: in WGSL, `mat3x3 * vec3` is column-vector matrix multiply,
    // which matches the column-major layout we upload from the host.
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
    }

    // The uploaded texture carries **unpremul** source-encoded bytes
    // (SkImage convention -- post-G5.3 the source colorspace can be
    // non-sRGB ; the transform branch above lifts those texels into
    // sRGB-encoded values before premul). Multiply RGB by alpha to
    // match the premul intermediate convention. paintColor multiplies
    // the result so the paint.alpha / color filter (future G5.x) can
    // fold in at no extra pipeline cost ; default is (1, 1, 1, 1).
    let pa = sampled.a * uniforms.paintColor.a;
    let pr = src_rgb.r * sampled.a * uniforms.paintColor.r;
    let pg = src_rgb.g * sampled.a * uniforms.paintColor.g;
    let pb = src_rgb.b * sampled.a * uniforms.paintColor.b;
    return vec4f(pr, pg, pb, pa);
}
