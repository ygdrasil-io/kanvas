# FP-04 prepared-image sRGB store evidence

Date: 2026-07-27

## Scope

This report records the bounded Task 5.4 color-contract change. Task 5
acceptance is recorded separately in `fp-04-task-5-review.md`; this report does
not implement Task 6, remove the image legacy allowlist, or claim the complete
image/image-nine/lattice/atlas product route.

## Closed SDR contract

Prepared color images now use one exact source/sample/store contract:

- normalized host upload bytes are straight encoded sRGB;
- the color source texture is `RGBA8UnormSrgb`;
- WebGPU sampling performs the sRGB-to-linear transfer;
- the prepared-image fragment shader multiplies sampled linear RGB by sampled
  alpha before applying paint alpha;
- the scene target is `RGBA8UnormSrgb` with `LinearPremul` interpretation;
- the render attachment performs the linear-to-sRGB store conversion;
- A8 coverage remains a separate `RGBA8Unorm` linear coverage texture.

The legacy prepared-scene pair remains closed and byte-compatible for
non-image callers:

- `RGBA8Unorm` + `EncodedPremulSrgb` maps to native `RGBA8Unorm`;
- `RGBA8UnormSrgb` + `LinearPremul` maps to native
  `RGBA8UnormSrgb`.

The two cross-pairs are refused before capability lookup and native target
allocation. No silent format remap is permitted.

The native capability snapshot declares `RGBA8UnormSrgb` as a supported
single-sample color attachment. It deliberately advertises only `{1}` render
attachment support and no resolve-source sample count. A central materializer
guard refuses an sRGB `MultisampleFrame(4)` plan with
`unsupported.native-core-primitive.srgb-msaa` before encoder validation, cache
or frame-pool mutation, and native acquisition; the existing unorm-only MSAA
pool is not remapped or promoted.

Readback accepts the closed storage set `{RGBA8Unorm, RGBA8UnormSrgb}` only
when the preflighted CPU output remains `Rgba8Unorm` +
`EncodedPremulSrgb`. The mapper copies the stored bytes unchanged; it neither
remaps the format nor applies an implicit CPU transfer conversion. The bounded
SDR contract therefore names `EncodedPremulSrgb`, not `LinearPremul`, as its
readback interpretation.

## Structural propagation

Scene target format is part of the CorePrimitive structural authority and
native descriptor identity. Direct, path-stencil, clip-stencil, and
coverage-mask consumer programs retain the declared scene format. The
coverage-mask producer and its intermediate attachment remain
`RGBA8Unorm`.

Legacy `rgba8unorm` target-state and stable pipeline hashes remain byte exact.
The sRGB variants have distinct stable identities and cannot reuse the legacy
pipeline entry accidentally.

## Native oracle

`GPUPreparedImageSrgbNativeProbeTest` submits public wgpu4k commands and
compares four candidate source/store contracts against an independent
IEC 61966-2-1 translucent oracle.

The selected production candidate is:

```text
StraightSrgbRepremul
```

The observed backend was `wgpu4k-native`, the adapter was `Apple M2 Max`, and
the production route marker was `image.draw.texture_upload`. The exact native
readback bytes were:

```text
CurrentEncodedPremul = [19,56,99,120, 30,90,157,191, 96,96,96,96]
DirectPremulSrgb     = [21,65,116,120, 34,105,185,191, 165,165,165,96]
StraightSrgbRepremul = [25,84,150,120, 34,105,185,191, 165,165,165,96]
LegacyManualTransfer = [25,84,150,120, 34,105,185,191, 165,165,165,96]
```

The selected candidate and the manual-transfer reference both had twelve zero
channel deltas. The rejected legacy candidates retained non-zero RGB deltas:

```text
CurrentEncodedPremul = [6,28,51,0, 4,15,28,0, 69,69,69,0]
DirectPremulSrgb     = [4,19,34,0, 0,0,0,0, 0,0,0,0]
StraightSrgbRepremul = [0,0,0,0, 0,0,0,0, 0,0,0,0]
LegacyManualTransfer = [0,0,0,0, 0,0,0,0, 0,0,0,0]
```

It uses a straight `RGBA8UnormSrgb` source, shader premultiplication, and an
`RGBA8UnormSrgb` target. The bounded oracle pixels are:

```text
translucent = [25, 84, 150, 120]
opaque      = [34, 105, 185, 191]
coverage    = [165, 165, 165, 96]
```

The selected candidate and the retained manual-transfer reference must remain
within one channel level of the oracle. The former encoded-premul/unorm
candidate and direct premultiplied-sRGB-source candidate must remain observably
different. The probe also recovers A8 coverage after paint alpha and checks it
within one channel level.

## TDD and focused validation

Observed serial runs with JDK 25 and Gradle 9.2:

- structural/preflight/materializer RED:
  176 tests, 173 passed, 3 failed;
- intermediate RED after the direct-format classifier change:
  176 tests, 175 passed, 1 failed;
- structural/preflight/materializer GREEN:
  176 tests, 176 passed, 0 failed, 0 skipped;
- runtime exact-pair RED:
  1 test, 0 passed, 1 failed;
- runtime exact-pair GREEN:
  1 test, 1 passed;
- prepared-surface diagnostic RED:
  1 test, 0 passed, 1 failed;
- complete runtime-native plus prepared-surface builder GREEN:
  84 tests, 84 passed, 0 failed, 0 skipped.
- native sRGB probe GREEN:
  2 tests, 2 passed, 0 failed, 0 skipped.
- native capability inventory RED then GREEN:
  1 test failed before `RGBA8UnormSrgb` was declared, then 1 passed;
- exact sRGB readback storage RED then GREEN:
  1 test failed against the unorm-only mapper, then 1 passed with unchanged
  byte delivery and closed-set refusal coverage.
- independent-review repair RED:
  3 tests, 0 passed, 3 failed while the SDR readback contract still named
  `LinearPremul`, native sRGB still advertised x4 resolve support, and the
  materializer had no pre-acquisition sRGB-MSAA guard;
- independent-review repair GREEN:
  3 tests, 3 passed, including the forged sRGB x4 frame plan with an
  intentionally invalid encoder plan and zero native/cache/pool side effects.

The final Task 5.4 manifests were run from the current working tree, not
reused from historical certification:

- GPU: 24 suites, 531 tests, 531 passed, 0 failed, 0 skipped;
- Kanvas: 9 suites, 81 tests, 81 passed, 0 failed, 0 skipped.

The exact suite lists and commands are recorded in
`fp-04-task-5-review.md`.

The prepared-surface diagnostic now records the actual contract:

```text
image.upload.format=RGBA8UnormSrgb
image.upload.encoding=StraightEncodedSrgb
image.target.format=rgba8unorm-srgb
image.shader.interpretation=LinearPremul
image.attachment.srgbConversion=true
```

## Explicit non-claims

- No destination CPU snapshot or upload was added.
- Animation refusal behavior is unchanged.
- This evidence is bounded to the current SDR contract; HDR, gainmaps, wide
  gamut, and arbitrary color profiles remain outside it.
- Native MSAA sRGB attachment pooling is not promoted by this report; sRGB x4
  is explicitly refused before allocation.
- Task 5 acceptance is recorded by the completed consolidation review; it does
  not broaden this bounded color-contract evidence into a product-routing
  claim.
