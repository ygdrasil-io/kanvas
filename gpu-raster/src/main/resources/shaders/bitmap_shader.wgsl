// G5.1 -- first bitmap-shader pipeline. Supports drawImageRect with a
// single (filter, tile, blend) tuple : kLinear / kClamp / SrcOver.
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
// with `lod = 0` does the bilinear filter on the sampler bound at slot 1.
// `kClamp` tile mode is enforced on the sampler side (addressModeU/V =
// ClampToEdge) ; the shader has no tile-mode branch.
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
// G6.2 -- intermediate target is `RGBA16Float` ; the output convention
// is unchanged (premul sRGB-coded). F16 buys sub-byte precision on
// downstream blends and bilinear lerps ; no colorspace switch.
//
// Limitations enforced at the dispatch gate (see SkWebGpuDevice
// drawImageRect) :
//  - Only one filter mode (kLinear) -- sampler hard-coded to Linear.
//  - Only one tile mode (kClamp) -- sampler hard-coded to ClampToEdge.
//  - Only one blend mode (kSrcOver) -- pipeline blend state hard-coded.
//  - No paint.shader-as-bitmap (drawImageRect only).
//  - No color management (texture is uploaded as-is in sRGB-coded bytes).
//
// ASCII strict -- WGSL parser truncates on non-ASCII in wgpu4k 0.2.0.

struct Uniforms {
    // Source rect in source-image-pixel coords (l, t, r, b).
    srcRect:   vec4f,    // offset  0
    // Destination rect in device-pixel coords (l, t, r, b).
    dstRect:   vec4f,    // offset 16
    // Image size in source pixels (w, h, _, _).
    imageSize: vec4f,    // offset 32
    // Per-draw paint scale folded into the sampled color (premul vec4f).
    // Defaults to (1, 1, 1, 1) -- paint.alpha and color filter overrides
    // can scale rgba multiplicatively here (G5.x follow-ups).
    paintColor: vec4f,   // offset 48
};

@group(0) @binding(0) var<uniform> uniforms: Uniforms;
@group(0) @binding(1) var image_texture: texture_2d<f32>;
@group(0) @binding(2) var image_sampler: sampler;

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

    // Normalise to [0, 1] UV. The sampler's ClampToEdge address mode
    // pins UVs outside [0, 1] to the edge texels -- the kClamp tile
    // mode contract.
    let u = sx / uniforms.imageSize.x;
    let v = sy / uniforms.imageSize.y;

    // Bilinear sample (sampler magFilter / minFilter = Linear).
    let sampled = textureSampleLevel(image_texture, image_sampler, vec2f(u, v), 0.0);

    // The uploaded texture carries **unpremul** sRGB-encoded bytes
    // (SkImage convention). Multiply RGB by alpha to match the premul
    // intermediate convention. paintColor multiplies the result so the
    // paint.alpha / color filter (future G5.x) can fold in at no extra
    // pipeline cost ; default is (1, 1, 1, 1).
    let pa = sampled.a * uniforms.paintColor.a;
    let pr = sampled.r * sampled.a * uniforms.paintColor.r;
    let pg = sampled.g * sampled.a * uniforms.paintColor.g;
    let pb = sampled.b * sampled.a * uniforms.paintColor.b;
    return vec4f(pr, pg, pb, pa);
}
