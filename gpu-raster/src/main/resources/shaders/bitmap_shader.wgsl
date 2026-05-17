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
// Limitations enforced at the dispatch gate (see SkWebGpuDevice
// drawImageRect) :
//  - No paint.shader-as-bitmap (drawImageRect only ; G5.2 onwards).
//  - No color management (texture is uploaded as-is in sRGB-coded bytes).
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
};

// Tile-mode constants -- mirror `SkTileMode` ordinals.
const TILE_DECAL: u32 = 3u;

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
