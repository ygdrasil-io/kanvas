# KAN-052 Image Filter Visual Delta

KAN-052 relit `crop-image-filter-nonnull-prepass` after KAN-041/KAN-042 and closes this slice as `blocked=true`, not as a renderer visual fix.

## Decision

| Field | Value |
|---|---|
| rendererChanged | `False` |
| blocked | `True` |
| closureDecision | `blocked-root-cause` |
| rootCause | `rgba16float-intermediate-store-to-present-byte-quantization-policy` |
| reasonCode | `not-bounded-to-image-filter-crop-prepass` |

## Selected Row

| Field | Value |
|---|---|
| row | `crop-image-filter-nonnull-prepass` |
| referenceKind | `skia-upstream` |
| GPU route | `webgpu.image-filter.crop-nonnull-offset-prepass.final-crop-composite` |
| fallbackReason | `none` |

## Current Evidence

| Metric | Value |
|---|---:|
| GPU similarity | `98.44` |
| GPU matching pixels | `126000` |
| threshold | `50.0` |
| RGB-only residual pixels | `6622` |
| alpha delta non-zero pixels | `0` |
| color audit max channel delta | `1` |

Current artifacts remain under `reports/wgsl-pipeline/scenes/artifacts/crop-image-filter-nonnull-prepass/`.

## Before/After Status

- before: existing selected-row PNG/stat/route artifacts are reused as the investigation input.
- after: renderer artifacts are not materialized because `rendererChanged=false` and the slice is blocked by `rgba16float-intermediate-store-to-present-byte-quantization-policy`.

## Blocker Evidence

- `FOR-252`: residual reproduces on non image-filter route `webgpu.canvas.draw-rect.src-over`.
- `FOR-259`: remaining boundary is `rgba16float-intermediate-store-to-present-byte-quantization-policy`.
- `FOR-260`: candidate remains diagnostic because `missing_whole_scene_intermediate_rgba8_candidate_evidence_for_exact_and_precision_sensitive_routes`.

No threshold, picture-prepass, CPU readback, broad DAG, or implementation-gap to support conversion is claimed.
