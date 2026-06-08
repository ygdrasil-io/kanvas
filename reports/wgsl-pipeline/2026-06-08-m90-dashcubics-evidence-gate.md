# M90 DashCubics Evidence Gate

Date: 2026-06-08

## Scope

`M90-PAA-3I-REF` is the row-specific evidence gate for `DashCubicsGM` /
`skia-gm-dashcubics`.
The current M90 intake records only policy and historical signals, so support
evaluation remains blocked until a row-specific Skia reference, CPU/WebGPU
routes, render images, diff/stat artifacts, and performance artifacts exist.

## Current Status

- Row status: `expected-unsupported`
- Support claim: `False`
- Fallback: `coverage.dash-cubic.row-specific-artifacts-required`
- Present row-specific evidence items: `0`
- Missing row-specific evidence items: `10`
- Historical signals: `7`, all non-promotional

Historical DashCubics CPU test, the Kotlin GM source port, the crossbackend test,
GPU score `DashCubicsGM-gpu=92.69; DashCubicsGM-raster=91.07`, CPU score
`89.86897880539499`, cpu-raster mirror `89.86897880539499`, and kanvas-skia
mirror `89.86897880539499` remain provenance only. The intake contains no
standalone WebGPU historical DashCubics test; only the crossbackend test and
historical GPU similarity score are present. These signals do not replace the
`skia-gm-dashcubics` artifact bundle and do not update registry, dashboard,
threshold, edge-budget, or readiness state.

## Gate Artifact

The unresolved evidence requirement is tracked in:

```text
reports/wgsl-pipeline/scenes/generated/m90-dashcubics-row-specific-evidence-gate.json
```

The gate requires all ten row-specific artifacts under:

```text
reports/wgsl-pipeline/scenes/artifacts/skia-gm-dashcubics/
```

Future support evaluation also requires CPU and WebGPU route evidence with
`fallbackReason=none`.

## Non-Claims

- No row support claim.
- No broad Path AA support claim.
- No broad dash support claim.
- No broad hairline support claim.
- No broad stroke support claim.
- No Ganesh or Graphite port.
- No dynamic SkSL compiler, IR, or VM.
- No global threshold reduction.
- No dashboard promotion.
- No support promotion from historical scores or a below-threshold/tolerance-only case.

## Validation

```text
rtk python3 scripts/validate_m90_dashcubics_evidence_gate.py --check-worktree-scope
rtk python3 scripts/m90_path_aa_dashcubics_evidence_intake.py
rtk ./gradlew --no-daemon pipelineM90PathAaDashCubicsEvidenceGate
rtk git diff --check
```
