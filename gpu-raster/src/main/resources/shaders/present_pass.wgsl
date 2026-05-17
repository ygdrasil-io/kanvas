// G6.1 / G6.2 present pass -- sRGB-encoded sRGB-primary intermediate
// -> Rec.2020 encoded readback (matches
// `TestUtils.DM_REFERENCE_COLOR_SPACE`).
//
// Reads each pixel of the intermediate render target with `textureLoad`
// (1:1 pixel mapping, no sampling filter), applies the inverse sRGB
// OETF, multiplies by the BT.2020 primaries matrix in linear space,
// then re-encodes through the Rec.2020 OETF and writes to the final
// render target. WebGpuSink's CPU loop (G6.0) is now redundant and
// drops to a straight byte-to-bitmap repack.
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

    let lin_srgb = vec3f(
        srgb_to_linear(src.r),
        srgb_to_linear(src.g),
        srgb_to_linear(src.b),
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
