# FP-04 Image Refusal Matrix Audit

Prepared: 2026-07-27
Branch: `codex/fp04-image-refusal-matrix`
Base commit: `c310b1f9e7d02bd28046131657a5e0bd5043ffe6`

## Stable refusal codes inventory

All 29 canonical codes from `GPUPreparedImageRefusalCodes.ALL` are inventoried
in `GPUPreparedImageRefusalCases.kt`. The animation code is confirmed unchanged:

```
unsupported.image.animation
```

Present at `PreparedImageContracts.kt:51`, tested at
`PreparedImageContractsTest.kt:148-149` and `GPUPreparedImageSourceRefusalMatrixTest.kt`.

## Currently testable (source/contract boundary)

These 16 codes are exercised against `GPUPreparedImageArtifactFactory.prepare()`:

| Code | Cases tested |
|---|---|
| `unsupported.image.codec.unregistered` | encoded source class |
| `unsupported.image.animation` | animated source class |
| `unsupported.color.yuv_conversion` | YUV source class |
| `unsupported.color.hdr_transfer` | HDR source class |
| `unsupported.texture.import_unvalidated` | imported texture class |
| `unsupported.image.pixel.format` | Unsupported format |
| `unsupported.color.image_profile_conversion` | Unresolved profile |
| `unsupported.color.gamut_transform` | Other gamut |
| `unsupported.image.orientation` | Unresolved orientation |
| `unsupported.image.alpha_interpretation` | UNPREMUL, UNKNOWN, A8+OPAQUE, OPAQUE+non-255 alpha |
| `unsupported.image.dimensions` | zero/negative width/height |
| `unsupported.image.native_generation` | negative sourceGeneration |
| `unsupported.image.pixel.row_stride` | row stride < tight bytes |
| `unsupported.image.pixels_missing` | null pixel bytes |
| `unsupported.image.pixel.length` | byte length != stride * height |
| `unsupported.image.upload.budget_exceeded` | maxUploadBytes=0 |

## Atlas blend closed table

| Accepted (5) | Refused (24 → `unsupported.image.atlas.source_blend`) |
|---|---|
| `SRC` | CLEAR, DST_OVER, SRC_IN, DST_IN, SRC_OUT, DST_OUT, SRC_ATOP, DST_ATOP, XOR |
| `DST` | MULTIPLY, SCREEN, OVERLAY, DARKEN, LIGHTEN |
| `SRC_OVER` | COLOR_DODGE, COLOR_BURN, HARD_LIGHT, SOFT_LIGHT |
| `PLUS` | DIFFERENCE, EXCLUSION |
| `MODULATE` | HUE, SATURATION, COLOR, LUMINOSITY |

The closed table is tested in `GPUPreparedImageSourceRefusalMatrixTest` with
29/29 `BlendMode` entries covered and the exact accepted set verified.

## Integration checklist for Tasks 7-9

### Task 7 (DrawImageLowerer)

After `GPUPreparedDrawImageLowerer` is available, add tests for:

```
test: cubic sampling refuses unsupported.image.sampling_cubic
        → assert lowering.refusalCode == SAMPLING_CUBIC, zero commands returned

test: perspective transform refuses unsupported.image.perspective_sampling
        → assert lowering.refusalCode == PERSPECTIVE_SAMPLING, zero commands returned

test: singular transform refuses unsupported.image.perspective_sampling
        → assert lowering.refusalCode == PERSPECTIVE_SAMPLING, zero commands returned
```

### Task 8 (ImageGridLowerer)

After `GPUPreparedImageGridLowerer` is available, add tests for:

```
test: invalid nine center dimensions refuse unsupported.image.nine_geometry
        → assert lowering.refusalCode == NINE_GEOMETRY, zero commands returned

test: invalid lattice dimensions refuse unsupported.image.lattice_geometry
        → assert lowering.refusalCode == LATTICE_GEOMETRY, zero commands returned
```

### Task 9 (AtlasLowerer + final evidence)

After `GPUPreparedAtlasLowerer` is available, add tests for:

```
test: atlas array length mismatch refuses unsupported.image.atlas.array_lengths
        → assert lowering.refusalCode == ATLAS_ARRAY_LENGTHS, zero commands returned

test: atlas invalid sprite rect refuses unsupported.image.atlas.geometry
        → assert lowering.refusalCode == ATLAS_GEOMETRY, zero commands returned

test: atlas unsupported blend mode refuses unsupported.image.atlas.source_blend
        → parameterize all 24 non-accepted BlendMode values
        → assert lowering.refusalCode == ATLAS_SOURCE_BLEND, zero commands returned
```

## Sampler boundary (from ImageContracts SamplerBoundaryPlanner)

These codes are exercised at the sampler boundary planner. When the new
`GPUPreparedImageSampling` path is available through the lowerers, add these
integration verifications:

```
test: repeat tile mode refuses unsupported.image.tile_mode
test: mirror tile mode refuses unsupported.image.tile_mode
test: decal tile mode refuses unsupported.image.tile_mode
test: anisotropic sampling (>1) refuses unsupported.image.sampling_anisotropic
test: mip required refuses unsupported.image.mip_required
test: sampler LOD clamp refuses (invalid pointer)
```

## Future boundaries (preflight, materializer)

```
test: texture limit refuses unsupported.image.texture_limit
        → trigger: device texture budget exceeded at resource plan stage

test: native generation refuses unsupported.image.native_generation
        → trigger: generation seal mismatch at preflight stage

test: native binding incomplete refuses unsupported.image.native_binding
        → trigger: incomplete bind group layout at preflight stage

test: WGSL validation refuses unsupported.image.wgsl_validation
        → trigger: shader compilation failure at preflight stage
```

## Zero-allocation and no-fallback

Every refusal in this matrix is required to:
- Return zero fake-native handles (no texture, sampler, bind group, pipeline created)
- Not fall back to a legacy route or CPU compatibility path

These requirements are documented as `requiresZeroAlloc` and `requiresNoFallback`
on every `ImageRefusalCase` and `ImageSamplingRefusalCase`.

## Reserved test name

`GPUPreparedImageRefusalMatrixTest` is reserved for Task 9. It is the final
integrated test combining all 29 codes with parameterized lowerer/sampler/
atlas inputs. Do not create it before the lowerers exist.
