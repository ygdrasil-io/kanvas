# M90 Dashing Evidence Gate

Date: 2026-06-08

## Scope

`M90-PAA-3H-REF` is the row-specific evidence gate for `DashingGM` /
`skia-gm-dashing`.
The current M90 intake records only policy and historical signals, so support
evaluation remains blocked until a row-specific Skia reference, CPU/WebGPU
routes, render images, diff/stat artifacts, and performance artifacts exist.

## Current Status

- Row status: `expected-unsupported`
- Support claim: `False`
- Fallback: `coverage.dashing.row-specific-artifacts-required`
- Present row-specific evidence items: `0`
- Missing row-specific evidence items: `10`
- Historical signals: `7`, all non-promotional

Historical Dashing CPU tests, the Kotlin GM source port, the crossbackend test,
GPU score `DashingGM-gpu=96.80; DashingGM-raster=96.08`, CPU score
`96.06985294117646`, cpu-raster mirror `96.06985294117646`, and kanvas-skia
mirror `96.06985294117646` remain provenance only. The intake contains no
standalone WebGPU historical Dashing test; only the crossbackend test and
historical GPU similarity score are present. These signals do not replace the
`skia-gm-dashing` artifact bundle and do not update registry, dashboard,
threshold, edge-budget, or readiness state.

## Gate Artifact

The unresolved evidence requirement is tracked in:

```text
reports/wgsl-pipeline/scenes/generated/m90-dashing-row-specific-evidence-gate.json
```

The gate requires all ten row-specific artifacts under:

```text
reports/wgsl-pipeline/scenes/artifacts/skia-gm-dashing/
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
rtk python3 scripts/validate_m90_dashing_evidence_gate.py --check-worktree-scope
rtk python3 scripts/m90_path_aa_dashing_evidence_intake.py
rtk ./gradlew --no-daemon pipelineM90PathAaDashingEvidenceGate
rtk git diff --check
```
