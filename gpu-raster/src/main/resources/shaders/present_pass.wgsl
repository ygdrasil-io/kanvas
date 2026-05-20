// G6.1 / G6.2 present pass -- sRGB-encoded sRGB-primary intermediate
// -> Rec.2020 encoded readback (matches
// `TestUtils.DM_REFERENCE_COLOR_SPACE`).
//
// Reads each pixel of the intermediate render target with `textureLoad`
// (1:1 pixel mapping, no sampling filter), un-premultiplies, applies
// the inverse sRGB OETF, multiplies by the BT.2020 primaries matrix
// in linear space, then re-encodes through the Rec.2020 OETF and
// writes to the final render target. WebGpuSink's CPU loop (G6.0) is
// now redundant and drops to a straight byte-to-bitmap repack.
//
// **II5 / Crbug892988.** The un-premultiplication step is required so
// the readback bytes match the unpremul convention of the reference
// PNGs in `original-888/`. F16 raster's `SkBitmap.getPixel` for
// `kRGBA_F16Norm` does the same conversion (premul -> unpremul ARGB)
// at compare time ; doing it here keeps the cross-backend comparison
// fair. For opaque draws (the common case) `src.a == 1` and
// un-premul is a no-op, so existing tests are unchanged. For
// non-opaque kSrc (the Crbug892988 case) it brings the GPU output
// from `(R*a, G*a, B*a, a)` back to `(R, G, B, a)` -- the bytes the
// PNG reference encodes.
//
// **G6.2.** The intermediate is now `RGBA16Float` instead of
// `RGBA8Unorm` (see [SkWebGpuDevice.intermediateTexture]) but the
// stored content convention is unchanged : draw shaders still emit
// premul **sRGB-coded** values, the F16 format only buys sub-byte
// precision in the intermediate. The transform chain below is
// therefore identical to G6.1's.
//
// Vertex stage : full-screen Bjorke triangle, same pattern as G0's
// clear_red.wgsl.
//
// ASCII strict -- WGSL parser truncates on non-ASCII in wgpu4k 0.2.0.

@group(0) @binding(0) var intermediate_texture: texture_2d<f32>;

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

// sRGB transfer inverse : encoded value [0,1] -> linear.
fn srgb_to_linear(v: f32) -> f32 {
    if (v <= 0.04045) {
        return v / 12.92;
    }
    return pow((v + 0.055) / 1.055, 2.4);
}

// BT.2020 OETF : linear value [0,1] -> encoded.
fn rec2020_encode(v: f32) -> f32 {
    let c = max(v, 0.0);
    if (c < 0.0181) {
        return 4.5 * c;
    }
    return 1.0993 * pow(c, 0.45) - 0.0993;
}

@fragment
fn fs_main(@builtin(position) frag: vec4f) -> @location(0) vec4f {
    let pix = vec2i(i32(frag.x), i32(frag.y));
    let src = textureLoad(intermediate_texture, pix, 0);

    // II5 -- un-premultiply RGB before the OETF inverse / matrix /
    // OETF chain. The intermediate stores premul sRGB-encoded values ;
    // the colour-space transform is defined on unpremul linear values,
    // and the readback bytes feed into a comparison that loads the
    // reference PNG as unpremul.
    //
    // Alpha is clamped to 1.0 prior to the divide. The F16 intermediate
    // can hold values > 1.0 from non-clamping blend operations
    // (kPlus's `One + One` doesn't saturate on the GPU side ; the F16
    // format faithfully stores `src.a + dst.a` even when it exceeds
    // 1.0). Skia's CPU `kPlus` clamps each channel pre-store, so the
    // reference PNG encodes alpha = 1.0 for those pixels. Clamping
    // here keeps the divide neutral (inv_a = 1) on the kPlus case and
    // recovers `(R/A, G/A, B/A)` only when the genuine source alpha
    // was non-opaque (the Crbug892988 kSrc path).
    //
    // The `src.a > 0` guard avoids NaN from `0 / 0`.
    var rgb_unpremul = vec3f(0.0, 0.0, 0.0);
    if (src.a > 0.0) {
        let inv_a = 1.0 / min(src.a, 1.0);
        rgb_unpremul = vec3f(src.r * inv_a, src.g * inv_a, src.b * inv_a);
    }

    let lin_srgb = vec3f(
        srgb_to_linear(rgb_unpremul.r),
        srgb_to_linear(rgb_unpremul.g),
        srgb_to_linear(rgb_unpremul.b),
    );

    // Linear sRGB primaries -> linear Rec.2020 primaries.
    // WGSL mat3x3 is column-major : each `vec3f` is a column.
    let m = mat3x3<f32>(
        vec3f(0.62740, 0.06909, 0.01639),  // column 0 (acts on .r of input)
        vec3f(0.32928, 0.91954, 0.08801),  // column 1 (acts on .g)
        vec3f(0.04338, 0.01136, 0.89559),  // column 2 (acts on .b)
    );
    let lin_rec = m * lin_srgb;

    return vec4f(
        clamp(rec2020_encode(lin_rec.r), 0.0, 1.0),
        clamp(rec2020_encode(lin_rec.g), 0.0, 1.0),
        clamp(rec2020_encode(lin_rec.b), 0.0, 1.0),
        src.a,
    );
}
