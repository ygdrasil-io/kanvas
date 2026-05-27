# M34 Image-Filter MVP Lane Closeout

Date: 2026-05-27
Linear: GRA-113 / parent GRA-102
Branch: gra-113-m34-closeout

## Final Status

M34 is closed with `SkImageFilters.Crop(input = nonNull)` retained as an accepted MVP limitation.

The WebGPU MVP does not promote any `SimpleOffsetImageFilter*` image-filter fixture to required smoke. The two affected rows remain classified inventory-only with the stable reason:

```text
image-filter.crop-input-nonnull-prepass-required
```

This is a deliberate release boundary, not an untriaged failure.

## M34 PR Evidence

| Ticket | PR | Merge commit | Result |
|---|---|---|---|
| GRA-109 | https://github.com/ygdrasil-io/kanvas/pull/1181 | `4bc309a10940a019fce8ef2c085db0ee6374a4a7` | Reproduced current image-filter inventory. |
| GRA-110 | https://github.com/ygdrasil-io/kanvas/pull/1182 | `b2bdb570063afd3ad775bb7b237a5289434143d4` | Chose accepted MVP limitation instead of pre-pass implementation. |
| GRA-111 | https://github.com/ygdrasil-io/kanvas/pull/1183 | `e6d8c7d17ae3912149fc245b28900b4f56311707` | Added required-smoke guard for unsupported image-filter fixtures. |
| GRA-112 | https://github.com/ygdrasil-io/kanvas/pull/1184 | `756c30a9dd27cdff367238d48a39e992eff9bcfd` | Synced README, image-filter spec, and release-readiness wording. |

## Inventory Evidence

GRA-109 reproduced full GPU inventory after M33:

| Category | Count |
|---|---:|
| `expected-unsupported-diagnostic` | 50 |
| `similarity-regression` | 0 |
| `unsupported-image-filter` | 2 |
| `adapter-skip` | 48 |
| `adapter-missing` | 0 |
| `unexpected-exception` | 4 |

The image-filter subset contains exactly two rows:

| Test | Classification | Reason |
|---|---|---|
| `org.skia.gpu.webgpu.SimpleOffsetImageFilterWebGpuTest#SimpleOffsetImageFilterGM renders close to reference PNG on the GPU backend()` | `unsupported-image-filter` | `image-filter.crop-input-nonnull-prepass-required` |
| `org.skia.gpu.webgpu.crossbackend.SimpleOffsetImageFilterCrossBackendTest#SimpleOffsetImageFilterGM matches reference on raster and GPU backends()` | `unsupported-image-filter` | `image-filter.crop-input-nonnull-prepass-required` |

No other image-filter row was classified as unsupported, unexpected, or similarity-regressed.

The four `unexpected-exception` rows in the GRA-109 inventory are unrelated `SaveLayerTest` blend rows. They are M35 release-readiness blockers unless resolved before the final inventory audit, but they do not change the M34 image-filter boundary.

## Required Smoke Status

GRA-111 added fail-closed smoke policy for the two unsupported image-filter fixtures. Required smoke remains adapter-backed and does not include the expected-unsupported image-filter rows.

GRA-111 required CI:

- `Raster tests (ubuntu)`: https://github.com/ygdrasil-io/kanvas/actions/runs/26539884557/job/78178432188
- `GPU tests (macos)`: https://github.com/ygdrasil-io/kanvas/actions/runs/26539884557/job/78178432112

Local validation for the hardening path passed:

```bash
rtk ./gradlew --no-daemon :gpu-raster:validateGpuSmokePromotionPolicy
rtk ./gradlew --no-daemon :gpu-raster:gpuSmokeTest
```

## Spec and README State

- `README.md` now reports MVP readiness at approximately 95% and M34 Done/100%.
- `.upstream/specs/wgsl-pipeline/09-image-filter-mvp-lane.md` is Accepted.
- `.upstream/specs/README.md` lists the M34 spec as Accepted.
- `.upstream/specs/release-readiness-mvp.md` names `Crop(input = nonNull)` as an accepted MVP limitation and requires smoke exclusion while the stable reason remains inventory-only.

## Remaining Work

The post-MVP implementation path is a real render-to-texture child pre-pass. It must materialise the child filter output into scratch texture storage, then apply Crop tile/crop semantics against that scratch with adapter-backed WebGPU and cross-backend evidence before the `SimpleOffsetImageFilter*` fixtures can become smoke candidates.

M35 does not need to re-triage the image-filter lane. It must only confirm that the final inventory still contains exactly the accepted `unsupported-image-filter` rows and that no required smoke fixture emits `image-filter.crop-input-nonnull-prepass-required`.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-raster:validateGpuSmokePromotionPolicy
rtk ./gradlew --no-daemon pipelineConformanceReport
```
