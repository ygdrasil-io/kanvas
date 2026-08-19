# Prepared-image sRGB fixture oracle design

Date: 2026-07-27

## Objective

Provide one independent CPU oracle for the exact bounded FP-04 color-image
contract:

```text
straight encoded sRGB upload bytes
-> WebGPU sRGB decode
-> nearest or linear sampling
-> sampled-alpha premultiplication
-> component-wise premultiplied tint/paint alpha
-> WebGPU sRGB attachment encode
-> RGBA8 UNORM bytes
```

The oracle starts at the physical bytes returned by
`GPUPreparedImageUploadArtifact.tightRgba8BytesForUpload()`. It does not repeat
the artifact factory's RGBA/BGRA/A8 normalization or premultiplied-to-straight
conversion.

## Scope

The change is limited to test fixtures and CPU reference helpers. Production
rendering, product routing, the image legacy allowlist, native ownership, and
`unsupported.image.animation` remain unchanged.

The existing raw-byte helpers remain available for geometry, source-rectangle,
UV-clamp, and byte-layout tests. Their names must state that they are raw RGBA
helpers and must not imply that raw encoded-byte interpolation is a valid GPU
sRGB reference.

## Public test interfaces

`GPUPreparedImagePixelOracle` retains `SampleKind` and exposes these raw
helpers:

```kotlin
fun rawRgbaNearestSample(
    bytes: ByteArray,
    width: Int,
    height: Int,
    u: Float,
    v: Float,
): ByteArray

fun rawRgbaLinearSample(
    bytes: ByteArray,
    width: Int,
    height: Int,
    u: Float,
    v: Float,
): ByteArray

fun rawRgbaSourceRectSample(
    bytes: ByteArray,
    width: Int,
    height: Int,
    srcL: Float,
    srcT: Float,
    srcR: Float,
    srcB: Float,
    u: Float,
    v: Float,
    sample: SampleKind,
): ByteArray

fun rawRgbaApplyTint(
    srcRgba: ByteArray,
    tintRgba: FloatArray,
    paintAlpha: Float,
): ByteArray

fun rawExactMatch(a: ByteArray, b: ByteArray): Boolean
fun maxChannelDelta(a: ByteArray, b: ByteArray): Int
fun matchesWithinOneLsb(a: ByteArray, b: ByteArray): Boolean
```

The physical color oracle is:

```kotlin
fun sampleSrgbStraightToEncodedPremul(
    straightEncodedSrgb: ByteArray,
    width: Int,
    height: Int,
    u: Float,
    v: Float,
    sample: SampleKind,
    tintPremultipliedRgba: FloatArray,
): ByteArray
```

It accepts exactly four finite tint components in `[0, 1]`, with every RGB
component less than or equal to alpha. Non-positive dimensions, byte lengths
other than `width * height * 4`, non-finite UVs, invalid source rectangles, or
invalid tint values fail immediately with `IllegalArgumentException`. Finite
UVs outside `[0, 1]` are deliberately clamped; the test oracle does not
silently repair other malformed fixtures.

## Sampling coordinates

Normalized UVs follow the WebGPU texel-center model.

For nearest sampling, clamp UV to `[0, 1]` and select:

```text
x = min(floor(u * width), width - 1)
y = min(floor(v * height), height - 1)
```

For linear sampling, use:

```text
fx = u * width - 0.5
fy = v * height - 0.5
```

Interpolate the four surrounding texels from `floor(fx)` and `floor(fy)`.
Clamp each integer texel index to the texture edge. This makes `(0.5 / width,
0.5 / height)` the center of the first texel and preserves clamp-to-edge at
zero and one.

`rawRgbaSourceRectSample` treats `srcL`, `srcT`, `srcR`, and `srcB` as
normalized full-image UV bounds. It clamps the supplied full-image `u` and `v`
to those bounds and samples at the clamped absolute UV. It never remaps the
source rectangle back to `[0, 1]`.

## Color calculation

For each color texel, convert encoded RGB channels to `[0, 1]` and apply the
IEC 61966-2-1 transfer:

```text
encoded <= 0.04045
    ? encoded / 12.92
    : ((encoded + 0.055) / 1.055) ^ 2.4
```

Alpha is linear UNORM: `alphaByte / 255`.

Nearest or bilinear sampling operates on straight linear RGB and linear alpha.
After sampling:

```text
source.rgb = sampledLinearRgb * sampledAlpha
source.a   = sampledAlpha
result     = source * tintPremultipliedRgba
```

Encode each result RGB channel with:

```text
linear <= 0.0031308
    ? linear * 12.92
    : 1.055 * linear ^ (1 / 2.4) - 0.055
```

Clamp all channels to `[0, 1]`, multiply by `255`, and round to the nearest
integer. Alpha is quantized directly as linear UNORM. Tint and paint alpha are
applied exactly once.

## Fixture ownership

Every public `ByteArray` fixture access returns a fresh copy backed by private
storage. A test may mutate the returned array without changing the bytes seen
by later tests. Dimensions, color type, and literal pixel content remain
unchanged.

The existing self-comparison hash test is replaced by an isolation test:
mutating one returned array must not affect the next returned array. Pixel
content tests remain the deterministic content authority.

## Required evidence

The focused test suite must prove:

1. raw nearest and linear helpers use WebGPU texel centers;
2. raw source-rectangle sampling clamps absolute full-image UVs without
   remapping;
3. existing geometry and clamp tests call only the explicit `rawRgba...`
   helpers;
4. black/white linear sampling at the midpoint stays within one LSB of
   `[188, 188, 188, 255]`;
5. straight source `[40, 120, 210, 160]` with a `0.75` premultiplied tint
   produces `[25, 84, 150, 120]`;
6. nearest sampling preserves an exact texel before the shader calculation;
7. sampled alpha, premultiplication, tint, and paint alpha are each applied
   once;
8. `maxChannelDelta` reports the true unsigned maximum channel difference;
9. `matchesWithinOneLsb` accepts delta `1` and refuses delta `2`;
10. mutating one fixture snapshot cannot affect a later snapshot;
11. all existing RGBA, BGRA, A8, nine, atlas, geometry, and clamp content tests
    remain green.

The branch must pass `GPUPreparedImageTestFixturesTest`, `git diff --check`,
and a read-only independent review before integration.

## Integration

The fixture delivery commit and this correction are integrated before the
DrawImage lowerer. The unrelated local `.worktrees/` ignore commit is not part
of the target integration. After the lowerer is integrated, private test
fixtures are replaced only where a common fixture expresses the same case;
parameterized transform or color cases retain narrowly scoped builders.

## Non-goals

- No destination CPU snapshot or compatibility upload.
- No production WebGPU or WGSL change.
- No HDR, wide-gamut, YUV, codec, mipmap, anisotropic, cubic, repeat, mirror,
  decal, or animated-image support.
- No claim that a raw RGBA helper is a GPU color oracle.
- No product-gate, router, or legacy-allowlist change.
