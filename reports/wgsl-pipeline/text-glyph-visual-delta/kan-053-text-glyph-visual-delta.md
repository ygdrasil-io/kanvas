# KAN-053 Text Glyph Visual Delta

KAN-053 relit `text.simple-latin.line.v1` after KAN-043/KAN-044 and closes this slice as `blocked=true`, not as a renderer visual fix.

## Decision

| Field | Value |
|---|---|
| rendererChanged | `False` |
| blocked | `True` |
| closureDecision | `blocked-root-cause` |
| rootCause | `text-atlas-alpha-mask-draw-route-not-materialized` |
| reasonCode | `requires-production-glyph-atlas-sampling-route` |

## Selected Row

| Field | Value |
|---|---|
| row | `text.simple-latin.line.v1` |
| source | `KAN-043` |
| font | `Liberation Sans` |
| font sha256 | `76d04c18ea243f426b7de1f3ad208e927008f961dc5945e5aad352d0dfde8ee8` |
| WebGPU route | `webgpu.text.outline-path.simple-latin` |
| atlas route | `webgpu.text.glyph-atlas.simple-latin` |
| referenceKind | `cpu-atlas-alpha-mask-oracle` |
| fallbackReason | `none` |

## Current Evidence

| Metric | Value |
|---|---:|
| CPU mismatching pixels vs atlas reference | `581` |
| WebGPU mismatching pixels vs atlas reference | `608` |
| WebGPU minus CPU reference mismatches | `27` |
| tolerance | `8` |
| similarity threshold | `95.0` |
| atlas upload bytes | `12928` |
| atlas glyph entries | `26` |

Current PNG/stat/route artifacts remain under `reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/`.

## Before/After Status

- before: existing KAN-012 reference/CPU/WebGPU/diff/stat/route artifacts are reused as the selected-row evidence.
- after: renderer artifacts are not materialized because `rendererChanged=false` and the slice is blocked by `text-atlas-alpha-mask-draw-route-not-materialized`.

## Blocker Evidence

- KAN-043 proves the text row and font identity, but the draw route is `webgpu.text.outline-path.simple-latin`.
- KAN-044 proves the text-owned `webgpu.text.glyph-atlas.simple-latin` upload plan and CPU mask oracle, not a production atlas sampling draw path.
- KAN-044 keeps standalone WebGPU alpha mask `expected-unsupported` via `coverage.alpha-mask-unsupported`.

No implicit system font fallback, broad shaping, LCD/SDF/color-font, dynamic atlas eviction, threshold weakening, or coverage-owned atlas claim is made.
